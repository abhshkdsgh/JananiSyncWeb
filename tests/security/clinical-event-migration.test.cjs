const {test} = require('node:test');
const assert = require('node:assert/strict');
const {webcrypto} = require('node:crypto');
const {planMigration} = require('../../scripts/maintenance/migrate-clinical-events.cjs');
async function encrypted(payload, raw) {
  const key = await webcrypto.subtle.importKey('raw', raw, 'AES-GCM', false, ['encrypt']);
  const iv = webcrypto.getRandomValues(new Uint8Array(12));
  const cipher = await webcrypto.subtle.encrypt({name: 'AES-GCM', iv}, key, Buffer.from(JSON.stringify(payload)));
  return {ciphertext: Buffer.from(cipher).toString('base64'), iv: Buffer.from(iv).toString('base64'), keyVersion: 1};
}
async function fixture() {
  const raw = webcrypto.getRandomValues(new Uint8Array(32));
  const keys = {1: Buffer.from(raw).toString('base64')};
  const base = {sessionId: 'admission', timestamp: '2026-10-24T08:00:00Z'};
  const events = [
    {id: 'senior', data: {...await encrypted({...base, partographMarkCells: {systolic: '120', diastolic: '80'}}, raw), columnId: 'column', recordType: 'cell', cellKey: 'partographMark', revision: 1, accessLevel: 80}},
    {id: 'junior', data: {...await encrypted(base, raw), columnId: 'column', recordType: 'cell', cellKey: 'pulse', cellKeys: ['pulse'], revision: 2, accessLevel: 10}}
  ];
  return {keys, data: {women: [{id: 'woman', data: await encrypted({admissionId: 'admission'}, raw)}],
    columns: [{id: 'column', data: {revision: 2, womanDocumentId: 'woman'}}], events}};
}
test('migration creates current cell projections and preserves per-cell access and admission identity', async () => {
  const {keys, data} = await fixture();
  const plan = await planMigration(data, keys);
  assert.equal(plan.projections.length, 3);
  const column = plan.updates.find(item => item.path === 'lcgColumns/column');
  assert.deepEqual(column.data.cellAccessLevels, {systolic: 80, diastolic: 80, pulse: 10});
  assert.equal(column.data.sessionId, 'admission');
  assert.equal(column.data.timestampMillis, Date.parse('2026-10-24T08:00:00Z'));
  assert.equal(plan.projections.find(item => item.data.cellKey === 'systolic').data.sourceEventId, 'senior');
  // A resumed preflight produces the same plan; the utility never logs clinical plaintext.
  assert.deepEqual(await planMigration(data, keys), plan);
});
test('migration refuses missing history, duplicate revisions, wrong keys, and cross-admission bindings', async () => {
  const {keys, data} = await fixture();
  await assert.rejects(planMigration({...data, events: data.events.slice(1)}, keys));
  await assert.rejects(planMigration({...data, events: [data.events[0], {...data.events[1], data: {...data.events[1].data, revision: 1}}]}, keys));
  await assert.rejects(planMigration(data, {1: Buffer.alloc(32).toString('base64')}));
  await assert.rejects(planMigration({...data, columns: [{id: 'column', data: {revision: 2, womanDocumentId: 'other'}}]}, keys));
});
