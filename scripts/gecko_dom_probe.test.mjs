import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import vm from 'node:vm';

const asset = (name) => readFileSync(new URL(`../app/src/main/assets/candy_privacy/${name}`, import.meta.url), 'utf8');

function probeHarness({ failMeasurement = false, depth = 3 } = {}) {
  const style = { position: 'fixed', top: '0px', paddingTop: '0px', marginTop: '0px',
    maxBlockSize: 'none', transform: 'none', display: 'block', visibility: 'visible' };
  const element = (tagName, parentElement = null) => ({
    tagName, parentElement, style: { setProperty() {} },
    get textContent() { throw new Error('Page text must not be read'); },
    get id() { throw new Error('Page ID must not be read'); },
    get className() { throw new Error('Page class must not be read'); },
    getBoundingClientRect: () => ({ x: 0, y: 0, width: 360, height: 80 }),
  });
  const root = element('HTML');
  const body = element('BODY', root);
  let header = element('HEADER', body);
  for (let index = 0; index < depth; index++) header = element('DIV', header);
  let appended = 0;
  let removed = 0;
  let reads = 0;
  let measurement;
  root.appendChild = (node) => { measurement = node; appended++; };
  const context = vm.createContext({
    document: {
      documentElement: root, body,
      createElement: () => ({ style: { setProperty() {} }, remove() { removed++; } }),
      querySelector: (selector) => selector === 'header' ? header : null,
      elementFromPoint: () => header,
    },
    innerWidth: 360, innerHeight: 800, devicePixelRatio: 3, scrollY: 100,
    visualViewport: { scale: 1, offsetTop: 0 },
    matchMedia: () => ({ matches: true }),
    getComputedStyle: (node) => {
      reads++;
      if (node === measurement) {
        if (failMeasurement) throw new Error('Measurement failed');
        return { paddingTop: '48px', paddingRight: '0px', paddingBottom: '24px', paddingLeft: '0px' };
      }
      return style;
    },
    MutationObserver: class { constructor() { throw new Error('No observer allowed'); } },
    setTimeout() { throw new Error('No timers allowed'); },
    requestAnimationFrame() { throw new Error('No animation frames allowed'); },
  });
  vm.runInContext(asset('dom_probe.js'), context);
  return { context, get counts() { return { appended, removed, reads }; } };
}

test('probe remains inert until explicitly sampled and exports no author metadata', () => {
  const harness = probeHarness();
  assert.deepEqual(harness.counts, { appended: 0, removed: 0, reads: 0 });
  const report = JSON.parse(JSON.stringify(harness.context.CandyDomProbe.sample()));
  assert.deepEqual(report.env, { top: 48, right: 0, bottom: 24, left: 0 });
  assert.equal(report.darkPreferred, true);
  assert.equal(report.viewportFit, 'unset');
  assert.equal(report.candidates[0].y, 0);
  assert.equal(report.candidates[0].paddingTop, 0);
  assert.deepEqual(Object.keys(report.candidates[0]), [
    'tag', 'position', 'x', 'y', 'width', 'height', 'top', 'paddingTop', 'marginTop',
    'maxBlockSize', 'hasTransform', 'displayed', 'visible',
  ]);
  assert.equal(harness.counts.appended, 1);
  assert.equal(harness.counts.removed, 1);
});

test('temporary env measurement is removed even on a read failure', () => {
  const harness = probeHarness({ failMeasurement: true });
  assert.throws(() => harness.context.CandyDomProbe.sample(), /Measurement failed/);
  assert.equal(harness.counts.appended, 1);
  assert.equal(harness.counts.removed, 1);
});

test('candidate depth and geometry reads remain bounded on deeply nested documents', () => {
  const harness = probeHarness({ depth: 10_000 });
  const report = harness.context.CandyDomProbe.sample();
  assert.ok(report.candidates.length <= 16);
  assert.ok(harness.counts.reads <= 20);
});

