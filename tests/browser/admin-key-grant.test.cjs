const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

// Exercise the compiler's actual state machine: the original Kotlin safe-call on
// a suspend function returning dynamic omitted the suspension boundary entirely.
const generatedPath = process.env.JANANISYNC_GENERATED_SHARED || path.resolve(
  __dirname, '../../webApp/build/compileSync/js/main/developmentExecutable/kotlin/JananiSync-shared.mjs'
);
const generated = fs.readFileSync(generatedPath, 'utf8');
const source = generated.match(/function \$adminGrantOrganizationKeyCOROUTINE\$\([\s\S]*?\n};/)[0];
const maintenanceOffset = source.indexOf('/keyMaintenance/current');
assert.notEqual(maintenanceOffset, -1, 'generated grant must check maintenance');
const maintenanceState = Number([...source.slice(0, maintenanceOffset).matchAll(/case (\d+):/g)].at(-1)[1]);
const failureState = Number(source.match(/this\.set_exceptionState_[\w$]+\((\d+)\)/)[1]);
const fields = [...source.matchAll(/this\.([\w$]+)\b/g)].map(match => match[1]);

function createGrant({ existingEnvelope = null } = {}) {
  const suspended = Object.freeze({ suspended: true });
  const unit = Object.freeze({ unit: true });
  const reads = [];
  const writes = [];
  let readContinuation;
  let writeContinuation;
  let completions = 0;

  function CoroutineImpl() {
    this.state = maintenanceState;
    this.result = unit;
    this.exceptionState = failureState;
    this.exception = null;
  }
  // Resolve accessor names from generated output rather than fixing Kotlin's
  // mangled suffixes or temporary-field numbering in the regression test.
  for (const [accessor, field] of Object.entries({
    get_state: 'state', set_state: 'state',
    get_result: 'result',
    get_exceptionState: 'exceptionState', set_exceptionState: 'exceptionState',
    get_exception: 'exception', set_exception: 'exception'
  })) {
    const names = new Set([...source.matchAll(new RegExp(`this\\.(${accessor}_[\\w$]+)\\(`, 'g'))].map(match => match[1]));
    for (const name of names) {
      CoroutineImpl.prototype[name] = accessor.startsWith('set_')
        ? function (value) { this[field] = value; }
        : function () { return this[field]; };
    }
  }

  const storage = new Proxy({}, {
    get(_target, name) {
      assert.match(name, /^read_/);
      return (documentPath, continuation) => {
        reads.push(documentPath);
        readContinuation = continuation;
        return suspended;
      };
    }
  });
  const firebase = new Proxy({}, {
    get(_target, name) {
      assert.match(name, /^grantOrganizationKeyEnvelope_/);
      return (...args) => { writes.push(args); return { write: true }; };
    }
  });
  const context = vm.createContext({
    CoroutineImpl,
    protoOf: constructor => constructor.prototype,
    Unit_getInstance: () => unit,
    get_COROUTINE_SUSPENDED: () => suspended,
    OrganizationRotationStore_getInstance: () => storage,
    FirebaseService_getInstance: () => firebase,
    awaitFirebaseUnit(_promise, continuation) {
      writeContinuation = continuation;
      return suspended;
    }
  });
  vm.runInContext(source, context, { filename: generatedPath });
  Object.setPrototypeOf(context.$adminGrantOrganizationKeyCOROUTINE$.prototype, CoroutineImpl.prototype);
  const coroutine = new context.$adminGrantOrganizationKeyCOROUTINE$({}, 'Rejoin01', 'member', null);
  const setField = (prefix, value) => {
    const field = fields.find(name => name.startsWith(prefix));
    assert.ok(field, `generated field ${prefix} must exist`);
    coroutine[field] = value;
  };
  setField('currentEnvelope', existingEnvelope);
  setField('keyVersion', 2);
  setField('wrappedKey', 'wrapped-current-key');
  setField('admin', { uid: 'admin' });
  const resumeMethod = Object.getOwnPropertyNames(context.$adminGrantOrganizationKeyCOROUTINE$.prototype)
    .find(name => name.startsWith('doResume_'));
  const run = () => {
    try {
      const result = coroutine[resumeMethod]();
      if (result !== suspended) completions++;
      return result;
    } catch (error) {
      completions++;
      throw error;
    }
  };
  return {
    suspended, unit, reads, writes,
    get completions() { return completions; },
    start: run,
    resolveRead(value) {
      assert.ok(readContinuation);
      readContinuation = null;
      coroutine.result = value;
      return run();
    },
    rejectRead(error) {
      assert.ok(readContinuation);
      readContinuation = null;
      coroutine.exception = error;
      coroutine.state = coroutine.exceptionState;
      return run();
    },
    resolveWrite() {
      assert.ok(writeContinuation);
      writeContinuation = null;
      coroutine.result = unit;
      return run();
    }
  };
}

for (const maintenance of [null, { active: false }]) {
  test(`grant waits for maintenance read (${JSON.stringify(maintenance)})`, () => {
    const grant = createGrant();
    assert.equal(grant.start(), grant.suspended);
    assert.deepEqual(grant.reads, ['organizations/Rejoin01/keyMaintenance/current']);
    assert.equal(grant.writes.length, 0, 'key grant must not run while maintenance read is suspended');
    assert.equal(grant.resolveRead(maintenance), grant.suspended);
    assert.equal(grant.completions, 0, 'read completion must not finish the still-pending grant');
    assert.deepEqual(grant.writes, [['Rejoin01', 'member', 2, 'wrapped-current-key', 'admin']]);
    assert.equal(grant.resolveWrite(), grant.unit);
    assert.equal(grant.completions, 1);
  });
}

test('active maintenance skips key grant', () => {
  const grant = createGrant();
  assert.equal(grant.start(), grant.suspended);
  assert.equal(grant.writes.length, 0);
  assert.equal(grant.resolveRead({ active: true }), grant.unit);
  assert.equal(grant.writes.length, 0);
  assert.equal(grant.completions, 1);
});

test('matching current envelope skips duplicate key grant after read completes', () => {
  const grant = createGrant({ existingEnvelope: { keyVersion: 2 } });
  assert.equal(grant.start(), grant.suspended, 'even a duplicate grant must await the outstanding read');
  assert.equal(grant.writes.length, 0);
  assert.equal(grant.resolveRead({ active: false }), grant.unit);
  assert.equal(grant.writes.length, 0);
  assert.equal(grant.completions, 1);
});

test('maintenance read failure propagates without granting access', () => {
  const grant = createGrant();
  assert.equal(grant.start(), grant.suspended);
  assert.equal(grant.writes.length, 0);
  const error = new Error('maintenance read failed');
  assert.throws(() => grant.rejectRead(error), value => value === error);
  assert.equal(grant.writes.length, 0);
  assert.equal(grant.completions, 1);
});
