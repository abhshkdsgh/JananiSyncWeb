// Run with npm run test:security. No production services or real patient data.
const {before, test} = require("node:test");
const assert = require("node:assert/strict");
const {readFileSync} = require("node:fs");
const {createHash} = require("node:crypto");
const {join} = require("node:path");
const host = process.env.FIRESTORE_EMULATOR_HOST;
assert.match(host || "", /^(127\.0\.0\.1|localhost):\d+$/, "A local Firestore emulator is required");
const project = "demo-jananisync";
const base = `http://${host}/v1/projects/${project}/databases/(default)/documents`;
const policySha256 = createHash("sha256").update(readFileSync(join(__dirname, "../../docs/privacy-policy.md"))).digest("hex");
const privacyFixtures = new Map();

// These existing organization tests begin after account setup. Seed that prerequisite
// with emulator-admin authorization; privacy-consent.test.cjs tests the client protocol.
async function ensurePrivacyFixture(auth) {
  if (!auth || auth === "owner") return;
  const uid = JSON.parse(Buffer.from(auth.split(".")[1], "base64url")).sub;
  if (!privacyFixtures.has(uid)) {
    privacyFixtures.set(uid, (async () => {
      const receipt = {userId: uid, lastEventId: "A".repeat(20), decision: "accepted",
        policyVersion: "1.0", policySha256, updatedAt: new Date()};
      const response = await fetch(`${base}/users/${uid}/privacy/current`, {
        method: "PATCH", headers: {"Content-Type": "application/json", Authorization: "Bearer owner"},
        body: JSON.stringify({fields: Object.fromEntries(Object.entries(receipt).map(([k, v]) => [k, field(v)]))})
      });
      assert.equal(response.status, 200, await response.text());
    })());
  }
  await privacyFixtures.get(uid);
}