function backgroundHarness(policy) {
  const posted = [];
  const sent = [];
  let resolve;
  const context = vm.createContext({
    policiesByToken: new Map([['token', policy]]), tokenByTab: new Map([[7, 'token']]),
    nativePort: { postMessage: (message) => posted.push(message) }, PROTOCOL_VERSION: 1,
    browser: { tabs: { sendMessage: (...args) => {
      sent.push(args);
      return new Promise((done) => { resolve = done; });
    } } },
  });
  const source = asset('background.js').split('function probeDom(message) {')[1]
    .split('function updatePictureInPicturePlayback')[0];
  vm.runInContext(`function probeDom(message) {${source}`, context);
  return { context, posted, sent, complete(payload) { resolve(payload); } };
}

const request = { token: 'token', revision: 3, navigationGeneration: 2, requestId: 1 };
const policy = { domDiagnosticsEnabled: true, revision: 3, navigationGeneration: 2 };

test('background only sends a fixed frame-zero request for an enabled current policy', async () => {
  const harness = backgroundHarness(policy);
  harness.context.probeDom(request);
  assert.equal(harness.sent[0][0], 7);
  assert.equal(harness.sent[0][2].frameId, 0);
  assert.deepEqual(Object.keys(harness.sent[0][1]), ['type', 'revision', 'navigationGeneration']);
  harness.complete({ version: 1 });
  await Promise.resolve();
  assert.equal(harness.posted[0].type, 'dom-probe-result');
  assert.equal(harness.posted[0].navigationGeneration, 2);
});

test('background rejects disabled stale malformed and removed targets', () => {
  for (const invalid of [
    { ...policy, domDiagnosticsEnabled: false }, { ...policy, domDiagnosticsEnabled: 'true' },
    { ...policy, revision: 2 }, { ...policy, navigationGeneration: 1 },
  ]) {
    const harness = backgroundHarness(invalid);
    harness.context.probeDom(request);
    assert.equal(harness.sent.length, 0);
  }
  const harness = backgroundHarness(policy);
  harness.context.probeDom({ ...request, requestId: 1.5 });
  harness.context.tokenByTab.clear();
  harness.context.probeDom(request);
  assert.equal(harness.sent.length, 0);
});

test('background rejects results after navigation or owner removal', async () => {
  for (const invalidate of [
    (context) => context.policiesByToken.set('token', { ...policy, revision: 4 }),
    (context) => context.policiesByToken.set('token', { ...policy, navigationGeneration: 3 }),
    (context) => context.tokenByTab.clear(),
    (context) => context.policiesByToken.set('token', { ...policy, domDiagnosticsEnabled: false }),
  ]) {
    const harness = backgroundHarness(policy);
    harness.context.probeDom(request);
    invalidate(harness.context);
    harness.complete({ version: 1 });
    await Promise.resolve();
    assert.equal(harness.posted.length, 0);
  }
});

test('content routing refuses non-diagnostic stale and iframe requests', async () => {
  let sampleCalls = 0;
  let listener;
  const top = {};
  const context = vm.createContext({
    self: top, top,
    CandyContentTopInset: { domDiagnosticsEnabled: () => false, policyRevision: () => 3, navigationGeneration: () => 2 },
    CandyDomProbe: { sample: () => { sampleCalls++; return { version: 1 }; } },
    browser: { runtime: { onMessage: { addListener: (value) => { listener = value; } } } },
  });
  const source = asset('content.js').split('browser.runtime.onMessage.addListener((message) => {')[1];
  vm.runInContext(`browser.runtime.onMessage.addListener((message) => {${source}`, context);
  listener({ ...request, type: 'dom-probe' });
  assert.equal(sampleCalls, 0);
  context.CandyContentTopInset.domDiagnosticsEnabled = () => true;
  listener({ ...request, type: 'dom-probe', revision: 2 });
  listener({ ...request, type: 'dom-probe', navigationGeneration: 1 });
  assert.equal(sampleCalls, 0);
  assert.equal((await listener({ ...request, type: 'dom-probe' })).version, 1);
  assert.equal(sampleCalls, 1);
  context.self = {};
  listener({ ...request, type: 'dom-probe' });
  assert.equal(sampleCalls, 1);
});
