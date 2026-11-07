// Trusted maintenance utility. Dry-run by default. Never logs keys, patient values, or plaintext.
const {webcrypto} = require('node:crypto');
const cryptoKeys = new Map();
async function decryptEnvelope(envelope, keys) {
  const raw = keys[envelope.keyVersion || 1];
  if (!raw || Buffer.from(raw, 'base64').length !== 32) throw Error('Missing or invalid organization key version');
  let key = cryptoKeys.get(raw);
  if (!key) {
    key = await webcrypto.subtle.importKey('raw', Buffer.from(raw, 'base64'), 'AES-GCM', false, ['decrypt']);
    cryptoKeys.set(raw, key);
  }
  const bytes = await webcrypto.subtle.decrypt({name: 'AES-GCM', iv: Buffer.from(envelope.iv, 'base64')}, key,
    Buffer.from(envelope.ciphertext, 'base64'));
  return JSON.parse(Buffer.from(bytes).toString('utf8'));
}
function eventKeys(event, payload, previous) {
  if (event.cellKeys) return event.cellKeys;
  if (event.recordType === 'confirmation') return ['initials',
    ...(!previous?.fullDilationConfirmed && payload.fullDilationConfirmed ? ['fullDilationConfirmed'] : []),
    ...(!previous?.birthConfirmed && payload.birthConfirmed ? ['birthConfirmed'] : [])];
  if (event.cellKey === 'partographMark') return Object.keys(event.schemaVersion === 2 ? payload.values : payload.partographMarkCells || {});
  return [event.cellKey];
}
async function planMigration({women, columns, events, corrections = []}, keys) {
  const bySession = new Map();
  for (const woman of women) {
    const payload = await decryptEnvelope(woman.data, keys);
    if (!payload.admissionId || bySession.has(payload.admissionId)) throw Error('Missing or duplicate admission identity');
    bySession.set(payload.admissionId, woman.id);
  }
  const grouped = new Map();
  for (const event of events) {
    if (!event.data.columnId) throw Error('Unbound legacy observation requires a reviewed import before migration');
    const group = grouped.get(event.data.columnId) || [];
    group.push(event); grouped.set(event.data.columnId, group);
  }
  const updates = [], projections = [];
  const eventWomen = new Map();
  for (const column of columns) {
    const history = (grouped.get(column.id) || []).sort((a, b) => a.data.revision - b.data.revision);
    if (!history.length || history.length !== column.data.revision) throw Error('Missing column history');
    const latest = new Map(), levels = {};
    let sessionId, timestampMillis, womanDocumentId, chartColumnIndex, chartStartMillis, previous;
    for (let i = 0; i < history.length; i++) {
      const row = history[i], event = row.data;
      if (event.revision !== i + 1 || !['cell', 'cellCorrection', 'confirmation'].includes(event.recordType)) throw Error('Invalid event sequence');
      const payload = await decryptEnvelope(event, keys);
      const time = Date.parse(payload.timestamp);
      if (!Number.isFinite(time)) throw Error('Invalid column timestamp');
      if (i === 0) {
        sessionId = payload.sessionId; timestampMillis = time; womanDocumentId = bySession.get(sessionId);
        chartColumnIndex = payload.chartColumnIndex ?? -1;
        chartStartMillis = Number.isFinite(Date.parse(payload.chartStartTimestamp)) ? Date.parse(payload.chartStartTimestamp) : 0;
        if (!womanDocumentId || (column.data.womanDocumentId && column.data.womanDocumentId !== womanDocumentId)) throw Error('Admission binding mismatch');
      }
      if (payload.sessionId !== sessionId || time !== timestampMillis || (payload.chartColumnIndex ?? -1) !== chartColumnIndex ||
          (Number.isFinite(Date.parse(payload.chartStartTimestamp)) ? Date.parse(payload.chartStartTimestamp) : 0) !== chartStartMillis) throw Error('Conflicting column identity');
      if (payload.womanDocumentId && payload.womanDocumentId !== womanDocumentId) throw Error('Payload admission binding mismatch');
      const cellKeys = eventKeys(event, payload, previous);
      if (!cellKeys.length || cellKeys.length > 4 || new Set(cellKeys).size !== cellKeys.length ||
          cellKeys.some(key => !/^[A-Za-z][A-Za-z0-9]{0,39}$/.test(key) || key === 'partographMark')) throw Error('Invalid cell declarations');
      if (event.cellKey !== 'initials' && event.cellKey !== 'partographMark' && (cellKeys.length !== 1 || cellKeys[0] !== event.cellKey)) throw Error('Cell declaration mismatch');
      const migrated = {...event, cellKeys, womanDocumentId};
      updates.push({path: `lcgObservations/${row.id}`, source: row, data: {cellKeys, womanDocumentId}});
      eventWomen.set(row.id, womanDocumentId);
      for (const key of cellKeys) {
        latest.set(key, {sourceEventId: row.id, columnId: column.id, womanDocumentId, cellKey: key, event: migrated});
        if (key !== 'initials') levels[key] = event.accessLevel;
      }
      previous = payload;
    }
    updates.push({path: `lcgColumns/${column.id}`, source: column, data: {sessionId, timestampMillis, womanDocumentId, chartColumnIndex, chartStartMillis, cellAccessLevels: levels}});
    for (const [key, projection] of latest) projections.push({path: `lcgCells/${column.id}~${key}`, data: projection});
    grouped.delete(column.id);
  }
  if (grouped.size) throw Error('Events reference a missing column');
  for (const row of corrections) {
    const womanDocumentId = eventWomen.get(row.data.correctedObservationId);
    if (!womanDocumentId) throw Error('Unbound legacy correction');
    updates.push({path: `lcgCorrections/${row.id}`, source: row, data: {womanDocumentId}});
  }
  return {updates, projections};
}
async function main() {
  const [orgId, ...flags] = process.argv.slice(2);
  if (!/^[A-Za-z0-9]{8}$/.test(orgId || '') || flags.some(flag => flag !== '--apply')) throw Error('Usage: node scripts/maintenance/migrate-clinical-events.cjs ORG_ID [--apply]');
  // Use an environment variable in a trusted shell; never pass secret keys as CLI arguments.
  const keys = process.env.JANANISYNC_ORG_KEYS_JSON ? JSON.parse(process.env.JANANISYNC_ORG_KEYS_JSON)
    : {1: process.env.JANANISYNC_ORG_AES_KEY_BASE64};
  const {initializeApp, applicationDefault} = require('firebase-admin/app');
  const {getFirestore} = require('firebase-admin/firestore');
  initializeApp({credential: applicationDefault()});
  const db = getFirestore(), org = db.doc(`organizations/${orgId}`), clinical = org.collection('clinicalData').doc('v1');
  const read = async ref => (await ref.get()).docs.map(doc => ({id: doc.id, data: doc.data(), ref: doc.ref, updateTime: doc.updateTime}));
  const [women, columns, events, corrections] = await Promise.all([
    read(org.collection('women')), read(clinical.collection('lcgColumns')),
    read(clinical.collection('lcgObservations')), read(clinical.collection('lcgCorrections'))]);
  const plan = await planMigration({women, columns, events, corrections}, keys);
  console.log(`Validated ${columns.length} columns, ${events.length} events and ${plan.projections.length} current cells. ${flags.includes('--apply') ? 'Applying under maintenance.' : 'Dry-run only.'}`);
  if (!flags.includes('--apply')) return;
  // Writers must be stopped throughout. Update-time preconditions prevent silently overwriting concurrent edits.
  // Batches are resumable: a fresh dry-run produces the same projection content after interruption.
  const operations = [...plan.updates.map(item => ({...item, update: true})), ...plan.projections];
  for (let i = 0; i < operations.length; i += 350) {
    const batch = db.batch();
    for (const item of operations.slice(i, i + 350)) {
      if (item.update) batch.update(item.source.ref, item.data, {lastUpdateTime: item.source.updateTime});
      else batch.set(clinical.collection(item.path.split('/')[0]).doc(item.path.split('/')[1]), item.data);
    }
    await batch.commit();
  }
  console.log('Migration complete. Run dry-run again and verify projections before releasing maintenance.');
}
module.exports = {planMigration, eventKeys};
if (require.main === module) main().catch(() => { console.error('Migration stopped. Verify keys, complete history, bindings and maintenance status. No plaintext has been logged.'); process.exitCode = 1; });