// The emulator accepts unsigned Firebase test tokens; production does not.
function token(uid, verified = true) {
  const encode = value => Buffer.from(JSON.stringify(value)).toString("base64url");
  const now = Math.floor(Date.now() / 1000);
  return `${encode({alg: "none", typ: "JWT"})}.${encode({
    iss: `https://securetoken.google.com/${project}`, aud: project,
    sub: uid, user_id: uid, email: `${uid}@example.test`, email_verified: verified,
    iat: now, exp: now + 3600, auth_time: now,
    firebase: {sign_in_provider: "password", identities: {}}
  })}.`;
}
function field(value) {
  if (value instanceof Date) return {timestampValue: value.toISOString()};
  if (typeof value === "number") return Number.isInteger(value) ? {integerValue: String(value)} : {doubleValue: value};
  if (typeof value === "boolean") return {booleanValue: value};
  if (Array.isArray(value)) return {arrayValue: {values: value.map(field)}};
  if (value && typeof value === "object") return {mapValue: {fields: Object.fromEntries(Object.entries(value).map(([k, v]) => [k, field(v)]))}};
  return {stringValue: value};
}
async function request(path, auth, method = "GET", data) {
  await ensurePrivacyFixture(auth);
  return fetch(`${base}/${path}`, {
    method,
    headers: {"Content-Type": "application/json", ...(auth ? {Authorization: `Bearer ${auth}`} : {})},
    body: data ? JSON.stringify({fields: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, field(v)]))}) : undefined
  });
}
async function expectStatus(path, auth, status, method, data) {
  const response = await request(path, auth, method, data);
  assert.equal(response.status, status, `${path}: ${await response.text()}`);
}

before(async () => {
  for (const org of ["OrgOne01", "OrgTwo02"]) {
    await expectStatus(`organizations/${org}`, "owner", 200, "PATCH", {name: org});
    await expectStatus(`organizations/${org}/keyMetadata/current`, "owner", 200, "PATCH", {
      currentKeyVersion: 1, algorithm: "AES-256-GCM", updatedBy: "admin", updatedAt: new Date()});
    for (const collection of ["wards", "women"]) {
      await expectStatus(`organizations/${org}/${collection}/record-a`, "owner", 200, "PATCH", {ciphertext: "YWJj"});
    }
    await expectStatus(`organizations/${org}/clinicalData/v1/lcgObservations/record-a`, "owner", 200, "PATCH", {ciphertext: "YWJj"});
  }
  for (const [uid, role, status] of [["admin", "admin", "active"], ["inactiveAdmin", "admin", "inactive"], ["doctor", "doctor", "active"], ["nurse", "nurse", "active"], ["inactive", "doctor", "inactive"]]) {
    await expectStatus(`organizations/OrgOne01/members/${uid}`, "owner", 200, "PATCH", {
      userId: uid, name: uid, role, status, accessLevel: 10, joinedAt: new Date()
    });
  }
});

for (const collection of ["wards", "women", "clinicalData/v1/lcgObservations"]) {
  test(`${collection}: direct document and list requests enforce membership`, async () => {
    const own = `organizations/OrgOne01/${collection}`;
    const foreign = `organizations/OrgTwo02/${collection}`;
    for (const auth of [undefined, token("stranger"), token("inactive"), token("doctor", false)]) {
      await expectStatus(`${own}/record-a`, auth, 403);
      await expectStatus(own, auth, 403);
    }
    for (const uid of ["admin", "doctor", "nurse"]) {
      await expectStatus(`${own}/record-a`, token(uid), 200);
      await expectStatus(own, token(uid), 200);
      await expectStatus(`${foreign}/record-a`, token(uid), 403);
      await expectStatus(foreign, token(uid), 403);
    }
  });
}

test("admins can read clinical rosters while organization and verification checks still apply", async () => {
  for (const suffix of ["", "/record-a"]) {
    await expectStatus(`organizations/OrgOne01/wards${suffix}`, token("admin"), 200);
    await expectStatus(`organizations/OrgTwo02/wards${suffix}`, token("admin"), 403);
    await expectStatus(`organizations/OrgOne01/wards${suffix}`, token("inactiveAdmin"), 403);
    await expectStatus(`organizations/OrgOne01/wards${suffix}`, token("admin", false), 403);
    await expectStatus(`organizations/OrgOne01/women${suffix}`, token("admin"), 200);
    await expectStatus(`organizations/OrgOne01/clinicalData/v1/lcgObservations${suffix}`, token("admin"), 200);
  }
});

test("clinical user cannot promote their own membership or grant themselves access", async () => {
  await expectStatus("organizations/OrgOne01/members/doctor", token("doctor"), 403, "PATCH", {
    userId: "doctor", name: "doctor", role: "admin", status: "active", accessLevel: 99, joinedAt: new Date()
  });
  await expectStatus("organizations/OrgTwo02/members/doctor", token("doctor"), 403, "PATCH", {
    userId: "doctor", name: "doctor", role: "doctor", status: "active", accessLevel: 99, joinedAt: new Date()
  });
  await expectStatus("organizations/OrgOne01/keyEnvelopes/doctor", token("doctor"), 403, "PATCH", {
    userId: "doctor", keyVersion: 1, wrappedKey: "YWJj", algorithm: "RSA-OAEP-3072-SHA256", grantedBy: "doctor", createdAt: new Date()
  });
});

test("clinical user cannot write ward administration data or another user's membership", async () => {
  await expectStatus("organizations/OrgOne01/wards/record-a", token("doctor"), 403, "PATCH", {ciphertext: "YWJj"});
  await expectStatus("organizations/OrgOne01/members/nurse", token("doctor"), 403, "PATCH", {
    userId: "nurse", name: "nurse", role: "admin", status: "active", accessLevel: 99, joinedAt: new Date()
  });
});

const columnTimes = new Map();
const physicalColumns = new Map();
let fixtureColumnIndex = 100;
const physicalColumn = alias => physicalColumns.get(alias)?.id || alias;
async function clinicalCommit(uid, columnId, eventId, state, cellKey, cellKeys, confirmation = false, extra = {}, options = {}) {
  await ensurePrivacyFixture(token(uid));
  const alias = columnId;
  state = {womanDocumentId: "record-a", ...state};
  if (state.revision === 1 && !physicalColumns.has(alias)) {
    const index = extra.chartColumnIndex ?? fixtureColumnIndex++;
    physicalColumns.set(alias, {index, id: alias.startsWith("header-") ? alias : `timeline-${state.womanDocumentId}-${index}`});
  }
  columnId = options.forceColumnId || physicalColumn(alias);
  const half2 = cellKeys.some(key => key.endsWith("Half2"));
  const defaultTime = columnTimes.get(alias) ?? (extra.observationTimeMillis ?? Date.now() - 3600000) - (half2 ? 1800000 : 0);
  if (state.revision === 1) state = {sessionId: alias.startsWith("header-") ? alias.slice(7) : "admission", timestampMillis: defaultTime, chartColumnIndex: physicalColumns.get(alias)?.index ?? 0, chartStartMillis: extra.chartStartMillis ?? defaultTime, ...state};
  const root = `projects/${project}/databases/(default)/documents/organizations/OrgOne01/clinicalData/v1`;
  const event = {ciphertext: "YWJj", iv: "AAAAAAAAAAAAAAAA", keyVersion: 1, algorithm: "AES-GCM",
    schemaVersion: 2, createdBy: uid, accessLevel: ({senior: 20, senior24: 24, senior25: 25, senior40: 40, readonly60: 60, readonly99: 99})[uid] || 10,
    recordType: confirmation ? "confirmation" : "cell", womanDocumentId: state.womanDocumentId, columnId, cellKey, revision: state.revision,
    cellKeys: confirmation ? ["initials"] : cellKeys,
    ...(!confirmation ? {observationTimeMillis: defaultTime + (half2 ? 1800000 : 0)} : {}), ...extra};
  const write = (path, data, timestamps) => ({update: {name: `${root}/${path}`,
    fields: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, field(v)]))},
    updateTransforms: timestamps.map(fieldPath => ({fieldPath, setToServerValue: "REQUEST_TIME"})),
    ...(path.startsWith("lcgColumns/") ? {updateMask: {fieldPaths: Object.keys(data)}} : {})});
  const response = await fetch(`${base}:commit`, {method: "POST", headers: {
    "Content-Type": "application/json", Authorization: `Bearer ${token(uid)}`}, body: JSON.stringify({writes: [
      ...(extra.chartStartMillis !== undefined ? [write(`chartStarts/${state.womanDocumentId}`,
        {startMillis: extra.chartStartMillis, womanDocumentId: state.womanDocumentId, sessionId: "admission"}, [])] : []),
      write(`lcgColumns/${columnId}`, state, confirmation ? ["confirmedAt"] : ["lastEditedAt"]),
      write(`lcgObservations/${eventId}`, event, ["createdAt"]),
      ...(options.omitProjections ? [] : (options.projectionKeys || event.cellKeys).map(key => write(`lcgCells/${columnId}~${key}`,
        {sourceEventId: eventId, columnId, womanDocumentId: state.womanDocumentId, cellKey: key, event: {...event, ...options.projectionEvent}}, ["event.createdAt"])))
    ]})});
  if (response.status === 200 && state.timestampMillis !== undefined) columnTimes.set(alias, state.timestampMillis);
  return {status: response.status, body: await response.text()};
}

test("admins can record and confirm clinical observations at their assigned access level", async () => {
  const state = {lastEditorUid: "admin", lastEditorAccessLevel: 10, highestEditorAccessLevel: 10,
    highestEditorUids: ["admin"], revision: 1, confirmed: false, lastEventId: "admin-cell",
    cellAccessLevels: {pulse: 10}};
  const created = await clinicalCommit("admin", "admin-clinical", "admin-cell", state, "pulse", ["pulse"]);
  assert.equal(created.status, 200, created.body);
  const confirmation = {...state, revision: 2, confirmed: true, confirmedBy: "admin", confirmationEventId: "admin-confirm"};
  const confirmed = await clinicalCommit("admin", "admin-clinical", "admin-confirm", confirmation, "initials", [], true);
  assert.equal(confirmed.status, 200, confirmed.body);
});

test("cell access applies only to touched cells and graph edits are atomic", async () => {
  await expectStatus("organizations/OrgOne01/members/senior", "owner", 200, "PATCH", {
    userId: "senior", name: "senior", role: "doctor", status: "active", accessLevel: 20, joinedAt: new Date()});
  const initial = {lastEditorUid: "senior", lastEditorAccessLevel: 20, highestEditorAccessLevel: 20,
    highestEditorUids: ["senior"], revision: 1, confirmed: false, lastEventId: "cell-first",
    cellAccessLevels: {pulse: 20}};
  const check = (result, expected) => assert.equal(result.status, expected, result.body);
  check(await clinicalCommit("senior", "cell-scope", "cell-first", initial, "pulse", ["pulse"]), 200);
  const lower = {...initial, lastEditorUid: "doctor", lastEditorAccessLevel: 10,
    revision: 2, lastEventId: "cell-other", cellAccessLevels: {pulse: 20, temperature: 10}};
  check(await clinicalCommit("doctor", "cell-scope", "cell-other", lower, "temperature", ["temperature"]), 200);
  const blocked = {...lower, revision: 3, lastEventId: "cell-blocked", cellAccessLevels: {pulse: 10, temperature: 10}};
  check(await clinicalCommit("doctor", "cell-scope", "cell-blocked", blocked, "pulse", ["pulse"]), 403);
  check(await clinicalCommit("doctor", "cell-scope", "cell-blocked", {...blocked,
    cellAccessLevels: {...blocked.cellAccessLevels, systolic: 10}}, "partographMark", ["pulse", "systolic"]), 403);
  const graph = {...lower, revision: 3, lastEventId: "cell-graph",
    cellAccessLevels: {...lower.cellAccessLevels, systolic: 10, diastolic: 10}};
  check(await clinicalCommit("doctor", "cell-scope", "cell-graph", graph, "partographMark", ["systolic", "diastolic"]), 200);
  check(await clinicalCommit("doctor", "cell-scope", "cell-tamper", {...graph, revision: 4, lastEventId: "cell-tamper",
    cellAccessLevels: {temperature: 10, systolic: 10, diastolic: 10}}, "temperature", ["temperature"]), 403);
  check(await clinicalCommit("doctor", "cell-scope", "cell-invalid", {...graph, revision: 4, lastEventId: "cell-invalid",
    cellAccessLevels: {...graph.cellAccessLevels, medicine: 10}}, "medicine", ["medicine", "medicine"]), 403);
  check(await clinicalCommit("doctor", "cell-scope", "cell-mismatch", {...graph, revision: 4, lastEventId: "cell-mismatch",
    cellAccessLevels: {...graph.cellAccessLevels, medicine: 10}}, "pulse", ["medicine"]), 403);
  const confirmed = {...graph, confirmed: true, confirmedBy: "senior", confirmationEventId: "cell-confirm", revision: 4};
  check(await clinicalCommit("senior", "cell-scope", "cell-confirm", confirmed, "initials", [], true), 200);
  check(await clinicalCommit("doctor", "cell-scope", "cell-confirmed-edit", {...confirmed, revision: 5,
    lastEventId: "cell-confirmed-edit", cellAccessLevels: {...confirmed.cellAccessLevels, medicine: 10}}, "medicine", ["medicine"]), 200);
});

test("confirmed columns remain editable and corrections retain per-cell access and reason checks", async () => {
  const check = (result, expected) => assert.equal(result.status, expected, result.body);
  const initial = {lastEditorUid: "senior", lastEditorAccessLevel: 20, highestEditorAccessLevel: 20,
    highestEditorUids: ["senior"], revision: 1, confirmed: false, lastEventId: "correction-initial",
    cellAccessLevels: {pulse: 20}};
  check(await clinicalCommit("senior", "correction-scope", "correction-initial", initial, "pulse", ["pulse"]), 200);
  const confirmed = {...initial, confirmed: true, confirmedBy: "doctor", confirmationEventId: "correction-confirm", revision: 2};
  // Confirmation no longer requires the highest editor or a +5 access threshold.
  check(await clinicalCommit("doctor", "correction-scope", "correction-confirm", confirmed, "initials", [], true), 200);
  const state = (uid, access, event, levels) => ({...confirmed, lastEditorUid: uid, lastEditorAccessLevel: access,
    revision: 3, lastEventId: event, cellAccessLevels: {...confirmed.cellAccessLevels, ...levels}});
  check(await clinicalCommit("doctor", "correction-scope", "correction-low-edit",
    state("doctor", 10, "correction-low-edit", {pulse: 10}), "pulse", ["pulse"]), 403);
  const reason = {recordType: "cellCorrection", reasonCiphertext: "AAAAAAAAAAAAAAAAAAAAAAAA", reasonIv: "AAAAAAAAAAAAAAAA", correctedRevision: 2};
  check(await clinicalCommit("doctor", "correction-scope", "correction-low",
    state("doctor", 10, "correction-low", {pulse: 10}), "pulse", ["pulse"], false, reason), 403);
  check(await clinicalCommit("senior", "correction-scope", "correction-no-reason",
    state("senior", 20, "correction-no-reason", {pulse: 20}), "pulse", ["pulse"], false,
    {...reason, reasonCiphertext: ""}), 403);
  check(await clinicalCommit("senior", "correction-scope", "correction-equal",
    state("senior", 20, "correction-equal", {pulse: 20}), "pulse", ["pulse"], false, reason), 200);
  const normal = {...state("doctor", 10, "correction-after-confirm", {temperature: 10}), revision: 4};
  check(await clinicalCommit("doctor", "correction-scope", "correction-after-confirm", normal, "temperature", ["temperature"]), 200);
  const future = {...normal, revision: 5, lastEventId: "future-reading"};
  check(await clinicalCommit("doctor", "correction-scope", "future-reading", future, "temperature", ["temperature"], false,
    {observationTimeMillis: Date.now() + 3600000}), 403);
});

test("headers remain editable with history, including legacy confirmed headers", async () => {
  const id = "header-editable";
  const initial = {womanDocumentId: "record-a", lastEditorUid: "doctor", lastEditorAccessLevel: 10, highestEditorAccessLevel: 20,
    highestEditorUids: ["senior"], revision: 3, confirmed: true, confirmedBy: "senior",
    confirmationEventId: "legacy-header-confirm", lastEventId: "legacy-header-event",
    cellAccessLevels: {riskFactors: 20}};
  await expectStatus(`organizations/OrgOne01/clinicalData/v1/lcgColumns/${id}`, "owner", 200, "PATCH", initial);
  const updated = {...initial, confirmed: false, revision: 4, lastEventId: "header-unlocked",
    cellAccessLevels: {riskFactors: 10}};
  const check = (result, expected) => assert.equal(result.status, expected, result.body);
  check(await clinicalCommit("doctor", id, "header-unlocked", updated, "riskFactors", ["riskFactors"]), 200);
  const next = {...updated, revision: 5, lastEventId: "header-next"};
  check(await clinicalCommit("doctor", id, "header-next", next, "riskFactors", ["riskFactors"]), 200);
  check(await clinicalCommit("doctor", id, "header-grid-bypass", {...next, revision: 6,
    lastEventId: "header-grid-bypass", cellAccessLevels: {riskFactors: 10, pulse: 10}}, "pulse", ["pulse"]), 403);
  check(await clinicalCommit("senior", id, "header-confirm-denied", {...next, revision: 6,
    confirmed: true, confirmedBy: "senior", confirmationEventId: "header-confirm-denied"}, "initials", [], true), 403);
  check(await clinicalCommit("inactive", id, "header-inactive", {...next, revision: 6,
    lastEditorUid: "inactive", lastEventId: "header-inactive"}, "riskFactors", ["riskFactors"]), 403);
  const history = await request(`organizations/OrgOne01/clinicalData/v1/lcgObservations/header-unlocked`, token("doctor"));
  assert.equal(history.status, 200);
});


test("organization metadata supports root fields while enforcing membership and blocking private fields", async () => {
  const path = "organizations/OrgOne01";
  const metadata = {name: "House Haritham", organizationName: "Haritham", createdAt: new Date(), status: "active"};
  await expectStatus(path, "owner", 200, "PATCH", metadata);
  for (const uid of ["doctor", "nurse"]) {
    await expectStatus(path, token(uid), 200);
    await expectStatus("organizations/OrgTwo02", token(uid), 403);
    await expectStatus("organizations", token(uid), 403);
    await expectStatus(path, token(uid), 403, "PATCH", metadata);
    await expectStatus(path, token(uid), 403, "DELETE");
    await expectStatus("organizations/NewOrg01", token(uid), 403, "PATCH", metadata);
  }
  for (const auth of [undefined, token("stranger"), token("inactive"), token("doctor", false)]) {
    await expectStatus(path, auth, 403);
  }
  await expectStatus(path, "owner", 200, "PATCH", {...metadata, privateKey: "must-not-leak"});
  await expectStatus(path, token("doctor"), 403);
  await expectStatus(path, "owner", 200, "PATCH", {name: "Legacy name"});
  await expectStatus(path, token("doctor"), 200);
});

async function archiveCommit(uid, womanId, archived, revision, eventId, options = {}) {
  await ensurePrivacyFixture(token(uid));
  const org = options.org || "OrgOne01";
  const root = `projects/${project}/databases/(default)/documents/organizations/${org}`;
  const status = {archived, archiveRevision: revision, lastArchiveEventId: eventId, ...options.status};
  const event = {womanDocumentId: womanId, archived, revision, createdBy: uid,
    accessLevel: ({archive59: 59, archive60: 60, archive99: 99, inactiveArchive: 60, readonly60: 60, readonly99: 99})[uid] || 10,
    ciphertext: "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", iv: "AAAAAAAAAAAAAAAA",
    keyVersion: 1, algorithm: "AES-GCM", schemaVersion: 2, ...options.event};
  const writes = [];
  if (options.includeStatus !== false) writes.push({
    update: {name: `${root}/women/${womanId}`, fields: Object.fromEntries(Object.entries(status).map(([k, v]) => [k, field(v)]))},
    updateMask: {fieldPaths: Object.keys(status)}
  });
  if (options.includeEvent !== false) writes.push({
    update: {name: `${root}/rosterArchiveEvents/${eventId}`, fields: Object.fromEntries(Object.entries(event).map(([k, v]) => [k, field(v)]))},
    updateTransforms: [{fieldPath: "createdAt", setToServerValue: "REQUEST_TIME"}]
  });
  const response = await fetch(`${base}:commit`, {method: "POST", headers: {
    "Content-Type": "application/json", Authorization: `Bearer ${token(uid, options.verified !== false)}`},
    body: JSON.stringify({writes})});
  return {status: response.status, body: await response.text()};
}

test("archiving requires level 60 and atomic immutable history; unarchiving preserves clinical data", async () => {
  for (const [uid, level, status] of [["archive59", 59, "active"], ["archive60", 60, "active"],
    ["archive99", 99, "active"], ["inactiveArchive", 60, "inactive"]]) {
    await expectStatus(`organizations/OrgOne01/members/${uid}`, "owner", 200, "PATCH", {
      userId: uid, name: uid, role: "nurse", status, accessLevel: level, joinedAt: new Date()
    });
  }
  const envelope = {ciphertext: "YWJj", iv: "AAAAAAAAAAAAAAAA", keyVersion: 1, algorithm: "AES-GCM",
    schemaVersion: 1, createdBy: "doctor", createdAt: new Date()};
  for (const org of ["OrgOne01", "OrgTwo02"]) {
    await expectStatus(`organizations/${org}/women/archive-woman`, "owner", 200, "PATCH", envelope);
  }
  const check = (result, expected) => assert.equal(result.status, expected, result.body);
  for (const uid of ["archive59", "admin", "inactiveArchive"]) {
    check(await archiveCommit(uid, "archive-woman", true, 1, `denied-${uid}`), 403);
  }
  check(await archiveCommit("archive60", "archive-woman", true, 1, "unverified-archive", {verified: false}), 403);
  check(await archiveCommit("archive60", "archive-woman", true, 1, "foreign-archive", {org: "OrgTwo02"}), 403);
  check(await archiveCommit("archive60", "missing-woman", true, 1, "missing-archive"), 403);
  check(await archiveCommit("archive60", "archive-woman", true, 1, "no-history", {includeEvent: false}), 403);
  check(await archiveCommit("archive60", "archive-woman", true, 1, "orphan-history", {includeStatus: false}), 403);
  check(await archiveCommit("archive60", "archive-woman", true, 1, "fake-author", {event: {createdBy: "archive99"}}), 403);
  check(await archiveCommit("archive60", "archive-woman", true, 1, "fake-access", {event: {accessLevel: 99}}), 403);
  check(await archiveCommit("archive60", "archive-woman", true, 1, "changed-record", {status: {ciphertext: "AAAA"}}), 403);
  check(await archiveCommit("archive60", "archive-woman", true, 1, "wrong-action", {event: {archived: false}}), 403);
  check(await archiveCommit("archive60", "archive-woman", true, 1, "polluted-history", {event: {extra: true}}), 403);
  check(await archiveCommit("archive60", "archive-woman", false, 1, "unarchive-active"), 403);
  check(await archiveCommit("archive60", "archive-woman", true, 1, "archive-first"), 200);
  check(await archiveCommit("archive99", "archive-woman", true, 1, "stale-archive"), 403);
  check(await archiveCommit("archive59", "archive-woman", false, 2, "low-unarchive"), 403);
  check(await archiveCommit("archive99", "archive-woman", false, 2, "unarchive-second"), 200);
  await expectStatus("organizations/OrgOne01/rosterArchiveEvents/archive-first", token("archive60"), 403, "PATCH", {archived: false});
  await expectStatus("organizations/OrgOne01/rosterArchiveEvents/archive-first", token("archive60"), 403, "DELETE");
  await expectStatus("organizations/OrgOne01/women/archive-woman", token("archive60"), 403, "DELETE");
  for (const uid of ["doctor", "nurse", "admin"]) {
    await expectStatus("organizations/OrgOne01/rosterArchiveEvents", token(uid), 200);
  }
  for (const auth of [undefined, token("inactiveArchive"), token("stranger"), token("archive60", false)]) {
    await expectStatus("organizations/OrgOne01/rosterArchiveEvents", auth, 403);
  }
  await expectStatus("organizations/OrgTwo02/rosterArchiveEvents", token("archive60"), 403);
  const response = await request("organizations/OrgOne01/women/archive-woman", token("doctor"));
  const data = (await response.json()).fields;
  assert.equal(data.ciphertext.stringValue, envelope.ciphertext);
  assert.equal(data.archived.booleanValue, false);
  assert.equal(data.archiveRevision.integerValue, "2");
  assert.equal(data.createdBy.stringValue, "doctor");
  const eventResponse = await request("organizations/OrgOne01/rosterArchiveEvents/unarchive-second", token("doctor"));
  const event = (await eventResponse.json()).fields;
  assert.equal(event.createdBy.stringValue, "archive99");
  assert.equal(event.accessLevel.integerValue, "99");
  assert.ok(event.createdAt.timestampValue);
  check(await archiveCommit("archive99", "archive-woman", true, 3, "archive-third"), 200);
  check(await archiveCommit("archive60", "archive-woman", false, 4, "unarchive-fourth"), 200);
});

test("archive and restore preserve a historical roster envelope after key rotation", async () => {
  const org = "HistOrg1", root = `organizations/${org}`;
  const check = (result, expected) => assert.equal(result.status, expected, result.body);
  await expectStatus(root, "owner", 200, "PATCH", {name: "Historical envelope"});
  for (const [uid, role, level] of [["admin", "admin", 10], ["archive60", "nurse", 60]]) {
    await expectStatus(`${root}/members/${uid}`, "owner", 200, "PATCH", {
      userId: uid, name: uid, role, status: "active", accessLevel: level, joinedAt: new Date()
    });
  }
  await expectStatus(`${root}/keyMetadata/current`, "owner", 200, "PATCH", {
    currentKeyVersion: 1, algorithm: "AES-256-GCM", updatedBy: "admin", updatedAt: new Date()
  });
  const envelope = version => ({userId: "admin", keyVersion: version, wrappedKey: "YWJj",
    algorithm: "RSA-OAEP-3072-SHA256", grantedBy: "admin"});
  await expectStatus(`${root}/keyEnvelopes/admin`, "owner", 200, "PATCH", {...envelope(1), createdAt: new Date()});
  const roster = {ciphertext: "YWJj", iv: "AAAAAAAAAAAAAAAA", keyVersion: 1, algorithm: "AES-GCM",
    schemaVersion: 1, createdBy: "archive60"};
  check(await atomicCommit("archive60", [{path: `${root}/women/historical`, data: roster, timestamps: ["createdAt"]}]), 200);
  await expectStatus(`${root}/members/departing`, "owner", 200, "PATCH", {
    userId: "departing", name: "Departing", role: "nurse", status: "active", accessLevel: 10, joinedAt: new Date()
  });
  await completeRotation(org, "departing", ["admin", "archive60"], 1);
  check(await atomicCommit("archive60", [{path: `${root}/women/stale-create`, data: roster, timestamps: ["createdAt"]}]), 403);
  check(await archiveCommit("archive60", "historical", true, 1, "historical-mutated", {
    org, status: {ciphertext: "AAAA", keyVersion: 2}, event: {keyVersion: 2}
  }), 403);
  check(await archiveCommit("archive60", "historical", true, 1, "historical-old-event", {
    org, event: {keyVersion: 1}
  }), 403);
  check(await archiveCommit("archive60", "historical", true, 1, "historical-archive", {
    org, event: {keyVersion: 2}
  }), 200);
  check(await archiveCommit("archive60", "historical", false, 2, "historical-restore", {
    org, event: {keyVersion: 2}
  }), 200);
  const data = (await (await request(`${root}/women/historical`, token("archive60"))).json()).fields;
  assert.equal(data.keyVersion.integerValue, "1");
  assert.equal(data.ciphertext.stringValue, roster.ciphertext);
  assert.equal(data.archived.booleanValue, false);
});

test("archived clinical records are read-only, including headers, corrections, legacy columns and new time points", async () => {
  for (const [uid, level] of [["readonly60", 60], ["readonly99", 99]]) {
    await expectStatus(`organizations/OrgOne01/members/${uid}`, "owner", 200, "PATCH", {
      userId: uid, name: uid, role: "doctor", status: "active", accessLevel: level, joinedAt: new Date()
    });
  }
  await expectStatus("organizations/OrgOne01/women/readonly-woman", "owner", 200, "PATCH", {
    ciphertext: "YWJj", iv: "AAAAAAAAAAAAAAAA", keyVersion: 1, algorithm: "AES-GCM",
    schemaVersion: 1, createdBy: "doctor", createdAt: new Date()
  });
  const baseState = {womanDocumentId: "readonly-woman", lastEditorUid: "readonly60", lastEditorAccessLevel: 60,
    highestEditorAccessLevel: 60, highestEditorUids: ["readonly60"], revision: 1, confirmed: false,
    lastEventId: "readonly-initial", cellAccessLevels: {pulse: 60}};
  const header = {...baseState, lastEventId: "readonly-header", cellAccessLevels: {riskFactors: 60}};
  const check = (result, expected) => assert.equal(result.status, expected, result.body);
  check(await clinicalCommit("readonly60", "readonly-open", "readonly-initial", baseState, "pulse", ["pulse"]), 200);
  check(await clinicalCommit("readonly60", "header-readonly", "readonly-header", header, "riskFactors", ["riskFactors"]), 200);
  check(await clinicalCommit("readonly60", "readonly-confirmed", "readonly-to-confirm",
    {...baseState, lastEventId: "readonly-to-confirm"}, "pulse", ["pulse"]), 200);
  const confirmed = {...baseState, lastEventId: "readonly-to-confirm", revision: 2, confirmed: true,
    confirmedBy: "readonly60", confirmationEventId: "readonly-confirm"};
  check(await clinicalCommit("readonly60", "readonly-confirmed", "readonly-confirm", confirmed, "initials", [], true), 200);
  check(await archiveCommit("readonly60", "readonly-woman", true, 1, "readonly-archive"), 200);
  const edited = {...baseState, revision: 2, lastEventId: "readonly-edit"};
  check(await clinicalCommit("readonly60", "readonly-open", "readonly-edit", edited, "pulse", ["pulse"]), 403);
  const editedHeader = {...header, revision: 2, lastEventId: "readonly-header-edit"};
  check(await clinicalCommit("readonly60", "header-readonly", "readonly-header-edit", editedHeader, "riskFactors", ["riskFactors"]), 403);
  check(await clinicalCommit("readonly60", "readonly-new", "readonly-new-event",
    {...baseState, lastEventId: "readonly-new-event"}, "pulse", ["pulse"]), 403);
  check(await clinicalCommit("readonly60", "readonly-open", "readonly-late-confirm",
    {...baseState, revision: 2, confirmed: true, confirmedBy: "readonly60", confirmationEventId: "readonly-late-confirm"}, "initials", [], true), 403);
  const correction = {...confirmed, revision: 3, lastEventId: "readonly-correction", lastEditorUid: "readonly99",
    lastEditorAccessLevel: 99, highestEditorAccessLevel: 99, highestEditorUids: ["readonly99"], cellAccessLevels: {pulse: 99}};
  const reason = {recordType: "cellCorrection", reasonCiphertext: "AAAAAAAAAAAAAAAAAAAAAAAA", reasonIv: "AAAAAAAAAAAAAAAA", correctedRevision: 2};
  check(await clinicalCommit("readonly99", "readonly-confirmed", "readonly-correction", correction, "pulse", ["pulse"], false, reason), 403);
  check(await clinicalCommit("readonly60", "readonly-open", "readonly-remap", {...edited,
    womanDocumentId: "record-a", lastEventId: "readonly-remap"}, "pulse", ["pulse"]), 403);
  // Historical records without a roster link stay locked until a trusted user links them.
  const legacy = {...baseState, lastEditedAt: new Date()};
  delete legacy.womanDocumentId;
  await expectStatus("organizations/OrgOne01/clinicalData/v1/lcgColumns/readonly-legacy", "owner", 200, "PATCH", legacy);
  await expectStatus("organizations/OrgOne01/clinicalData/v1/lcgColumns/readonly-legacy", token("doctor"), 403, "PATCH", {
    ...legacy, womanDocumentId: "readonly-woman"});
  await expectStatus("organizations/OrgOne01/clinicalData/v1/lcgColumns/readonly-legacy", token("readonly60"), 403, "PATCH", {
    ...legacy, womanDocumentId: "readonly-woman", revision: 2});
  await expectStatus("organizations/OrgOne01/clinicalData/v1/lcgColumns/readonly-legacy", token("readonly60"), 200, "PATCH", {
    ...legacy, womanDocumentId: "readonly-woman"});
  await expectStatus("organizations/OrgOne01/clinicalData/v1/lcgColumns/readonly-legacy", token("readonly60"), 200, "PATCH", {
    ...legacy, womanDocumentId: "readonly-woman"});
  await expectStatus("organizations/OrgOne01/clinicalData/v1/lcgColumns/readonly-legacy", token("readonly60"), 403, "PATCH", {
    ...legacy, womanDocumentId: "record-a"});
  check(await clinicalCommit("readonly60", "readonly-legacy", "readonly-legacy-edit",
    {...edited, lastEventId: "readonly-legacy-edit"}, "pulse", ["pulse"]), 403);
  await expectStatus(`organizations/OrgOne01/clinicalData/v1/lcgColumns/${physicalColumn("readonly-open")}`, token("doctor"), 200);
  await expectStatus("organizations/OrgOne01/clinicalData/v1/lcgObservations/readonly-initial", token("doctor"), 200);
  // Once restored, the very same clinical edits succeed under the original access checks.
  check(await archiveCommit("readonly99", "readonly-woman", false, 2, "readonly-unarchive"), 200);
  check(await clinicalCommit("readonly60", "readonly-open", "readonly-edit", edited, "pulse", ["pulse"]), 200);
  check(await clinicalCommit("readonly60", "header-readonly", "readonly-header-edit", editedHeader, "riskFactors", ["riskFactors"]), 200);
  check(await clinicalCommit("readonly99", "readonly-confirmed", "readonly-correction", correction, "pulse", ["pulse"], false, reason), 200);
  check(await clinicalCommit("readonly60", "readonly-new", "readonly-new-event",
    {...baseState, lastEventId: "readonly-new-event"}, "pulse", ["pulse"]), 200);
});


test("chart start is immutable and scheduled half-hours cannot be recorded in advance", async () => {
  const check = (result, status) => assert.equal(result.status, status, result.body);
  const start = Date.now() - 3 * 3600000;
  const state = {lastEditorUid: "doctor", lastEditorAccessLevel: 10, highestEditorAccessLevel: 10,
    highestEditorUids: ["doctor"], revision: 1, confirmed: false, lastEventId: "timeline-start",
    cellAccessLevels: {fetalHeartRateHalf1: 10}};
  const metadata = {chartStartMillis: start, chartColumnIndex: 0, observationTimeMillis: start};
  check(await clinicalCommit("doctor", "timeline-record-a-0", "timeline-start", state,
    "fetalHeartRateHalf1", ["fetalHeartRateHalf1"], false, metadata), 200);
  const update = {...state, revision: 2, lastEventId: "timeline-half2", cellAccessLevels: {...state.cellAccessLevels, fetalHeartRateHalf2: 10}};
  check(await clinicalCommit("doctor", "timeline-record-a-0", "timeline-half2", update,
    "fetalHeartRateHalf2", ["fetalHeartRateHalf2"], false, {...metadata, observationTimeMillis: start + 1800000}), 200);
  const oxy = {...update, revision: 3, lastEventId: "timeline-oxy-half2", cellAccessLevels: {...update.cellAccessLevels, oxytocinHalf2: 10}};
  check(await clinicalCommit("doctor", "timeline-record-a-0", "timeline-oxy-half2", oxy,
    "oxytocinHalf2", ["oxytocinHalf2"], false, {...metadata, observationTimeMillis: start + 1800000}), 200);
  check(await clinicalCommit("doctor", "timeline-record-a-3", "timeline-oxy-future", {...state, lastEventId: "timeline-oxy-future", cellAccessLevels: {oxytocinHalf2: 10}},
    "oxytocinHalf2", ["oxytocinHalf2"], false, {...metadata, chartColumnIndex: 3, observationTimeMillis: start + 12600000}), 403);
  const next = {...state, lastEventId: "timeline-gap"};
  check(await clinicalCommit("doctor", "timeline-record-a-2", "timeline-gap", next,
    "fetalHeartRateHalf1", ["fetalHeartRateHalf1"], false,
    {...metadata, chartColumnIndex: 2, observationTimeMillis: start + 7200000}), 200);
  check(await clinicalCommit("doctor", "timeline-record-a-1", "timeline-wrong-start", {...state, lastEventId: "timeline-wrong-start"},
    "fetalHeartRateHalf1", ["fetalHeartRateHalf1"], false,
    {...metadata, chartStartMillis: start - 60000, chartColumnIndex: 1, observationTimeMillis: start + 3540000}), 403);
  check(await clinicalCommit("doctor", "timeline-record-a-4", "timeline-future", {...state, lastEventId: "timeline-future"},
    "fetalHeartRateHalf1", ["fetalHeartRateHalf1"], false,
    {...metadata, chartColumnIndex: 4, observationTimeMillis: start + 14400000}), 403);
  check(await clinicalCommit("doctor", "timeline-record-a-1", "timeline-off-grid", {...state, lastEventId: "timeline-off-grid"},
    "fetalHeartRateHalf1", ["fetalHeartRateHalf1"], false,
    {...metadata, chartColumnIndex: 1, observationTimeMillis: start + 3600001}), 403);
});


test("legacy oxytocin access protects first-half migration while second-half readings are independent", async () => {
  const check = (result, status) => assert.equal(result.status, status, result.body);
  const initial = {lastEditorUid: "senior", lastEditorAccessLevel: 20, highestEditorAccessLevel: 20,
    highestEditorUids: ["senior"], revision: 1, confirmed: false, lastEventId: "oxy-legacy",
    cellAccessLevels: {oxytocin: 20}};
  check(await clinicalCommit("senior", "oxy-legacy-column", "oxy-legacy", initial, "oxytocin", ["oxytocin"]), 200);
  const lower = {...initial, lastEditorUid: "doctor", lastEditorAccessLevel: 10, revision: 2,
    lastEventId: "oxy-first-denied", cellAccessLevels: {oxytocin: 20, oxytocinHalf1: 10}};
  check(await clinicalCommit("doctor", "oxy-legacy-column", "oxy-first-denied", lower, "oxytocinHalf1", ["oxytocinHalf1"]), 403);
  const second = {...lower, lastEventId: "oxy-second", cellAccessLevels: {oxytocin: 20, oxytocinHalf2: 10}};
  check(await clinicalCommit("doctor", "oxy-legacy-column", "oxy-second", second, "oxytocinHalf2", ["oxytocinHalf2"]), 200);
});


test("partograph second-half fields retain independent cell access and scheduled observation times", async () => {
  const check = (result, status) => assert.equal(result.status, status, result.body);
  const first = {lastEditorUid: "doctor", lastEditorAccessLevel: 10, highestEditorAccessLevel: 10,
    highestEditorUids: ["doctor"], revision: 1, confirmed: false, lastEventId: "half-pulse-first", cellAccessLevels: {pulse: 10}};
  check(await clinicalCommit("doctor", "half-pulse", "half-pulse-first", first, "pulse", ["pulse"]), 200);
  const second = {...first, lastEditorUid: "senior", lastEditorAccessLevel: 20, highestEditorAccessLevel: 20,
    highestEditorUids: ["senior"], revision: 2, lastEventId: "half-pulse-second", cellAccessLevels: {pulse: 10, pulseHalf2: 20}};
  check(await clinicalCommit("senior", "half-pulse", "half-pulse-second", second, "pulseHalf2", ["pulseHalf2"]), 200);
  const denied = {...second, lastEditorUid: "doctor", lastEditorAccessLevel: 10, revision: 3,
    lastEventId: "half-pulse-denied", cellAccessLevels: {pulse: 10, pulseHalf2: 10}};
  check(await clinicalCommit("doctor", "half-pulse", "half-pulse-denied", denied, "pulseHalf2", ["pulseHalf2"]), 403);
  const allowed = {...denied, lastEventId: "half-pulse-allowed", cellAccessLevels: {pulse: 10, pulseHalf2: 20}};
  check(await clinicalCommit("doctor", "half-pulse", "half-pulse-allowed", allowed, "pulse", ["pulse"]), 200);

  const start = Date.now() - 3600000;
  await expectStatus("organizations/OrgOne01/clinicalData/v1/chartStarts/half-hour-woman", "owner", 200, "PATCH",
    {startMillis: start, womanDocumentId: "half-hour-woman", sessionId: "admission"});
  await expectStatus("organizations/OrgOne01/women/half-hour-woman", "owner", 200, "PATCH", {archived: false});
  const levels = {};
  let revision = 0;
  for (const key of ["amnioticFluidHalf2", "mouldingHalf2", "cervicalDilationHalf2", "descentHalf2", "pulseHalf2", "systolicHalf2", "diastolicHalf2", "temperatureHalf2"]) {
    levels[key] = 10;
    const state = {...first, revision: ++revision, womanDocumentId: "half-hour-woman", lastEventId: `event-${key}`, cellAccessLevels: {...levels}};
    check(await clinicalCommit("doctor", "timeline-half-hour-woman-0", `event-${key}`, state, key, [key], false,
      {chartStartMillis: start, chartColumnIndex: 0, observationTimeMillis: start + 1800000}), 200);
  }
});


test("partograph-specific obstetric and gestational header fields use the shared encrypted header history", async () => {
  const check = (result, status) => assert.equal(result.status, status, result.body);
  const fields = ["gravida", "livingChildren", "gestationalWeeks", "gestationalDays"];
  const initial = {lastEditorUid: "doctor", lastEditorAccessLevel: 10, highestEditorAccessLevel: 10,
    highestEditorUids: ["doctor"], revision: 1, confirmed: false, lastEventId: "partograph-header-initial",
    cellAccessLevels: Object.fromEntries(fields.map(key => [key, 10]))};
  check(await clinicalCommit("doctor", "header-partograph-specific", "partograph-header-initial", initial, "partographMark", fields), 200);
  const rupture = {...initial, revision: 2, lastEventId: "partograph-header-rupture", cellAccessLevels: {...initial.cellAccessLevels, rupturedHours: 10}};
  check(await clinicalCommit("doctor", "header-partograph-specific", "partograph-header-rupture", rupture, "rupturedHours", ["rupturedHours"]), 200);
  const clinical = {...rupture, revision: 3, lastEventId: "partograph-header-reject-clinical", cellAccessLevels: {...rupture.cellAccessLevels, pulseHalf2: 10}};
  check(await clinicalCommit("doctor", "header-partograph-specific", "partograph-header-reject-clinical", clinical, "pulseHalf2", ["pulseHalf2"]), 403);
});

test("column sign-offs require strictly higher frozen signer access and preserve history", async () => {
  for (const [uid, accessLevel] of [["sign10", 10], ["sign20", 20], ["sign30", 30]]) {
    await expectStatus(`organizations/OrgOne01/members/${uid}`, "owner", 200, "PATCH", {
      userId: uid, name: uid, role: "nurse", status: "active", accessLevel, joinedAt: new Date()
    });
  }
  const commit = (uid, event, state, accessLevel) => clinicalCommit(uid, "signoff-hierarchy", event,
    state, "initials", [], true, {accessLevel});
  const check = (result, status) => assert.equal(result.status, status, result.body);
  const initial = {lastEditorUid: "sign10", lastEditorAccessLevel: 10, highestEditorAccessLevel: 10,
    highestEditorUids: ["sign10"], revision: 1, confirmed: false, lastEventId: "signoff-initial",
    cellAccessLevels: {pulse: 10}};
  check(await clinicalCommit("sign10", "signoff-hierarchy", "signoff-initial", initial, "pulse", ["pulse"]), 200);
  const first = {...initial, revision: 2, confirmed: true, confirmedBy: "sign20", confirmationEventId: "signoff-first"};
  check(await commit("sign20", "signoff-first", first, 20), 200);
  const attempt = (uid, event) => ({...first, revision: 3, confirmedBy: uid, confirmationEventId: event});
  check(await commit("sign20", "signoff-equal", attempt("sign20", "signoff-equal"), 20), 403);
  check(await commit("sign10", "signoff-lower", attempt("sign10", "signoff-lower"), 10), 403);
  check(await commit("sign10", "signoff-forged", attempt("sign10", "signoff-forged"), 99), 403);
  // A later membership promotion must not change the threshold frozen by the earlier sign-off.
  await expectStatus("organizations/OrgOne01/members/sign20", "owner", 200, "PATCH", {
    userId: "sign20", name: "sign20", role: "nurse", status: "active", accessLevel: 99, joinedAt: new Date()
  });
  const second = attempt("sign30", "signoff-second");
  check(await commit("sign30", "signoff-second", second, 30), 200);
  check(await commit("sign30", "signoff-second-equal", {...second, revision: 4, confirmationEventId: "signoff-second-equal"}, 30), 403);
  await expectStatus("organizations/OrgOne01/clinicalData/v1/lcgObservations/signoff-first", token("sign10"), 200);
  await expectStatus("organizations/OrgOne01/clinicalData/v1/lcgObservations/signoff-first", token("sign30"), 403, "DELETE");
  // Recording a stage transition on a signed column does not overwrite its signer.
  check(await clinicalCommit("sign10", "signoff-hierarchy", "signoff-birth", {...second, revision: 4,
    lastEditorUid: "sign10", lastEditorAccessLevel: 10, lastEventId: "signoff-birth",
    cellAccessLevels: {...second.cellAccessLevels, birthConfirmed: 10}}, "birthConfirmed", ["birthConfirmed"]), 200);
  const response = await request(`organizations/OrgOne01/clinicalData/v1/lcgColumns/${physicalColumn("signoff-hierarchy")}`, token("sign10"));
  const fields = (await response.json()).fields;
  assert.equal(fields.confirmedBy.stringValue, "sign30");
  assert.equal(fields.confirmationEventId.stringValue, "signoff-second");
});

// Model the SDK's atomic writes, including server timestamps, without production credentials.
async function atomicCommit(uid, entries) {
  await ensurePrivacyFixture(token(uid));
  const response = await fetch(`${base}:commit`, {method: "POST", headers: {
    "Content-Type": "application/json", Authorization: `Bearer ${token(uid)}`
  }, body: JSON.stringify({writes: entries.map(({path, data, timestamps = [], patch = false}) => ({
    update: {name: `projects/${project}/databases/(default)/documents/${path}`,
      fields: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, field(v)]))},
    ...(patch ? {updateMask: {fieldPaths: Object.keys(data)}} : {}),
    ...(timestamps.length ? {updateTransforms: timestamps.map(fieldPath => ({fieldPath, setToServerValue: "REQUEST_TIME"}))} : {})
  }))})});
  return {status: response.status, body: await response.text()};
}

test("sign-off metadata cannot reference an unrelated or reused event without an atomic signature", async () => {
  const initial = {lastEditorUid: "doctor", lastEditorAccessLevel: 10, highestEditorAccessLevel: 10,
    highestEditorUids: ["doctor"], revision: 1, confirmed: false, lastEventId: "hardening-sign-cell", cellAccessLevels: {pulse: 10}};
  let result = await clinicalCommit("doctor", "hardening-sign-column", "hardening-sign-cell", initial, "pulse", ["pulse"]);
  assert.equal(result.status, 200, result.body);
  result = await atomicCommit("doctor", [{path: `organizations/OrgOne01/clinicalData/v1/lcgColumns/${physicalColumn("hardening-sign-column")}`,
    data: {confirmed: true, confirmedBy: "doctor", confirmationEventId: "hardening-sign-cell", revision: 2},
    timestamps: ["confirmedAt"], patch: true}]);
  assert.equal(result.status, 403, result.body);
});

test("tied editor updates cannot inject another UID into authorization metadata", async () => {
  const state = {lastEditorUid: "doctor", lastEditorAccessLevel: 10, highestEditorAccessLevel: 10,
    highestEditorUids: ["doctor"], revision: 1, confirmed: false, lastEventId: "hardening-uid-cell", cellAccessLevels: {pulse: 10}};
  let result = await clinicalCommit("doctor", "hardening-uid-column", "hardening-uid-cell", state, "pulse", ["pulse"]);
  assert.equal(result.status, 200, result.body);
  result = await clinicalCommit("doctor", "hardening-uid-column", "hardening-uid-forgery", {...state,
    revision: 2, lastEventId: "hardening-uid-forgery", highestEditorUids: ["doctor", "stranger"]}, "pulse", ["pulse"]);
  assert.equal(result.status, 403, result.body);
  result = await clinicalCommit("nurse", "hardening-uid-column", "hardening-uid-legitimate", {...state,
    revision: 2, lastEditorUid: "nurse", lastEventId: "hardening-uid-legitimate",
    highestEditorUids: ["doctor", "nurse"]}, "pulse", ["pulse"]);
  assert.equal(result.status, 200, result.body);
});

test("key envelopes require an active recipient and protect private material", async () => {
  await expectStatus("organizations/OrgOne01/keyMetadata/current", "owner", 200, "PATCH", {
    currentKeyVersion: 1, algorithm: "AES-256-GCM", updatedAt: new Date(), updatedBy: "admin"
  });
  const grant = uid => atomicCommit("admin", [{path: `organizations/OrgOne01/keyEnvelopes/${uid}`,
    data: {userId: uid, keyVersion: 1, wrappedKey: "YWJj", algorithm: "RSA-OAEP-3072-SHA256", grantedBy: "admin"}, timestamps: ["createdAt"]}]);
  for (const uid of ["stranger", "inactive"]) {
    const result = await grant(uid);
    assert.equal(result.status, 403, result.body);
  }
  const result = await grant("nurse");
  assert.equal(result.status, 200, result.body);
  await expectStatus("organizations/OrgOne01/keyEnvelopes/nurse", token("nurse"), 200);
  await expectStatus("organizations/OrgOne01/keyEnvelopes/nurse", token("doctor"), 403);
  await expectStatus("organizations/OrgOne01/keyEnvelopes/nurse", token("admin"), 200);
});

test("chart metadata rejects negative time and oversized session identifiers", async () => {
  const path = "organizations/OrgOne01/clinicalData/v1/chartStarts/hardening-time-woman";
  await expectStatus("organizations/OrgOne01/women/hardening-time-woman", "owner", 200, "PATCH", {ciphertext: "YWJj"});
  for (const data of [
    {startMillis: -1, womanDocumentId: "hardening-time-woman", sessionId: "admission"},
    {startMillis: Date.now() - 60000, womanDocumentId: "hardening-time-woman", sessionId: "x".repeat(10000)}
  ]) {
    const result = await atomicCommit("doctor", [{path, data}]);
    assert.equal(result.status, 403, result.body);
  }
  const result = await atomicCommit("doctor", [{path, data: {
    startMillis: Date.now() - 60000, womanDocumentId: "hardening-time-woman", sessionId: "valid-admission"
  }}]);
  assert.equal(result.status, 200, result.body);
});

test("joining, approval, key setup, rotation and own-user collection-group queries remain functional", async () => {
  const org = "FlowOrg1", root = `organizations/${org}`, applicant = "flowApplicant";
  const profile = uid => ({userId: uid, publicKeyJwk: "{}", publicKeyFingerprint: "A".repeat(43), keyAlgorithm: "RSA-OAEP-3072-SHA256"});
  const backup = uid => ({userId: uid, ciphertext: "A".repeat(32), iv: "A".repeat(16), salt: "A".repeat(22) + "==",
    kdf: "PBKDF2-SHA256", kdfIterations: 600000, keyAlgorithm: "RSA-OAEP-3072-SHA256"});
  const envelope = (uid, version) => ({userId: uid, keyVersion: version, wrappedKey: "YWJj", algorithm: "RSA-OAEP-3072-SHA256", grantedBy: "admin"});
  const check = (result, expected) => assert.equal(result.status, expected, result.body);
  await expectStatus(root, "owner", 200, "PATCH", {name: "Workflow regression"});
  await expectStatus(`${root}/members/admin`, "owner", 200, "PATCH", {
    userId: "admin", name: "Admin", role: "admin", status: "active", accessLevel: 10, joinedAt: new Date()
  });
  check(await atomicCommit("admin", [
    {path: `${root}/keyProfiles/admin`, data: profile("admin"), timestamps: ["createdAt"]},
    {path: `${root}/keyBackups/admin`, data: backup("admin"), timestamps: ["updatedAt"]},
    {path: `${root}/keyMetadata/current`, data: {currentKeyVersion: 1, algorithm: "AES-256-GCM", updatedBy: "admin"}, timestamps: ["updatedAt"]},
    {path: `${root}/keyEnvelopes/admin`, data: envelope("admin", 1), timestamps: ["createdAt"]}
  ]), 200);
  check(await atomicCommit(applicant, [
    {path: `${root}/accessRequests/${applicant}`, data: {userId: applicant, userName: "Applicant", userEmail: `${applicant}@example.test`, requestedRole: "nurse", status: "pending"}, timestamps: ["createdAt"]},
    {path: `${root}/keyProfiles/${applicant}`, data: profile(applicant), timestamps: ["createdAt"]},
    {path: `${root}/keyBackups/${applicant}`, data: backup(applicant), timestamps: ["updatedAt"]}
  ]), 200);
  await expectStatus(`${root}/keyBackups/${applicant}`, token(applicant), 200);
  for (const uid of ["admin", "doctor", "stranger"]) {
    await expectStatus(`${root}/keyBackups/${applicant}`, token(uid), 403);
  }
  check(await atomicCommit(applicant, [{path: `${root}/accessRequests/${applicant}`,
    data: {status: "approved", approvedRole: "admin", reviewedBy: applicant}, timestamps: ["reviewedAt"], patch: true}]), 403);
  check(await atomicCommit(applicant, [{path: `${root}/keyProfiles/${applicant}`,
    data: {...profile(applicant), publicKeyFingerprint: "B".repeat(43)}, timestamps: ["createdAt"]}]), 403);
  check(await atomicCommit("admin", [
    {path: `${root}/accessRequests/${applicant}`, data: {status: "approved", approvedRole: "nurse", reviewedBy: "admin"}, timestamps: ["reviewedAt"], patch: true},
    {path: `${root}/members/${applicant}`, data: {userId: applicant, name: "Applicant", role: "nurse", status: "active", accessLevel: 10}, timestamps: ["joinedAt"]},
    {path: `${root}/keyEnvelopes/${applicant}`, data: envelope(applicant, 1), timestamps: ["createdAt"]}
  ]), 200);
  const queryOwn = async (collectionId, ownOnly) => {
    const response = await fetch(`${base}:runQuery`, {method: "POST", headers: {
      "Content-Type": "application/json", Authorization: `Bearer ${token(applicant)}`
    }, body: JSON.stringify({structuredQuery: {from: [{collectionId, allDescendants: true}],
      ...(ownOnly ? {where: {fieldFilter: {field: {fieldPath: "userId"}, op: "EQUAL", value: {stringValue: applicant}}}} : {})}})});
    assert.equal(response.status, ownOnly ? 200 : 403, await response.text());
  };
  for (const collection of ["members", "accessRequests"]) { await queryOwn(collection, true); await queryOwn(collection, false); }
  await expectStatus(`${root}/members/departing`, "owner", 200, "PATCH", {
    userId: "departing", name: "Departing", role: "nurse", status: "active", accessLevel: 10, joinedAt: new Date()
  });
  await completeRotation(org, "departing", ["admin", applicant], 1);
  check(await atomicCommit("admin", [{path: `${root}/members/${applicant}`, data: {status: "inactive"}, patch: true}]), 403);
  await completeRotation(org, applicant, ["admin"], 2);
  await expectStatus(`${root}/keyBackups/${applicant}`, token(applicant), 200);
  await expectStatus(`${root}/keyEnvelopes/${applicant}`, token(applicant), 403);
  check(await atomicCommit("admin", [
    {path: `${root}/keyMetadata/current`, data: {currentKeyVersion: 3, updatedBy: "admin"}, timestamps: ["updatedAt"], patch: true},
    {path: `${root}/keyEnvelopes/${applicant}`, data: envelope(applicant, 3), timestamps: ["createdAt"]}
  ]), 403);
  check(await atomicCommit("admin", [{path: `${root}/members/${applicant}`, data: {status: "active"}, patch: true}]), 200);
  check(await atomicCommit("admin", [{path: `${root}/keyEnvelopes/${applicant}`, data: envelope(applicant, 3), timestamps: ["createdAt"]}]), 200);
  await expectStatus(`${root}/keyEnvelopes/${applicant}`, token(applicant), 200);
});

test("malformed membership cannot authorize clinical access or key delivery", async () => {
  for (const [uid, override] of [["mismatched", {userId: "someone-else"}], ["badlevel", {accessLevel: 100}], ["badrole", {role: "guest"}]]) {
    await expectStatus(`organizations/OrgOne01/members/${uid}`, "owner", 200, "PATCH", {
      userId: uid, name: uid, role: "nurse", status: "active", accessLevel: 10, joinedAt: new Date(), ...override
    });
    await expectStatus("organizations/OrgOne01/women", token(uid), 403);
    const result = await atomicCommit("admin", [{path: `organizations/OrgOne01/keyEnvelopes/${uid}`,
      data: {userId: uid, keyVersion: 1, wrappedKey: "YWJj", algorithm: "RSA-OAEP-3072-SHA256", grantedBy: "admin"}, timestamps: ["createdAt"]}]);
    assert.equal(result.status, 403, result.body);
  }
});

test("cell event and column validators reject identity, schema and type corruption", async () => {
  const original = {lastEditorUid: "doctor", lastEditorAccessLevel: 10, highestEditorAccessLevel: 10,
    highestEditorUids: ["doctor"], revision: 1, confirmed: false, lastEventId: "hardening-shape", cellAccessLevels: {pulse: 10}};
  const cases = [
    {state: {...original, extra: "unexpected"}},
    {state: {...original, revision: 1.5}},
    {state: {...original, cellAccessLevels: {pulse: "10"}}},
    {state: {...original, highestEditorUids: ["doctor", "stranger"]}},
    {event: {createdBy: "nurse"}}, {event: {accessLevel: 99}},
    {event: {iv: "invalid"}}, {event: {ciphertext: "X".repeat(16385)}},
    {event: {observationTimeMillis: -1}}, {event: {unexpected: "value"}}
  ];
  for (const [index, attack] of cases.entries()) {
    const eventId = `hardening-shape-${index}`;
    const result = await clinicalCommit("doctor", eventId, eventId,
      {...original, ...attack.state, lastEventId: eventId}, "pulse", ["pulse"], false, attack.event || {});
    assert.equal(result.status, 403, `Case ${index}: ${result.body}`);
  }
});


test("clinical events require atomic, matching current-cell projections", async () => {
  const state = {lastEditorUid: "doctor", lastEditorAccessLevel: 10, highestEditorAccessLevel: 10,
    highestEditorUids: ["doctor"], revision: 1, confirmed: false, lastEventId: "projection-event", cellAccessLevels: {pulse: 10}};
  const commit = (extra = {}, options = {}) => clinicalCommit("doctor", "projection-column", "projection-event", state, "pulse", ["pulse"], false, extra, options);
  let result = await commit({}, {omitProjections: true});
  assert.equal(result.status, 403, result.body);
  result = await commit({}, {projectionKeys: ["pulse", "temperature"]});
  assert.equal(result.status, 403, result.body);
  result = await commit({}, {projectionEvent: {ciphertext: "Zm9yZ2Vk"}});
  assert.equal(result.status, 403, result.body);
  result = await commit({schemaVersion: 1});
  assert.equal(result.status, 403, result.body);
  result = await commit();
  assert.equal(result.status, 200, result.body);
  for (const uid of ["stranger", "inactive"]) {
    await expectStatus(`organizations/OrgOne01/clinicalData/v1/lcgCells/${physicalColumn("projection-column")}~pulse`, token(uid), 403);
  }
  await expectStatus(`organizations/OrgOne01/clinicalData/v1/lcgCells/${physicalColumn("projection-column")}~pulse`, token("doctor"), 200);
  const replay = await atomicCommit("doctor", [{path: "organizations/OrgOne01/clinicalData/v1/lcgCells/projection-column~temperature",
    data: {sourceEventId: "projection-event", columnId: "projection-column", womanDocumentId: "record-a", cellKey: "temperature", event: {}}}]);
  assert.equal(replay.status, 403, replay.body);
});

test("first-half aliases cannot bypass a senior clinician's cell protection", async () => {
  const initial = {lastEditorUid: "senior", lastEditorAccessLevel: 20, highestEditorAccessLevel: 20,
    highestEditorUids: ["senior"], revision: 1, confirmed: false, lastEventId: "alias-first", cellAccessLevels: {fetalHeartRate: 20}};
  let result = await clinicalCommit("senior", "alias-column", "alias-first", initial, "fetalHeartRate", ["fetalHeartRate"]);
  assert.equal(result.status, 200, result.body);
  const lower = {...initial, lastEditorUid: "doctor", lastEditorAccessLevel: 10, revision: 2,
    lastEventId: "alias-bypass", cellAccessLevels: {fetalHeartRate: 20, fetalHeartRateHalf1: 10}};
  result = await clinicalCommit("doctor", "alias-column", "alias-bypass", lower, "fetalHeartRateHalf1", ["fetalHeartRateHalf1"]);
  assert.equal(result.status, 403, result.body);
});

test("rotation rejects prior-version writes and keeps history bridges immutable", async () => {
  const root = "organizations/FlowOrg1";
  const ward = {ciphertext: "YWJj", iv: "A".repeat(16), algorithm: "AES-GCM", schemaVersion: 1, createdBy: "admin"};
  let result = await atomicCommit("admin", [{path: `${root}/wards/current-key-check`, data: {...ward, keyVersion: 1}, timestamps: ["createdAt"]}]);
  assert.equal(result.status, 403, result.body);
  result = await atomicCommit("admin", [{path: `${root}/wards/current-key-check`, data: {...ward, keyVersion: 3}, timestamps: ["createdAt"]}]);
  assert.equal(result.status, 200, result.body);
  result = await atomicCommit("admin", [{path: `${root}/keyHistory/1`, data: {ciphertext: "YWJj"}, patch: true}]);
  assert.equal(result.status, 403, result.body);
  await expectStatus(`${root}/keyHistory/1`, token("stranger"), 403);
});


test("a new column cannot impersonate another column's index under an arbitrary document ID", async () => {
  const state = {lastEditorUid: "doctor", lastEditorAccessLevel: 10, highestEditorAccessLevel: 10,
    highestEditorUids: ["doctor"], revision: 1, confirmed: false, lastEventId: "duplicate-index-event", cellAccessLevels: {pulse: 10}};
  const result = await clinicalCommit("doctor", "duplicate-index", "duplicate-index-event", state, "pulse", ["pulse"], false, {},
    {forceColumnId: "attacker-chosen-duplicate"});
  assert.equal(result.status, 403, result.body);
});

// Rotation is staged in bounded online transactions and made visible by one final version switch.
function rotationFixture(org, removed, recipients, version = 1) {
  const id = require('node:crypto').randomBytes(16).toString('base64url');
  const root = `organizations/${org}`, path = `${root}/keyRotations/${id}`;
  const operation = {adminId: 'admin', removedUserId: removed, baseVersion: version, targetVersion: version + 1,
    phase: 'preparing', recipients, recipientCount: recipients.length, nextIndex: 0,
    ciphertext: 'A'.repeat(64), iv: 'A'.repeat(16)};
  const status = {active: true, rotationId: id, adminId: 'admin', completed: 0, total: recipients.length};
  return {id, root, path, operation, status};
}
function rotationBegin(f, uid = 'admin') {
  return atomicCommit(uid, [
    {path: f.path, data: f.operation, timestamps: ['createdAt', 'updatedAt']},
    {path: `${f.root}/keyMaintenance/current`, data: f.status, timestamps: ['updatedAt']},
    ...(f.operation.removedUserId ? [{path: `${f.root}/members/${f.operation.removedUserId}`, data: {status: 'inactive'}, patch: true}] : [])
  ]);
}
function rotationPrepare(f, extra = {}) {
  return atomicCommit('admin', [{path: f.path, data: {phase: 'wrapping', ...extra}, patch: true, timestamps: ['updatedAt']}]);
}
function rotationStage(f, index, uid = 'admin', extra = {}) {
  const recipient = f.operation.recipients[index];
  return atomicCommit(uid, [
    {path: `${f.path}/grants/${recipient}`, data: {userId: recipient, keyVersion: f.operation.targetVersion,
      wrappedKey: 'YWJj', algorithm: 'RSA-OAEP-3072-SHA256', grantedBy: uid, index,
      proofCiphertext: 'A'.repeat(64), proofIv: 'A'.repeat(16), ...extra}, timestamps: ['createdAt']},
    {path: f.path, data: {nextIndex: index + 1}, patch: true, timestamps: ['updatedAt']},
    {path: `${f.root}/keyMaintenance/current`, data: {completed: index + 1}, patch: true, timestamps: ['updatedAt']}
  ]);
}
function rotationFinish(f) {
  return atomicCommit('admin', [
    {path: `${f.root}/keyMetadata/current`, data: {currentKeyVersion: f.operation.targetVersion, rotationId: f.id, updatedBy: 'admin'}, patch: true, timestamps: ['updatedAt']},
    {path: `${f.root}/keyHistory/${f.operation.baseVersion}`, data: {keyVersion: f.operation.baseVersion, wrappingVersion: f.operation.targetVersion,
      ciphertext: 'A'.repeat(64), iv: 'A'.repeat(16), createdBy: 'admin'}, timestamps: ['createdAt']},
    {path: f.path, data: {phase: 'completed'}, patch: true, timestamps: ['updatedAt']},
    {path: `${f.root}/keyMaintenance/current`, data: {active: false}, patch: true, timestamps: ['updatedAt']}
  ]);
}
async function completeRotation(org, removed, recipients, version) {
  const f = rotationFixture(org, removed, recipients, version);
  for (const result of [await rotationBegin(f), await rotationPrepare(f)]) assert.equal(result.status, 200, result.body);
  for (let i = 0; i < recipients.length; i++) {
    const result = await rotationStage(f, i); assert.equal(result.status, 200, result.body);
  }
  const result = await rotationFinish(f); assert.equal(result.status, 200, result.body);
  return f;
}

test('resumable rotation freezes membership and clinical data until every grant activates atomically', async () => {
  const org = 'Rotate01', root = `organizations/${org}`;
  await expectStatus(root, 'owner', 200, 'PATCH', {name: 'Rotation test'});
  for (const [uid, role] of [['admin', 'admin'], ['leaving', 'nurse'], ['remaining', 'doctor'], ['other', 'nurse']]) {
    await expectStatus(`${root}/members/${uid}`, 'owner', 200, 'PATCH', {
      userId: uid, name: uid, role, status: 'active', accessLevel: 60, joinedAt: new Date()
    });
  }
  await expectStatus(`${root}/keyMetadata/current`, 'owner', 200, 'PATCH', {currentKeyVersion: 1,
    algorithm: 'AES-256-GCM', updatedBy: 'admin', updatedAt: new Date()});
  await expectStatus(`${root}/women/existing`, 'owner', 200, 'PATCH', {ciphertext: 'YWJj'});
  const f = rotationFixture(org, 'leaving', ['admin', 'other', 'remaining']);
  const check = (r, status) => assert.equal(r.status, status, r.body);
  check(await rotationBegin(f, 'remaining'), 403);
  check(await rotationBegin(f), 200);
  await expectStatus(`${root}/keyMaintenance/current`, token('remaining'), 200);
  await expectStatus(`${root}/keyMaintenance/current`, token('leaving'), 403);
  await expectStatus(`${root}/women/existing`, token('remaining'), 403);
  await expectStatus(f.path, token('remaining'), 403);
  check(await atomicCommit('admin', [{path: `${root}/members/other`, data: {accessLevel: 61}, patch: true}]), 403);
  check(await atomicCommit('admin', [{path: `${root}/members/leaving`, data: {status: 'active'}, patch: true}]), 403);
  check(await atomicCommit('admin', [{path: `${root}/keyMaintenance/current`, data: {active: false}, patch: true, timestamps: ['updatedAt']}]), 403);
  check(await rotationFinish(f), 403);
  check(await rotationStage(f, 0), 403); // Manifest must be reconciled after membership is frozen.
  check(await rotationPrepare(f), 200);
  check(await rotationStage(f, 1), 403); // Cannot skip a recipient.
  check(await rotationStage(f, 0, 'remaining'), 403);
  check(await rotationStage(f, 0, 'admin', {userId: 'leaving'}), 403);
  check(await rotationStage(f, 0), 200);
  check(await rotationStage(f, 0), 403); // Committed slots cannot be overwritten.
  check(await rotationFinish(f), 403);
  check(await atomicCommit('admin', [{path: f.path, data: {nextIndex: 3}, patch: true, timestamps: ['updatedAt']}]), 403);
  // A later authenticated session reads the durable journal and resumes with the next slot.
  await expectStatus(f.path, token('admin'), 200);
  check(await rotationStage(f, 1), 200);
  await expectStatus(`${f.path}/grants/other`, token('other'), 403); // Not exposed before activation.
  check(await rotationStage(f, 2), 200);
  check(await rotationFinish(f), 200);
  for (const uid of ['admin', 'other', 'remaining']) await expectStatus(`${f.path}/grants/${uid}`, token(uid), 200);
  await expectStatus(`${f.path}/grants/remaining`, token('other'), 403);
  await expectStatus(`${f.path}/grants/remaining`, token('leaving'), 403);
  await expectStatus(`${root}/women/existing`, token('remaining'), 200);
  await expectStatus(`${root}/members/newcomer`, 'owner', 200, 'PATCH', {
    userId: 'newcomer', name: 'New member', role: 'nurse', status: 'active', accessLevel: 10, joinedAt: new Date()
  });
  await expectStatus(`${f.path}/grants/newcomer`, token('newcomer'), 404);
  check(await rotationFinish(f), 403); // No replay of a completed operation.
});

test('recipient pins are admin-only authenticated ciphertext and reject extra or secret fields', async () => {
  const path = 'organizations/Rotate01/rotationRecipients/remaining';
  const pin = {ciphertext: 'A'.repeat(64), iv: 'A'.repeat(16), adminId: 'admin', schemaVersion: 1};
  let r = await atomicCommit('remaining', [{path, data: pin, timestamps: ['updatedAt']}]);
  assert.equal(r.status, 403, r.body);
  r = await atomicCommit('admin', [{path, data: {...pin, plaintextKey: 'secret'}, timestamps: ['updatedAt']}]);
  assert.equal(r.status, 403, r.body);
  r = await atomicCommit('admin', [{path, data: pin, timestamps: ['updatedAt']}]);
  assert.equal(r.status, 200, r.body);
  await expectStatus(path, token('admin'), 200);
  await expectStatus(path, token('remaining'), 403);
  await expectStatus(path, token('leaving'), 403);
});

test('manual rotation is admin-only, retains all memberships and uses the complete activation protocol', async () => {
  const org = 'Manual01', root = `organizations/${org}`;
  await expectStatus(root, 'owner', 200, 'PATCH', {name: 'Manual key rotation'});
  for (const [uid, role] of [['admin', 'admin'], ['manualDoctor', 'doctor'], ['manualNurse', 'nurse']]) {
    await expectStatus(`${root}/members/${uid}`, 'owner', 200, 'PATCH', {
      userId: uid, name: uid, role, status: 'active', accessLevel: 60, joinedAt: new Date()
    });
  }
  await expectStatus(`${root}/keyMetadata/current`, 'owner', 200, 'PATCH', {
    currentKeyVersion: 1, algorithm: 'AES-256-GCM', updatedBy: 'admin', updatedAt: new Date()
  });
  const ids = ['admin', 'manualDoctor', 'manualNurse'];
  const f = rotationFixture(org, '', ids);
  const check = (r, status) => assert.equal(r.status, status, r.body);
  check(await rotationBegin(f, 'manualDoctor'), 403);
  // A manual operation cannot be used to deactivate a member on the side.
  const invalid = await atomicCommit('admin', [
    {path: f.path, data: f.operation, timestamps: ['createdAt', 'updatedAt']},
    {path: `${root}/keyMaintenance/current`, data: f.status, timestamps: ['updatedAt']},
    {path: `${root}/members/manualDoctor`, data: {status: 'inactive'}, patch: true}
  ]);
  check(invalid, 403);
  check(await rotationBegin(f), 200);
  check(await rotationBegin(rotationFixture(org, '', ids)), 403);
  check(await rotationPrepare(f), 200);
  check(await rotationFinish(f), 403);
  for (let i = 0; i < ids.length; i++) check(await rotationStage(f, i), 200);
  check(await rotationFinish(f), 200);
  for (const uid of ids) {
    const membership = await (await request(`${root}/members/${uid}`, token('admin'))).json();
    assert.equal(membership.fields.status.stringValue, 'active');
    await expectStatus(`${f.path}/grants/${uid}`, token(uid), 200);
  }
  await completeRotation(org, '', ids, 2); // Can be manually triggered again after completion.
  const metadata = await (await request(`${root}/keyMetadata/current`, token('admin'))).json();
  assert.equal(metadata.fields.currentKeyVersion.integerValue, '3');
});

test('pending requests can be cancelled only by their owner and reapplied with the existing recovery identity', async () => {
  const org = 'Cancel01', root = `organizations/${org}`, uid = 'cancelApplicant';
  const check = (r, status) => assert.equal(r.status, status, r.body);
  await expectStatus(root, 'owner', 200, 'PATCH', {name: 'Cancellation tests'});
  await expectStatus(`${root}/members/admin`, 'owner', 200, 'PATCH', {
    userId: 'admin', name: 'Admin', role: 'admin', status: 'active', accessLevel: 10, joinedAt: new Date()
  });
  const requestData = userId => ({userId, userName: 'Applicant', userEmail: `${userId}@example.test`, requestedRole: 'nurse', status: 'pending'});
  async function createPending(userId) {
    await expectStatus(`${root}/accessRequests/${userId}`, token(userId), 404);
    check(await atomicCommit(userId, [
      {path: `${root}/accessRequests/${userId}`, data: requestData(userId), timestamps: ['createdAt']},
      {path: `${root}/keyProfiles/${userId}`, data: {userId, publicKeyJwk: '{}', publicKeyFingerprint: 'A'.repeat(43), keyAlgorithm: 'RSA-OAEP-3072-SHA256'}, timestamps: ['createdAt']},
      {path: `${root}/keyBackups/${userId}`, data: {userId, ciphertext: 'A'.repeat(32), iv: 'A'.repeat(16), salt: 'A'.repeat(22) + '==', kdf: 'PBKDF2-SHA256', kdfIterations: 600000, keyAlgorithm: 'RSA-OAEP-3072-SHA256'}, timestamps: ['updatedAt']}
    ]), 200);
  }
  const cancel = userId => atomicCommit(userId, [{path: `${root}/accessRequests/${userId}`, data: {status: 'cancelled'}, patch: true, timestamps: ['cancelledAt']}]);
  const approve = userId => atomicCommit('admin', [
    {path: `${root}/accessRequests/${userId}`, data: {status: 'approved', approvedRole: 'nurse', reviewedBy: 'admin'}, patch: true, timestamps: ['reviewedAt']},
    {path: `${root}/members/${userId}`, data: {userId, name: 'Applicant', role: 'nurse', status: 'active', accessLevel: 10}, timestamps: ['joinedAt']}
  ]);
  await createPending(uid);
  for (const caller of ['admin', 'stranger']) check(await atomicCommit(caller, [{path: `${root}/accessRequests/${uid}`, data: {status: 'cancelled'}, patch: true, timestamps: ['cancelledAt']}]), 403);
  check(await atomicCommit(uid, [{path: `${root}/accessRequests/${uid}`, data: {status: 'cancelled', requestedRole: 'doctor'}, patch: true, timestamps: ['cancelledAt']}]), 403);
  check(await cancel(uid), 200);
  check(await approve(uid), 403);
  await expectStatus(`${root}/members/${uid}`, 'owner', 404);
  const originalProfile = await (await request(`${root}/keyProfiles/${uid}`, token(uid))).json();
  check(await atomicCommit(uid, [{path: `${root}/accessRequests/${uid}`, data: requestData(uid), timestamps: ['createdAt']}]), 200);
  const retainedProfile = await (await request(`${root}/keyProfiles/${uid}`, token(uid))).json();
  assert.deepEqual(retainedProfile.fields, originalProfile.fields);
  check(await atomicCommit(uid, [{path: `${root}/keyProfiles/${uid}`, data: {publicKeyJwk: 'replacement'}, patch: true}]), 403);
  check(await approve(uid), 200);
  check(await cancel(uid), 403);
  // The state machine serializes cancellation and approval, regardless of which wins.
  await createPending('raceApplicant');
  const race = await Promise.all([cancel('raceApplicant'), approve('raceApplicant')]);
  assert.deepEqual(race.map(r => r.status).sort(), [200, 403]);
});

test('members can leave atomically, block future access and queue an admin-resumable key update', async () => {
  const org = 'Depart01', root = `organizations/${org}`;
  const check = (r, status) => assert.equal(r.status, status, r.body);
  await expectStatus(root, 'owner', 200, 'PATCH', {name: 'Departure tests'});
  for (const [uid, role] of [['admin', 'admin'], ['departing', 'doctor'], ['departing2', 'nurse'], ['staying', 'doctor']]) {
    await expectStatus(`${root}/members/${uid}`, 'owner', 200, 'PATCH', {userId: uid, name: uid, role, status: 'active', accessLevel: 60, joinedAt: new Date()});
  }
  await expectStatus(`${root}/keyMetadata/current`, 'owner', 200, 'PATCH', {currentKeyVersion: 1, algorithm: 'AES-256-GCM', updatedBy: 'admin', updatedAt: new Date()});
  await expectStatus(`${root}/women/example`, 'owner', 200, 'PATCH', {ciphertext: 'YWJj'});
  await expectStatus(`${root}/keyEnvelopes/departing`, 'owner', 200, 'PATCH', {userId: 'departing', keyVersion: 1, wrappedKey: 'YWJj'});
  const id = require('node:crypto').randomBytes(16).toString('base64url');
  const status = {active: true, awaitingAdmin: true, departedUserId: 'departing', rotationId: id, adminId: 'admin', completed: 0, total: 0};
  const memberLeave = (uid, extra = {}) => ({path: `${root}/members/${uid}`, data: {status: 'left', leftDuringRotationId: id, ...extra}, patch: true, timestamps: ['leftAt']});
  check(await atomicCommit('departing', [memberLeave('departing')]), 403);
  check(await atomicCommit('departing', [{path: `${root}/keyMaintenance/current`, data: status, timestamps: ['updatedAt']}]), 403);
  check(await atomicCommit('departing2', [memberLeave('departing'), {path: `${root}/keyMaintenance/current`, data: status, timestamps: ['updatedAt']}]), 403);
  check(await atomicCommit('admin', [memberLeave('admin'), {path: `${root}/keyMaintenance/current`, data: {...status, departedUserId: 'admin'}, timestamps: ['updatedAt']}]), 403);
  check(await atomicCommit('departing', [memberLeave('departing', {accessLevel: 99}), {path: `${root}/keyMaintenance/current`, data: status, timestamps: ['updatedAt']}]), 403);
  check(await atomicCommit('departing', [memberLeave('departing'), {path: `${root}/keyMaintenance/current`, data: {...status, adminId: 'departing2'}, timestamps: ['updatedAt']}]), 403);
  check(await atomicCommit('departing', [memberLeave('departing'), {path: `${root}/keyMaintenance/current`, data: status, timestamps: ['updatedAt']}]), 200);
  await expectStatus(`${root}/members/departing`, token('departing'), 200);
  await expectStatus(`${root}/women/example`, token('departing'), 403);
  await expectStatus(`${root}/keyEnvelopes/departing`, token('departing'), 403);
  await expectStatus(`${root}/women/example`, token('staying'), 403);
  await expectStatus(`${root}/keyMaintenance/current`, token('staying'), 200);
  check(await atomicCommit('admin', [{path: `${root}/members/departing`, data: {status: 'active'}, patch: true}]), 403);
  // A second member can also quit while the admin is offline, without exposing any new key.
  check(await atomicCommit('departing2', [memberLeave('departing2')]), 200);
  const f = rotationFixture(org, '', ['admin', 'staying']);
  f.id = id; f.path = `${root}/keyRotations/${id}`;
  f.status.rotationId = id;
  // Existing recipients can be verified while the queue is waiting for the admin.
  check(await atomicCommit('admin', [{path: `${root}/rotationRecipients/staying`, data: {ciphertext: 'A'.repeat(64), iv: 'A'.repeat(16), adminId: 'admin', schemaVersion: 1}, timestamps: ['updatedAt']}]), 200);
  check(await atomicCommit('admin', [
    {path: f.path, data: f.operation, timestamps: ['createdAt', 'updatedAt']},
    {path: `${root}/keyMaintenance/current`, data: {...f.status, awaitingAdmin: false}, timestamps: ['updatedAt']}
  ]), 200);
  check(await atomicCommit('staying', [memberLeave('staying')]), 403);
  check(await rotationPrepare(f), 200);
  check(await rotationStage(f, 0), 200);
  check(await rotationStage(f, 1), 200);
  check(await rotationFinish(f), 200);
  await expectStatus(`${root}/women/example`, token('staying'), 200);
  await expectStatus(`${f.path}/grants/staying`, token('staying'), 200);
  for (const uid of ['departing', 'departing2']) await expectStatus(`${f.path}/grants/${uid}`, token(uid), 403);
});

// Recovery replacement keeps the member UID, membership and clinical authorship intact.
function decodeFields(fields) {
  const decode = value => value.mapValue ? decodeFields(value.mapValue.fields) : value.timestampValue ? new Date(value.timestampValue)
    : value.integerValue ? Number(value.integerValue) : value.booleanValue !== undefined ? value.booleanValue : value.stringValue;
  return Object.fromEntries(Object.entries(fields).map(([key, value]) => [key, decode(value)]));
}
async function replacementFixture(suffix) {
  const org = "Recover1", uid = `recover-${suffix}`, root = `organizations/${org}`;
  const profile = {userId: uid, publicKeyJwk: "old-public-key", publicKeyFingerprint: "A".repeat(43), keyAlgorithm: "RSA-OAEP-3072-SHA256", createdAt: new Date()};
  const backup = {userId: uid, ciphertext: "YWJj", iv: "A".repeat(16), salt: "A".repeat(22) + "==", kdf: "PBKDF2-SHA256", kdfIterations: 600000, keyAlgorithm: "RSA-OAEP-3072-SHA256", updatedAt: new Date()};
  await expectStatus(root, "owner", 200, "PATCH", {name: "Recovery tests"});
  for (const [id, role] of [["recovery-admin", "admin"], [uid, "doctor"]]) {
    await expectStatus(`${root}/members/${id}`, "owner", 200, "PATCH", {userId: id, name: id, role, status: "active", accessLevel: 30, joinedAt: new Date()});
  }
  await expectStatus(`${root}/keyMetadata/current`, "owner", 200, "PATCH", {currentKeyVersion: 2, algorithm: "AES-256-GCM", updatedBy: "recovery-admin", updatedAt: new Date()});
  await expectStatus(`${root}/keyProfiles/${uid}`, "owner", 200, "PATCH", profile);
  await expectStatus(`${root}/keyBackups/${uid}`, "owner", 200, "PATCH", backup);
  await expectStatus(`${root}/keyEnvelopes/${uid}`, "owner", 200, "PATCH", {userId: uid, keyVersion: 2, wrappedKey: "b2xk", algorithm: "RSA-OAEP-3072-SHA256", grantedBy: "recovery-admin", createdAt: new Date()});
  const data = {userId: uid, requestId: "R".repeat(22), previousFingerprint: profile.publicKeyFingerprint,
    status: "pending", profile: {...profile, publicKeyJwk: "replacement-public-key", publicKeyFingerprint: "B".repeat(43)}, backup: {...backup, ciphertext: "bmV3"}};
  delete data.profile.createdAt; delete data.backup.updatedAt;
  const entry = {path: `${root}/keyReplacementRequests/${uid}`, data, timestamps: ["createdAt", "profile.createdAt", "backup.updatedAt"]};
  const submit = await atomicCommit(uid, [entry]);
  assert.equal(submit.status, 200, submit.body);
  const response = await request(entry.path, token(uid));
  const pending = decodeFields((await response.json()).fields);
  const entries = [
    {path: `${root}/keyProfiles/${uid}`, data: pending.profile},
    {path: `${root}/keyBackups/${uid}`, data: pending.backup},
    {path: `${root}/keyEnvelopes/${uid}`, data: {userId: uid, keyVersion: 2, wrappedKey: "bmV3", algorithm: "RSA-OAEP-3072-SHA256", grantedBy: "recovery-admin"}, timestamps: ["createdAt"]},
    {path: `${root}/rotationRecipients/${uid}`, data: {ciphertext: "A".repeat(24), iv: "A".repeat(16), adminId: "recovery-admin", schemaVersion: 1}, timestamps: ["updatedAt"]},
    {path: entry.path, data: {status: "approved", resolvedBy: "recovery-admin"}, patch: true, timestamps: ["resolvedAt"]}
  ];
  return {root, uid, entry, pending, entries};
}

test("key replacement: atomic admin approval regrants the same key version and preserves membership", async () => {
  const {root, uid, entries} = await replacementFixture("approve");
  const before = await (await request(`${root}/members/${uid}`, token(uid))).json();
  const result = await atomicCommit("recovery-admin", entries);
  assert.equal(result.status, 200, result.body);
  const after = await (await request(`${root}/members/${uid}`, token(uid))).json();
  assert.deepEqual(after.fields, before.fields);
  const profile = decodeFields((await (await request(`${root}/keyProfiles/${uid}`, token(uid))).json()).fields);
  assert.equal(profile.userId, uid); assert.equal(profile.publicKeyFingerprint, "B".repeat(43));
  const replay = await atomicCommit("recovery-admin", entries);
  assert.equal(replay.status, 403, replay.body);
});

test("key replacement: rejects self approval, unrelated users, partial commits and mismatched keys", async () => {
  const {root, uid, entries, entry, pending} = await replacementFixture("attacks");
  for (const actor of [uid, "doctor", "inactiveAdmin", "stranger"]) {
    const result = await atomicCommit(actor, entries);
    assert.equal(result.status, 403, result.body);
  }
  for (const index of [0, 1, 2, 3, 4]) {
    const result = await atomicCommit("recovery-admin", entries.filter((_, i) => i !== index));
    assert.equal(result.status, 403, `missing companion ${index}: ${result.body}`);
  }
  const mismatched = entries.map((value, i) => i === 0 ? {...value, data: {...value.data, publicKeyFingerprint: "C".repeat(43)}} : value);
  const mismatch = await atomicCommit("recovery-admin", mismatched);
  assert.equal(mismatch.status, 403, mismatch.body);
  for (const actor of [undefined, token("stranger"), token("doctor"), token(uid, false)]) await expectStatus(entry.path, actor, 403);
  await expectStatus(entry.path, token("recovery-admin"), 200);
  await expectStatus(`${root}/keyReplacementRequests`, token(uid), 403);
  await expectStatus(`${root}/keyReplacementRequests`, token("recovery-admin"), 200);
  const tamper = await atomicCommit(uid, [{...entry, data: {...entry.data, userId: "stranger"}}]);
  assert.equal(tamper.status, 403, tamper.body);
  const pendingOverwrite = await atomicCommit(uid, [entry]);
  assert.equal(pendingOverwrite.status, 403, pendingOverwrite.body);
  const resolution = await atomicCommit("recovery-admin", [{path: entry.path, data: {status: "rejected", resolvedBy: "recovery-admin"}, timestamps: ["resolvedAt"], patch: true}]);
  assert.equal(resolution.status, 200, resolution.body);
  const staleApproval = await atomicCommit("recovery-admin", entries);
  assert.equal(staleApproval.status, 403, staleApproval.body);
  const fresh = await atomicCommit(uid, [{...entry, data: {...entry.data, requestId: "S".repeat(22)}}]);
  assert.equal(fresh.status, 200, fresh.body);
});

test("key replacement: cancellation preserves registered key and blocks approval", async () => {
  const {root, uid, entry, entries} = await replacementFixture("cancel");
  const cancel = await atomicCommit(uid, [{path: entry.path, data: {status: "cancelled", resolvedBy: uid}, timestamps: ["resolvedAt"], patch: true}]);
  assert.equal(cancel.status, 200, cancel.body);
  const approval = await atomicCommit("recovery-admin", entries);
  assert.equal(approval.status, 403, approval.body);
  const profile = decodeFields((await (await request(`${root}/keyProfiles/${uid}`, token(uid))).json()).fields);
  assert.equal(profile.publicKeyFingerprint, "A".repeat(43));
});

test("key replacement: maintenance and deactivated membership block approval", async () => {
  const {root, uid, entries} = await replacementFixture("maintenance");
  await expectStatus(`${root}/keyMaintenance/current`, "owner", 200, "PATCH", {active: true});
  const during = await atomicCommit("recovery-admin", entries);
  assert.equal(during.status, 403, during.body);
  await expectStatus(`${root}/keyMaintenance/current`, "owner", 200, "PATCH", {active: false});
  await expectStatus(`${root}/members/${uid}`, "owner", 200, "PATCH", {userId: uid, name: uid, role: "doctor", status: "inactive", accessLevel: 30, joinedAt: new Date()});
  const inactive = await atomicCommit("recovery-admin", entries);
  assert.equal(inactive.status, 403, inactive.body);
});

test("key replacement: new requests validate schema, identity and key bounds; stale profiles cannot approve", async () => {
  const {root, uid, entry, entries} = await replacementFixture("validation");
  const cancel = await atomicCommit(uid, [{path: entry.path, data: {status: "cancelled", resolvedBy: uid}, timestamps: ["resolvedAt"], patch: true}]);
  assert.equal(cancel.status, 200, cancel.body);
  const fresh = {...entry.data, requestId: "V".repeat(22)};
  const invalid = [
    {...fresh, extra: "unexpected"}, {...fresh, userId: "another-user"}, {...fresh, previousFingerprint: "C".repeat(43)},
    {...fresh, profile: {...fresh.profile, publicKeyJwk: "X".repeat(4097)}},
    {...fresh, backup: {...fresh.backup, ciphertext: "X".repeat(16385)}},
    {...fresh, backup: {...fresh.backup, kdfIterations: 1}},
    {...fresh, profile: {...fresh.profile, publicKeyFingerprint: fresh.previousFingerprint}},
    {...fresh, requestId: "bad"}, {...fresh, status: "approved"}
  ];
  const missing = {...fresh}; delete missing.backup; invalid.push(missing);
  for (const data of invalid) {
    const result = await atomicCommit(uid, [{...entry, data}]);
    assert.equal(result.status, 403, result.body);
  }
  for (const actor of ["recovery-admin", "stranger", "doctor"]) {
    const result = await atomicCommit(actor, [{...entry, data: fresh}]);
    assert.equal(result.status, 403, result.body);
  }
  const valid = await atomicCommit(uid, [{...entry, data: fresh}]);
  assert.equal(valid.status, 200, valid.body);
  // A changed registered identity must force a fresh request, never approve an old one.
  await expectStatus(`${root}/keyProfiles/${uid}`, "owner", 200, "PATCH", {
    userId: uid, publicKeyJwk: "intervening-key", publicKeyFingerprint: "C".repeat(43), keyAlgorithm: "RSA-OAEP-3072-SHA256", createdAt: new Date()
  });
  const response = await request(entry.path, token(uid));
  const pending = decodeFields((await response.json()).fields);
  const approval = entries.map((value, i) => i === 0 ? {...value, data: pending.profile} : i === 1 ? {...value, data: pending.backup} : value);
  const stale = await atomicCommit("recovery-admin", approval);
  assert.equal(stale.status, 403, stale.body);
});

test("member roles: admins can swap Doctor/Nurse while preserving identity and permissions", async () => {
  const root = "organizations/RoleOrg1";
  for (const [uid, role, status] of [["role-admin", "admin", "active"], ["role-doctor", "doctor", "active"], ["role-nurse", "nurse", "inactive"]]) {
    await expectStatus(`${root}/members/${uid}`, "owner", 200, "PATCH", {userId: uid, name: uid, role, status, accessLevel: 42, joinedAt: new Date()});
  }
  for (const [uid, original, replacement] of [["role-doctor", "doctor", "nurse"], ["role-nurse", "nurse", "doctor"]]) {
    const path = `${root}/members/${uid}`;
    const before = decodeFields((await (await request(path, token("role-admin"))).json()).fields);
    for (const role of [replacement, original]) {
      const result = await atomicCommit("role-admin", [{path, data: {role}, patch: true}]);
      assert.equal(result.status, 200, result.body);
      const after = decodeFields((await (await request(path, token("role-admin"))).json()).fields);
      assert.deepEqual(after, {...before, role});
    }
  }
});

test("member roles: reject self-service, promotion, combined edits, former members and maintenance", async () => {
  const root = "organizations/RoleOrg2";
  for (const [uid, role, status] of [["role-admin2", "admin", "active"], ["role-user2", "doctor", "active"], ["role-inactive2", "admin", "inactive"], ["role-left2", "nurse", "left"]]) {
    await expectStatus(`${root}/members/${uid}`, "owner", 200, "PATCH", {userId: uid, name: uid, role, status, accessLevel: 42, joinedAt: new Date()});
  }
  const path = `${root}/members/role-user2`;
  for (const actor of ["role-user2", "stranger", "role-admin", "role-inactive2"]) {
    const result = await atomicCommit(actor, [{path, data: {role: "nurse"}, patch: true}]);
    assert.equal(result.status, 403, result.body);
  }
  for (const data of [{role: "admin"}, {role: "invalid"}, {role: 42}, {role: "nurse", accessLevel: 99}, {role: "nurse", status: "inactive"}, {role: "nurse", userId: "stranger"}, {role: "nurse", name: "forged"}, {role: "nurse", joinedAt: new Date()}, {role: "nurse", extra: "x"}]) {
    const result = await atomicCommit("role-admin2", [{path, data, patch: true}]);
    assert.equal(result.status, 403, result.body);
  }
  for (const uid of ["role-admin2", "role-left2"]) {
    const result = await atomicCommit("role-admin2", [{path: `${root}/members/${uid}`, data: {role: "doctor"}, patch: true}]);
    assert.equal(result.status, 403, result.body);
  }
  await expectStatus(`${root}/keyMaintenance/current`, "owner", 200, "PATCH", {active: true});
  const during = await atomicCommit("role-admin2", [{path, data: {role: "nurse"}, patch: true}]);
  assert.equal(during.status, 403, during.body);
});
