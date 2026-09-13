import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import vm from 'node:vm';

const kotlinSource = readFileSync(new URL(
  '../app/src/main/java/dev/sk2andy/materialbrowser/browser/WebContentTopInsetScript.kt',
  import.meta.url,
), 'utf8');
const generatedScript = kotlinSource
  .split('        """\n')[1]
  .split('\n        """.trimIndent()')[0]
  .replaceAll("${'$'}", '$')
  .replaceAll('$bridgeName', 'CandyContentTopInset');
const bridgeScript = readFileSync(new URL(
  '../app/src/main/assets/candy_privacy/content_top_inset_bridge.js',
  import.meta.url,
), 'utf8');

function performanceFixture() {
  const entries = new Set();
  const published = [];
  const marks = [];
  let calls = 0;
  return {
    entries,
    published,
    marks,
    get calls() { return calls; },
    api: {
      mark(name) { calls++; entries.add(name); marks.push(name); },
      measure(name, start, end) {
        calls++;
        assert.ok(entries.has(start));
        assert.ok(entries.has(end));
        entries.add(name);
        published.push(name);
      },
      clearMarks(name) { calls++; entries.delete(name); },
      clearMeasures(name) { calls++; entries.delete(name); },
    },
  };
}

function safeAreaHarness(enabled, performance, document = { elementsFromPoint: () => [] }) {
  const helpers = generatedScript.split('const beginCandyPerformancePhase =')[1]
    .split('const currentPolicyKey =')[0];
  const discovery = generatedScript.split('const deepElementsFromPoint =')[1]
    .split('const isVisiblePositionedElement =')[0];
  const context = vm.createContext({
    performance,
    document,
    CandyContentTopInset: { performanceDiagnosticsEnabled: () => enabled },
  });
  vm.runInContext(`
    const beginCandyPerformancePhase = ${helpers}
    let layoutReadCache = null;
    const observeDiscoveredShadowRoot = () => {};
    const deepElementsFromPoint = ${discovery}
    globalThis.begin = beginCandyPerformancePhase;
    globalThis.end = endCandyPerformancePhase;
    globalThis.discover = deepElementsFromPoint;
  `, context);
  return context;
}

function bridgeHarness(performance) {
  const listeners = [];
  const context = vm.createContext({
    performance,
    browser: {
      runtime: {
        sendMessage: () => Promise.resolve({ ready: false, type: 'content-policy' }),
        onMessage: { addListener(listener) { listeners.push(listener); } },
      },
    },
    document: { documentElement: { clientHeight: 800, scrollHeight: 2000 }, addEventListener() {} },
    addEventListener() {},
    requestAnimationFrame: () => 1,
    setTimeout: () => 1,
    clearTimeout() {},
    deliverMessage(message) { listeners.forEach((listener) => listener(message)); },
  });
  vm.runInContext(bridgeScript, context);
  return context;
}

test('Gradle template substitutions retain a valid complete Gecko script', () => {
  new vm.Script(generatedScript);
  assert.ok(generatedScript.includes('globalThis.CandyContentTopInset'));
  assert.ok(!generatedScript.includes("${'$'}"));
});

test('disabled safe-area diagnostics call no performance API', () => {
  const fixture = performanceFixture();
  const harness = safeAreaHarness(false, fixture.api);
  harness.discover(10, 10);
  assert.equal(fixture.calls, 0);
  assert.equal(fixture.entries.size, 0);
});

test('enabled safe-area discovery publishes static labels and clears its own entries', () => {
  const fixture = performanceFixture();
  fixture.entries.add('website-owned-marker');
  const harness = safeAreaHarness(true, fixture.api);
  for (let index = 0; index < 1000; index++) harness.discover(10, 10);
  assert.equal(fixture.published.length, 1000);
  assert.ok(fixture.published.every((name) => name === 'Candy.SafeArea.PointDiscovery'));
  assert.deepEqual([...fixture.entries], ['website-owned-marker']);
});

test('DOM exceptions still propagate and measurement entries clear in finally', () => {
  const fixture = performanceFixture();
  const failure = new Error('DOM failure');
  const harness = safeAreaHarness(true, fixture.api, {
    elementsFromPoint() { throw failure; },
  });
  assert.throws(() => harness.discover(10, 10), (error) => error === failure);
  assert.deepEqual(fixture.published, ['Candy.SafeArea.PointDiscovery']);
  assert.equal(fixture.entries.size, 0);
});

