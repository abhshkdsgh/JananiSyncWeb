const {test} = require('node:test');
const assert = require('node:assert/strict');
const {webcrypto} = require('node:crypto');
const {reconstruct} = require('../../scripts/maintenance/migrate-cell-access.cjs');

test('migration reconstructs latest levels per cell, independent of column last editor', async () => {
  const levels = await reconstruct([
    {recordType: 'cell', revision: 2, cellKey: 'temperature', accessLevel: 10},
    {recordType: 'cell', revision: 1, cellKey: 'pulse', accessLevel: 20},
    {recordType: 'confirmation', revision: 3}
  ]);
  assert.deepEqual({...levels}, {pulse: 20, temperature: 10});
});

test('migration decrypts legacy graph keys and refuses missing or incorrect keys', async () => {
  const bytes = webcrypto.getRandomValues(new Uint8Array(32));
  const iv = webcrypto.getRandomValues(new Uint8Array(12));
  const key = await webcrypto.subtle.importKey('raw', bytes, 'AES-GCM', false, ['encrypt']);
  const ciphertext = await webcrypto.subtle.encrypt({name: 'AES-GCM', iv}, key,
    Buffer.from(JSON.stringify({partographMarkCells: {systolic: '120', diastolic: '80'}})));
  const event = {recordType: 'cell', revision: 1, cellKey: 'partographMark', accessLevel: 20,
    ciphertext: Buffer.from(ciphertext).toString('base64'), iv: Buffer.from(iv).toString('base64')};
  await assert.rejects(reconstruct([event]), /requires/);
  await assert.rejects(reconstruct([event], Buffer.alloc(32).toString('base64')));
  assert.deepEqual({...await reconstruct([event], Buffer.from(bytes).toString('base64'))}, {systolic: 20, diastolic: 20});
});

test('migration rejects duplicate revisions and invalid field metadata', async () => {
  const event = {recordType: 'cell', revision: 1, cellKey: 'pulse', accessLevel: 20};
  await assert.rejects(reconstruct([event, event]), /duplicate/);
  await assert.rejects(reconstruct([{...event, cellKey: 'initials'}]), /Invalid/);
  await assert.rejects(reconstruct([{...event, accessLevel: 100}]), /Invalid/);
});

test('migration includes cell corrections in the latest per-cell access map', async () => {
  assert.deepEqual({...await reconstruct([
    {recordType: 'cell', revision: 1, cellKey: 'pulse', accessLevel: 20},
    {recordType: 'confirmation', revision: 2},
    {recordType: 'cellCorrection', revision: 3, cellKey: 'pulse', accessLevel: 25}
  ])}, {pulse: 25});
});
