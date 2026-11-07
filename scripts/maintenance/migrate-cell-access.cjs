// Trusted administrator utility. Dry-run by default; never logs keys or values.
const {webcrypto} = require('node:crypto');

async function reconstruct(events, keyBase64) {
  const result = Object.create(null);
  const revisions = new Set();
  for (const event of [...events].sort((a, b) => a.revision - b.revision)) {
    if (!Number.isInteger(event.revision) || event.revision < 1 || revisions.has(event.revision)) {
      throw Error('Invalid or duplicate event revision');
    }
    revisions.add(event.revision);
    if (event.recordType === 'confirmation') continue;
    if (!['cell', 'cellCorrection'].includes(event.recordType) || !Number.isInteger(event.accessLevel) || event.accessLevel < 10 || event.accessLevel > 99) {
      throw Error('Invalid cell event metadata');
    }
    let keys;
    if (event.cellKey === 'partographMark') {
      // Legacy graph keys are encrypted. Always decrypt, even if newer metadata exists.
      if (!keyBase64) throw Error('Graph history requires JANANISYNC_ORG_AES_KEY_BASE64');
      const bytes = Buffer.from(keyBase64, 'base64');
      if (bytes.length !== 32) throw Error('Organization AES key must contain 32 bytes');
      const key = await webcrypto.subtle.importKey('raw', bytes, 'AES-GCM', false, ['decrypt']);
      const plaintext = await webcrypto.subtle.decrypt({name: 'AES-GCM', iv: Buffer.from(event.iv, 'base64')},
        key, Buffer.from(event.ciphertext, 'base64'));
      const payload = JSON.parse(Buffer.from(plaintext).toString('utf8'));
      if (!payload.partographMarkCells || typeof payload.partographMarkCells !== 'object' || Array.isArray(payload.partographMarkCells)) {
        throw Error('Invalid graph payload');
      }
      keys = Object.keys(payload.partographMarkCells);
    } else keys = [event.cellKey];
    if (!keys.length || keys.length > 4 || keys.some(key => !/^[A-Za-z][A-Za-z0-9]{0,39}$/.test(key)
        || ['initials', 'partographMark'].includes(key))) throw Error('Invalid touched cell keys');
    for (const key of keys) result[key] = event.accessLevel;
  }
  if (Object.keys(result).length > 256) throw Error('Too many cell keys');
  return result;
}

async function main() {
  const [orgId, ...flags] = process.argv.slice(2);
  if (!/^[A-Za-z0-9]{8}$/.test(orgId || '') || flags.some(flag => flag !== '--apply')) {
    throw Error('Usage: node scripts/maintenance/migrate-cell-access.cjs ORG_ID [--apply]');
  }
  const {initializeApp, applicationDefault} = require('firebase-admin/app');
  const {getFirestore} = require('firebase-admin/firestore');
  initializeApp({credential: applicationDefault()});
  const db = getFirestore();
  const root = db.doc(`organizations/${orgId}/clinicalData/v1`);
  const [columns, observations] = await Promise.all([root.collection('lcgColumns').get(), root.collection('lcgObservations').get()]);
  // Preflight the entire organization before any mutation, so missing graph keys
  // or corrupt history cannot cause a partially decrypted migration.
  const plans = [];
  for (const column of columns.docs) {
    const state = column.data();
    const history = observations.docs.map(doc => doc.data()).filter(event => event.columnId === column.id);
    if (!history.length || Math.max(...history.map(event => event.revision)) !== state.revision) {
      throw Error(`Incomplete history for column ${column.id}`);
    }
    const levels = await reconstruct(history, process.env.JANANISYNC_ORG_AES_KEY_BASE64);
    if (history.length !== state.revision) throw Error(`Gaps in history for column ${column.id}`);
    plans.push({column, state, levels});
  }
  if (plans.length > 450) throw Error("More than 450 columns: use a reviewed maintenance migration for this organization");
  console.log(`Validated ${plans.length} columns. Mode: ${flags.includes('--apply') ? 'apply' : 'dry-run'}.`);
  if (!flags.includes('--apply')) return;
  // One transaction checks every snapshot before writing anything. Firestore
  // transaction limits apply; split large organizations only under maintenance.
  await db.runTransaction(async transaction => {
    const current = plans.length ? await transaction.getAll(...plans.map(plan => plan.column.ref)) : [];
    for (let i = 0; i < plans.length; i++) {
      if (!current[i].exists || !current[i].updateTime.isEqual(plans[i].column.updateTime)) {
        throw Error('Columns changed during migration; stop writers and retry');
      }
    }
    for (const plan of plans) transaction.update(plan.column.ref, {cellAccessLevels: plan.levels});
  });
  console.log(`Updated ${plans.length} columns.`);
}

module.exports = {reconstruct};
if (require.main === module) main().catch(error => { console.error(error.message); process.exitCode = 1; });