test('timing failures do not change DOM returns or original exceptions', () => {
  const fixture = performanceFixture();
  fixture.api.measure = () => { throw new Error('timing failure'); };
  const harness = safeAreaHarness(true, fixture.api);
  assert.equal(harness.discover(10, 10).length, 0);
  assert.equal(fixture.entries.size, 0);
  fixture.api.mark = () => { throw new Error('mark failure'); };
  assert.equal(harness.discover(10, 10).length, 0);
  const failure = new Error('original DOM failure');
  const failingHarness = safeAreaHarness(true, fixture.api, {
    elementsFromPoint() { throw failure; },
  });
  assert.throws(() => failingHarness.discover(10, 10), (error) => error === failure);
});

test('bridge diagnostics require explicit true and stop after recording policy toggles', () => {
  const fixture = performanceFixture();
  const harness = bridgeHarness(fixture.api);
  const publish = (revision, enabled) => {
    harness.applyPolicy({
      type: 'content-policy', ready: true, revision,
      scrollMetricsEnabled: true, performanceDiagnosticsEnabled: enabled,
    });
    harness.publishScrollMetrics();
  };
  publish(1, 'true');
  assert.equal(fixture.calls, 0);
  publish(2, true);
  assert.deepEqual(fixture.published, ['Candy.ScrollMetrics.Publish']);
  assert.equal(fixture.entries.size, 0);
  const previousCalls = fixture.calls;
  publish(3, false);
  assert.equal(fixture.calls, previousCalls);
  publish(2, true);
  assert.equal(fixture.calls, previousCalls);
  assert.equal(harness.CandyContentTopInset.performanceDiagnosticsEnabled(), false);
});

test('background content policy does not coerce truthy diagnostic input', () => {
  const source = readFileSync(new URL(
    '../app/src/main/assets/candy_privacy/background.js', import.meta.url,
  ), 'utf8');
  const contentPolicy = source.split('function contentPolicy(policy) {')[1]
    .split('function publishContentPolicy')[0];
  const context = vm.createContext({});
  vm.runInContext(`function contentPolicy(policy) {${contentPolicy}`, context);
  assert.equal(context.contentPolicy({ performanceDiagnosticsEnabled: true })
    .performanceDiagnosticsEnabled, true);
  for (const value of [undefined, false, 1, 'true']) {
    assert.equal(context.contentPolicy({ performanceDiagnosticsEnabled: value })
      .performanceDiagnosticsEnabled, false);
  }
});

test('diagnostic state messages require current revision without triggering reconciliation', () => {
  const fixture = performanceFixture();
  const harness = bridgeHarness(fixture.api);
  let reconciliations = 0;
  harness.__candyReconcileContentTopInset = () => { reconciliations++; };
  harness.applyPolicy({
    type: 'content-policy', ready: true, revision: 5, scrollMetricsEnabled: true,
  });
  assert.equal(reconciliations, 1);
  harness.deliverMessage({
    type: 'performance-diagnostics-state', revision: 4, performanceDiagnosticsEnabled: true,
  });
  assert.equal(harness.CandyContentTopInset.performanceDiagnosticsEnabled(), false);
  harness.deliverMessage({
    type: 'performance-diagnostics-state', revision: 5, performanceDiagnosticsEnabled: true,
  });
  harness.publishScrollMetrics();
  assert.deepEqual(fixture.published, ['Candy.ScrollMetrics.Publish']);
  assert.equal(harness.CandyContentTopInset.policyRevision(), 5);
  assert.equal(reconciliations, 1);
  harness.deliverMessage({
    type: 'performance-diagnostics-state', revision: 5, performanceDiagnosticsEnabled: false,
  });
  const previousCalls = fixture.calls;
  harness.publishScrollMetrics();
  assert.equal(fixture.calls, previousCalls);
  assert.equal(reconciliations, 1);
});

