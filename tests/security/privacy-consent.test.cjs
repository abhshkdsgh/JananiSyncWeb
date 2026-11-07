// Local emulator only. Tests authenticated decision evidence, not legal identity or reading.
const {before, test} = require("node:test");
const assert = require("node:assert/strict");
const {readFileSync} = require("node:fs");
const {createHash, randomBytes} = require("node:crypto");
const {join} = require("node:path");
const host = process.env.FIRESTORE_EMULATOR_HOST;
assert.match(host || "", /^(127\.0\.0\.1|localhost):\d+$/, "A local Firestore emulator is required");
const project = "demo-jananisync";
const prefix = `projects/${project}/databases/(default)/documents`;
const base = `http://${host}/v1/${prefix}`;
const policyVersion = readFileSync(join(__dirname, "../../docs/privacy-policy.md"), "utf8").match(/^\*\*Version:\*\* ([0-9]+\.[0-9]+)$/m)[1];
const policySha256 = createHash("sha256").update(readFileSync(join(__dirname, "../../docs/privacy-policy.md"))).digest("hex");
const eventId = () => randomBytes(10).toString("hex");

function token(uid, verified = true) {
  const encode = value => Buffer.from(JSON.stringify(value)).toString("base64url");
  const now = Math.floor(Date.now() / 1000);
  return `${encode({alg: "none", typ: "JWT"})}.${encode({
    iss: `https://securetoken.google.com/${project}`, aud: project, sub: uid, user_id: uid,
    email: `${uid}@example.test`, email_verified: verified, iat: now, exp: now + 3600, auth_time: now,
    firebase: {sign_in_provider: "password", identities: {}}
  })}.`;
}

function field(value) {
  if (value instanceof Date) return {timestampValue: value.toISOString()};
  if (value === null) return {nullValue: null};
  if (typeof value === "number") return Number.isInteger(value) ? {integerValue: String(value)} : {doubleValue: value};
  if (typeof value === "boolean") return {booleanValue: value};
  if (Array.isArray(value)) return {arrayValue: {values: value.map(field)}};
  if (value && typeof value === "object") return {mapValue: {fields: fields(value)}};
  return {stringValue: value};
}
const fields = data => Object.fromEntries(Object.entries(data).map(([key, value]) => [key, field(value)]));
const headers = auth => ({"Content-Type": "application/json", ...(auth ? {Authorization: `Bearer ${auth}`} : {})});

async function request(path, auth, method = "GET", data) {
  return fetch(`${base}/${path}`, {method, headers: headers(auth), body: data ? JSON.stringify({fields: fields(data)}) : undefined});
}

async function check(responsePromise, status) {
  const response = await responsePromise;
  const body = await response.text();
  assert.equal(response.status, status, body);
  return body ? JSON.parse(body) : undefined;
}

function write(path, data, timestamps = [], updateMask) {
  return {update: {name: `${prefix}/${path}`, fields: fields(data)},
    ...(updateMask ? {updateMask: {fieldPaths: updateMask}} : {}),
    ...(timestamps.length ? {updateTransforms: timestamps.map(fieldPath => ({fieldPath, setToServerValue: "REQUEST_TIME"}))} : {})};
}

async function commit(auth, writes) {
  return fetch(`${base}:commit`, {method: "POST", headers: headers(auth), body: JSON.stringify({writes})});
}

function decisionWrites(uid, decision = "accepted", options = {}) {
  const id = options.id || eventId();
  const verified = options.verified !== false;
  const event = {schemaVersion: 1, userId: uid, email: `${uid}@example.test`, emailVerified: verified,
    policyVersion, policySha256, decision, source: "account_gate", presentationVersion: 2,
    clientOccurredAtMillis: Date.now(), language: "en-IN", userAgent: "Local emulator test", ...options.event};
  const receipt = {userId: uid, lastEventId: id, decision, policyVersion, policySha256, ...options.receipt};
  for (const key of options.omitEventFields || []) delete event[key];
  for (const key of options.omitReceiptFields || []) delete receipt[key];
  const writes = [];
  if (options.eventOnly !== false) writes.push(write(`users/${uid}/privacyEvents/${id}`, event,
    options.eventTimestamps === false ? [] : ["createdAt"]));
  if (options.receiptOnly !== false) writes.push(write(`users/${uid}/privacy/current`, receipt,
    options.receiptTimestamps === false ? [] : ["updatedAt"]));
  return {id, event, receipt, writes};
}

async function decide(uid, decision = "accepted", options = {}) {
  const record = decisionWrites(uid, decision, options);
  const response = await commit(options.auth === undefined ? token(uid, options.verified !== false) : options.auth, record.writes);
  return {...record, response};
}

async function history(uid, auth = token(uid), limit = 100) {
  const structuredQuery = {from: [{collectionId: "privacyEvents"}],
    orderBy: [{field: {fieldPath: "createdAt"}, direction: "DESCENDING"}],
    ...(limit == null ? {} : {limit})};
  return fetch(`${base}/users/${uid}:runQuery`, {method: "POST", headers: headers(auth), body: JSON.stringify({structuredQuery})});
}

before(async () => {
  await check(request("organizations/PrivOrg1", "owner", "PATCH", {name: "Privacy test organization"}), 200);
  await check(request("organizations/PrivOrg1/women/patient", "owner", "PATCH", {ciphertext: "synthetic"}), 200);
  for (const uid of ["privacy-reader", "privacy-unverified-reader", "privacy-stale"]) {
    await check(request(`organizations/PrivOrg1/members/${uid}`, "owner", "PATCH", {
      userId: uid, name: "Synthetic clinician", role: "doctor", status: "active", accessLevel: 10, joinedAt: new Date()
    }), 200);
  }
});

test("acceptance and denial are atomic, account-linked, server-timestamped, and remain in owner history", async () => {
  const uid = "privacy-history";
  const accepted = await decide(uid);
  const acceptedCommit = await check(accepted.response, 200);
  const event = await check(request(`users/${uid}/privacyEvents/${accepted.id}`, token(uid)), 200);
  const receipt = await check(request(`users/${uid}/privacy/current`, token(uid)), 200);
  assert.equal(event.fields.createdAt.timestampValue, receipt.fields.updatedAt.timestampValue);
  assert.equal(event.fields.createdAt.timestampValue, acceptedCommit.writeResults[0].transformResults[0].timestampValue);
  assert.equal(event.fields.userId.stringValue, uid);
  assert.equal(event.fields.email.stringValue, `${uid}@example.test`);
  assert.equal(event.fields.emailVerified.booleanValue, true);
  assert.equal(event.fields.policySha256.stringValue, policySha256);
  const denied = await decide(uid, "denied", {event: {source: "settings"}});
  await check(denied.response, 200);
  const current = await check(request(`users/${uid}/privacy/current`, token(uid)), 200);
  assert.equal(current.fields.lastEventId.stringValue, denied.id);
  assert.equal(current.fields.decision.stringValue, "denied");
  const rows = await check(history(uid), 200);
  assert.equal(rows.filter(row => row.document).length, 2);
  assert.deepEqual(rows.filter(row => row.document).map(row => row.document.fields.decision.stringValue), ["denied", "accepted"]);
});

test("a former member can renew an old request and rejoin only after fresh admin approval", async () => {
  const orgId = "Rejoin01";
  const uid = "returning-clinician";
  const admin = "rejoin-admin";
  const root = `organizations/${orgId}`;
  await check(request(root, "owner", "PATCH", {name: "Rejoin test organisation"}), 200);
  await check(request(`${root}/members/${admin}`, "owner", "PATCH", {
    userId: admin, name: "Test admin", role: "admin", status: "active", accessLevel: 99, joinedAt: new Date()
  }), 200);
  await check(request(`${root}/members/${uid}`, "owner", "PATCH", {
    userId: uid, name: "Returning nurse", role: "nurse", status: "left", accessLevel: 10,
    joinedAt: new Date(0), leftAt: new Date(), leftDuringRotationId: "R".repeat(22)
  }), 200);
  await check(request(`${root}/keyProfiles/${uid}`, "owner", "PATCH", {
    userId: uid, publicKeyJwk: "synthetic", publicKeyFingerprint: "fingerprint", keyAlgorithm: "RSA-OAEP-3072-SHA256", createdAt: new Date()
  }), 200);
  await check(request(`${root}/keyBackups/${uid}`, "owner", "PATCH", {
    userId: uid, ciphertext: "synthetic", iv: "synthetic", salt: "synthetic", kdf: "PBKDF2-SHA256",
    kdfIterations: 600000, keyAlgorithm: "RSA-OAEP-3072-SHA256", updatedAt: new Date()
  }), 200);
  await check(request(`${root}/accessRequests/${uid}`, "owner", "PATCH", {
    userId: uid, userName: "Returning nurse", userEmail: `${uid}@example.test`, requestedRole: "nurse",
    status: "approved", createdAt: new Date(), reviewedBy: admin, reviewedAt: new Date(), approvedRole: "nurse"
  }), 200);

  const renewed = {userId: uid, userName: "Returning nurse", userEmail: `${uid}@example.test`,
    requestedRole: "nurse", status: "pending", createdAt: new Date()};
  await check(commit(token(uid), [write(`${root}/accessRequests/${uid}`, {status: "pending"}, ["createdAt"], ["status", "createdAt"])]), 200);

  const approved = {...renewed, status: "approved", reviewedBy: admin, approvedRole: "nurse"};
  const activeMember = {userId: uid, name: "Returning nurse", role: "nurse", status: "active", accessLevel: 10, joinedAt: new Date()};
  await check(commit(token(admin), [
    write(`${root}/accessRequests/${uid}`, approved, ["reviewedAt"]),
    write(`${root}/members/${uid}`, activeMember, ["joinedAt"]),
  ]), 200);
  const member = await check(request(`${root}/members/${uid}`, token(uid)), 200);
  assert.equal(member.fields.status.stringValue, "active");
  assert.equal(member.fields.leftAt, undefined);
});

test("unverified provisional accounts can decide and read their own records without organization access", async () => {
  const uid = "privacy-unverified-reader";
  await check((await decide(uid, "denied", {verified: false, event: {source: "signup"}})).response, 200);
  await check(request(`users/${uid}/privacy/current`, token(uid, false)), 200);
  await check(history(uid, token(uid, false)), 200);
  await check((await decide(uid, "accepted", {verified: false})).response, 200);
  await check(request("organizations/PrivOrg1/women/patient", token(uid, false)), 403);
});

test("anonymous and different accounts cannot read, list, or write another account's evidence", async () => {
  const uid = "privacy-isolation";
  const record = await decide(uid);
  await check(record.response, 200);
  for (const auth of [undefined, token("privacy-intruder")]) {
    await check(request(`users/${uid}/privacy/current`, auth), 403);
    await check(request(`users/${uid}/privacyEvents/${record.id}`, auth), 403);
    await check(history(uid, auth ?? null), 403);
    await check(commit(auth, decisionWrites(uid).writes), 403);
  }
  await check(request(`users/${uid}/privacy`, token(uid)), 403);
  await check(request(`users/${uid}/privacy/other`, token(uid)), 403);
  await check(history(uid, token(uid), null), 403);
  await check(history(uid, token(uid), 101), 403);
});

test("events and current pointer must accompany each other, with matching decision and a fresh event", async () => {
  const uid = "privacy-atomic";
  await check((await decide(uid, "accepted", {receiptOnly: false})).response, 403);
  await check((await decide(uid, "accepted", {eventOnly: false})).response, 403);
  await check((await decide(uid, "accepted", {receipt: {decision: "denied"}})).response, 403);
  await check((await decide(uid, "accepted", {receipt: {lastEventId: "Z".repeat(20)}})).response, 403);
  const first = await decide(uid);
  await check(first.response, 200);
  await check((await decide(uid, "denied", {eventOnly: false, id: first.id})).response, 403);
  await check((await decide(uid, "accepted", {eventOnly: false, id: first.id})).response, 403);
});

test("historical evidence and deletion of the current receipt are forbidden even to its owner", async () => {
  const uid = "privacy-immutable";
  const record = await decide(uid);
  await check(record.response, 200);
  await check(request(`users/${uid}/privacyEvents/${record.id}`, token(uid), "PATCH", {...record.event, decision: "denied"}), 403);
  await check(request(`users/${uid}/privacyEvents/${record.id}`, token(uid), "DELETE"), 403);
  await check(request(`users/${uid}/privacy/current`, token(uid), "DELETE"), 403);
  await check((await decide(uid, "denied", {id: record.id})).response, 403);
});

test("schema, token identity, versions, hashes, metadata bounds, and server timestamps are enforced", async () => {
  const uid = "privacy-validation";
  const badEvents = [
    {userId: "other"}, {email: "someone@example.test"}, {emailVerified: false},
    {schemaVersion: 2}, {schemaVersion: 1.5}, {presentationVersion: 3},
    {policyVersion: "0.9"}, {policySha256: "0".repeat(64)}, {decision: "maybe"},
    {source: "forged_source"}, {language: ""}, {language: "a".repeat(65)},
    {userAgent: "a".repeat(513)}, {userAgent: 12}, {clientOccurredAtMillis: -1},
    {clientOccurredAtMillis: 1.5}, {clientOccurredAtMillis: 253402300800000},
    {extra: "unmodeled"}, {isAdmin: true}
  ];
  for (const event of badEvents) await check((await decide(uid, "accepted", {event})).response, 403);
  for (const receipt of [{userId: "other"}, {decision: true}, {policyVersion: "0.9"},
    {policySha256: "0".repeat(64)}, {lastEventId: "bad/path"}, {extra: "unmodeled"}]) {
    await check((await decide(uid, "accepted", {receipt})).response, 403);
  }
  for (const key of Object.keys(decisionWrites(uid).event)) await check((await decide(uid, "accepted", {omitEventFields: [key]})).response, 403);
  for (const key of Object.keys(decisionWrites(uid).receipt)) await check((await decide(uid, "accepted", {omitReceiptFields: [key]})).response, 403);
  await check((await decide(uid, "accepted", {id: "too-short"})).response, 403);
  await check((await decide(uid, "accepted", {eventTimestamps: false, event: {createdAt: new Date(0)}})).response, 403);
  await check((await decide(uid, "accepted", {receiptTimestamps: false, receipt: {updatedAt: new Date("2099-01-01")}})).response, 403);
  await check((await decide(uid, "accepted", {verified: false, event: {emailVerified: true}})).response, 403);
});

test("latest policy acceptance gates clinical reads; denial blocks and a new acceptance restores access", async () => {
  const uid = "privacy-reader";
  const path = "organizations/PrivOrg1/women/patient";
  await check(request(path, token(uid)), 403);
  await check((await decide(uid)).response, 200);
  await check(request(path, token(uid)), 200);
  await check((await decide(uid, "denied")).response, 200);
  await check(request(path, token(uid)), 403);
  await check((await decide(uid)).response, 200);
  await check(request(path, token(uid)), 200);
  await check(request(path, token("privacy-reader", false)), 403);
});

test("a stale policy version or content hash does not unlock organization records", async () => {
  const uid = "privacy-stale";
  const path = "organizations/PrivOrg1/women/patient";
  const receipt = {userId: uid, lastEventId: eventId(), decision: "accepted", policyVersion: "0.9", policySha256, updatedAt: new Date()};
  await check(request(`users/${uid}/privacy/current`, "owner", "PATCH", receipt), 200);
  await check(request(path, token(uid)), 403);
  await check(request(`users/${uid}/privacy/current`, "owner", "PATCH", {...receipt, policyVersion, policySha256: "0".repeat(64)}), 200);
  await check(request(path, token(uid)), 403);
  await check((await decide(uid)).response, 200);
  await check(request(path, token(uid)), 200);
});