test('background diagnostic state preserves existing policy and rejects stale revisions', () => {
  const source = readFileSync(new URL(
    '../app/src/main/assets/candy_privacy/background.js', import.meta.url,
  ), 'utf8');
  const helper = source.split('function publishPerformanceDiagnosticsState(message) {')[1]
    .split('function publishPerformanceDiagnosticsGap')[0];
  const policy = {
    revision: 7, navigationGeneration: 3, topInsetPx: 50, pageHost: 'website-owned-host',
    performanceDiagnosticsEnabled: false,
  };
  const policies = new Map([['session', policy]]);
  const messages = [];
  const context = vm.createContext({
    policiesByToken: policies,
    tokenByTab: new Map([[12, 'session']]),
    browser: { tabs: { sendMessage(tab, message) { messages.push([tab, message]); return Promise.resolve(); } } },
  });
  vm.runInContext(`function publishPerformanceDiagnosticsState(message) {${helper}`, context);
  context.publishPerformanceDiagnosticsState({
    token: 'session', revision: 6, performanceDiagnosticsEnabled: true,
  });
  assert.equal(messages.length, 0);
  assert.equal(policies.get('session'), policy);
  context.publishPerformanceDiagnosticsState({
    token: 'session', revision: 7, performanceDiagnosticsEnabled: true,
  });
  assert.equal(policies.get('session').revision, 7);
  assert.equal(policies.get('session').navigationGeneration, 3);
  assert.equal(policies.get('session').topInsetPx, 50);
  assert.equal(policies.get('session').pageHost, 'website-owned-host');
  assert.equal(policy.performanceDiagnosticsEnabled, false);
  assert.equal(messages.length, 1);
  assert.deepEqual(Object.keys(messages[0][1]).sort(), [
    'performanceDiagnosticsEnabled', 'revision', 'type',
  ]);
  context.publishPerformanceDiagnosticsState({
    token: 'session', revision: 7, performanceDiagnosticsEnabled: true,
  });
  assert.equal(messages.length, 1);
});

test('gap markers require enabled ready current policy and clear only their own entry', () => {
  const fixture = performanceFixture();
  fixture.entries.add('website-owned-marker');
  const harness = bridgeHarness(fixture.api);
  const gap = (revision) => harness.deliverMessage({ type: 'performance-diagnostics-gap', revision });
  gap(5);
  assert.equal(fixture.calls, 0);
  harness.applyPolicy({ type: 'content-policy', ready: true, revision: 5 });
  gap(5);
  assert.equal(fixture.calls, 0);
  harness.deliverMessage({
    type: 'performance-diagnostics-state', revision: 5, performanceDiagnosticsEnabled: true,
  });
  gap(4);
  assert.equal(fixture.calls, 0);
  gap(5);
  assert.deepEqual(fixture.marks, ['Candy.Diagnostics.UserObservedGap']);
  assert.deepEqual([...fixture.entries], ['website-owned-marker']);
  fixture.api.mark = () => { throw new Error('timing failure'); };
  assert.doesNotThrow(() => gap(5));
  fixture.api.clearMarks = () => { throw new Error('cleanup failure'); };
  assert.doesNotThrow(() => gap(5));
  Object.defineProperty(fixture.api, 'mark', { get() { throw new Error('API access failure'); } });
  assert.doesNotThrow(() => gap(5));
});

test('background gap relay contains only static type and current revision', () => {
  const source = readFileSync(new URL(
    '../app/src/main/assets/candy_privacy/background.js', import.meta.url,
  ), 'utf8');
  const helper = source.split('function publishPerformanceDiagnosticsGap(message) {')[1]
    .split('function scheduleContentPolicy')[0];
  const policy = { revision: 7, performanceDiagnosticsEnabled: false };
  const policies = new Map([['session', policy]]);
  const messages = [];
  const context = vm.createContext({
    policiesByToken: policies,
    tokenByTab: new Map([[12, 'session']]),
    browser: { tabs: { sendMessage(tab, message) { messages.push([tab, message]); return Promise.resolve(); } } },
  });
  vm.runInContext(`function publishPerformanceDiagnosticsGap(message) {${helper}`, context);
  context.publishPerformanceDiagnosticsGap({ token: 'session', revision: 7 });
  assert.equal(messages.length, 0);
  policy.performanceDiagnosticsEnabled = true;
  context.publishPerformanceDiagnosticsGap({ token: 'session', revision: 6 });
  assert.equal(messages.length, 0);
  context.publishPerformanceDiagnosticsGap({ token: 'missing', revision: 7 });
  assert.equal(messages.length, 0);
  context.publishPerformanceDiagnosticsGap({ token: 'session', revision: 7 });
  assert.equal(messages.length, 1);
  assert.deepEqual(Object.keys(messages[0][1]).sort(), ['revision', 'type']);
  assert.equal(messages[0][1].type, 'performance-diagnostics-gap');
  assert.equal(policies.get('session'), policy);
});
