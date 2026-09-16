import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import vm from 'node:vm';

const asset = (name) => readFileSync(new URL(`../app/src/gecko/assets/candy_privacy/${name}`, import.meta.url), 'utf8');

function probeHarness({ failMeasurement = false, depth = 3, computedStyle = {}, flowDepth = 0,
  skippedSiblings = 0, configuration, diagnostics, box = {}, documentState = 'interactive', inert = false,
  distinctTopRightHit = false } = {}) {
  const style = { position: 'fixed', top: '0px', paddingTop: '0px', marginTop: '0px',
    maxBlockSize: 'none', transform: 'none', display: 'block', visibility: 'visible',
    overflowX: 'visible', overflowY: 'visible', ...computedStyle };
  const element = (tagName, parentElement = null) => ({
    tagName, parentElement, inert, firstElementChild: null, nextElementSibling: null, style: { setProperty() {} },
    get textContent() { throw new Error('Page text must not be read'); },
    get id() { throw new Error('Page ID must not be read'); },
    get className() { throw new Error('Page class must not be read'); },
    getBoundingClientRect: () => ({ x: 0, y: 0, width: 360, height: 80, ...box }),
  });
  const root = element('HTML');
  const body = element('BODY', root);
  let header = element('HEADER', body);
  for (let index = 0; index < depth; index++) header = element('DIV', header);
  const topRight = distinctTopRightHit ? element('BUTTON', body) : header;
  let nav = element('NAV', body);
  if (distinctTopRightHit) for (let index = 0; index < depth; index++) nav = element('DIV', nav);
  let parent = body;
  for (let index = 0; index < flowDepth; index++) {
    parent.firstElementChild = element(index === 0 ? 'MAIN' : 'DIV', parent);
    parent = parent.firstElementChild;
  }
  for (let index = 0; index < skippedSiblings; index++) {
    const sibling = element('DIV', body);
    sibling.computed = { ...style, display: 'none' };
    sibling.nextElementSibling = body.firstElementChild;
    body.firstElementChild = sibling;
  }
  let appended = 0;
  let removed = 0;
  let reads = 0;
  let measurement;
  const hits = [];
  root.appendChild = (node) => { measurement = node; appended++; };
  const context = vm.createContext({
    document: {
      documentElement: root, body, readyState: documentState,
      createElement: () => ({ style: { setProperty() {} }, remove() { removed++; } }),
      querySelector: (selector) => selector === 'header' ? header : distinctTopRightHit && selector === 'nav' ? nav : null,
      elementFromPoint: (x, y) => { hits.push([x, y]); return x === 336 && y === 72 ? topRight : header; },
    },
    innerWidth: 360, innerHeight: 800, devicePixelRatio: 3, scrollY: 100,
    visualViewport: { scale: 1, offsetTop: 0 },
    matchMedia: () => ({ matches: true }),
    CandyContentTopInset: { cssSafeAreaConfiguration: () => typeof configuration === 'function' ? configuration() : configuration },
    CandyCssSafeAreaDiagnostics: { sample: () => diagnostics },
    getComputedStyle: (node) => {
      reads++;
      if (node === measurement) {
        if (failMeasurement) throw new Error('Measurement failed');
        return { paddingTop: '48px', paddingRight: '0px', paddingBottom: '24px', paddingLeft: '0px' };
      }
      return node.computed || style;
    },
    MutationObserver: class { constructor() { throw new Error('No observer allowed'); } },
    setTimeout() { throw new Error('No timers allowed'); },
    requestAnimationFrame() { throw new Error('No animation frames allowed'); },
  });
  vm.runInContext(asset('dom_probe.js'), context);
  return { context, hits, get counts() { return { appended, removed, reads }; } };
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
    'maxBlockSize', 'hasTransform', 'overflowX', 'overflowY', 'hasMovingEffects',
    'hasAnimationEffects', 'hasTransitionEffects', 'hasIndividualTransformEffects', 'hasOffsetPathEffects', 'hasZoomEffects',
    'transitionPropertyKind',
    'pointerEvents', 'inert',
    'hasContainingBlockEffects', 'displayed', 'visible',
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

test('manual top-right hit reuses bounded candidate geometry and exports strict pointer metadata only', () => {
  for (const [pointerEvents, expected, inert, expectedInert] of [
    ['auto', 'auto', true, true], ['none', 'none', false, false], ['secret-author-pointer', 'other', 'true', false],
  ]) {
    const harness = probeHarness({ computedStyle: { pointerEvents }, inert });
    assert.equal(harness.hits.length, 0);
    const report = harness.context.CandyDomProbe.sample();
    assert.deepEqual(harness.hits[0], [336, 72]);
    assert.equal(harness.hits.length, 10);
    assert.equal(report.topRightCandidateIndex, 0);
    assert.equal(report.candidates[0].pointerEvents, expected);
    assert.equal(report.candidates[0].inert, expectedInert);
    assert.ok(report.candidates.length <= 16); assert.ok(harness.counts.reads <= 20);
    assert.equal(JSON.stringify(report).includes('secret-author'), false);
  }
});

test('candidate depth and geometry reads remain bounded on deeply nested documents', () => {
  const harness = probeHarness({ depth: 10_000 });
  const report = harness.context.CandyDomProbe.sample();
  assert.ok(report.candidates.length <= 16);
  assert.ok(harness.counts.reads <= 20);
});

test('distinct top-right hit cannot be starved by separate deep header and nav seed chains', () => {
  const harness = probeHarness({ depth: 10_000, distinctTopRightHit: true });
  const report = harness.context.CandyDomProbe.sample();
  assert.equal(report.topRightCandidateIndex, 0);
  assert.equal(report.candidates[report.topRightCandidateIndex].tag, 'BUTTON');
  assert.equal(report.candidates.length, 16);
  assert.ok(harness.counts.reads <= 20);
});

test('manual probe reports fixed configuration and document readiness without policy strings', () => {
  const harness = probeHarness({ configuration: { ready: true, enabled: true, cssSafeAreaTopInsetPx: 156,
    revision: 42, authorMetadata: 'do-not-export' } });
  const report = JSON.parse(JSON.stringify(harness.context.CandyDomProbe.sample()));
  assert.equal(report.readyState, 'interactive');
  assert.deepEqual(report.cssSafeArea, { available: true, ready: true, enabled: true, insetPx: 156 });
  assert.equal(JSON.stringify(report).includes('do-not-export'), false);
});

test('configuration booleans/insets are strict and missing or failing bridge remains diagnostic-only', () => {
  for (const configuration of [undefined, [], () => { throw new Error('Unavailable policy'); }]) {
    const report = probeHarness({ configuration }).context.CandyDomProbe.sample();
    assert.deepEqual(JSON.parse(JSON.stringify(report.cssSafeArea)), { available: false, ready: false, enabled: false, insetPx: null });
  }
  for (const inset of ['156', -1, Infinity, 10001]) {
    const report = probeHarness({ configuration: { ready: 'true', enabled: 1, cssSafeAreaTopInsetPx: inset } }).context.CandyDomProbe.sample();
    assert.equal(report.cssSafeArea.ready, false); assert.equal(report.cssSafeArea.enabled, false);
    assert.equal(report.cssSafeArea.insetPx, null);
  }
});

test('computed overflow is allowlisted and moving/containing effects export only booleans', () => {
  const report = probeHarness({ computedStyle: { overflowX: 'secret-author-string', overflowY: 'clip',
    transform: 'none', translate: '0px 3px', animationName: 'secret-animation', contain: 'paint' } }).context.CandyDomProbe.sample();
  assert.equal(report.html.overflowX, 'other'); assert.equal(report.html.overflowY, 'clip');
  assert.equal(report.html.hasMovingEffects, true); assert.equal(report.html.hasContainingBlockEffects, true);
  assert.equal(JSON.stringify(report).includes('secret'), false);
});

test('moving effects are anonymous independent booleans from the same cached style', () => {
  const fields = ['hasAnimationEffects', 'hasTransitionEffects', 'hasIndividualTransformEffects', 'hasOffsetPathEffects', 'hasZoomEffects'];
  for (const [computedStyle, expected] of [
    [{ animationName: 'secret-animation' }, 'hasAnimationEffects'],
    [{ transitionDuration: '0s, 0.1s' }, 'hasTransitionEffects'],
    [{ translate: '0px' }, 'hasIndividualTransformEffects'],
    [{ offsetPath: 'path("secret-path")' }, 'hasOffsetPathEffects'],
    [{ zoom: '2' }, 'hasZoomEffects'],
  ]) {
    const harness = probeHarness({ computedStyle });
    const report = harness.context.CandyDomProbe.sample();
    assert.equal(report.html.hasMovingEffects, true);
    assert.equal(report.html.hasTransform, false);
    for (const key of fields) assert.equal(report.html[key], key === expected);
    assert.ok(harness.counts.reads <= 20);
    assert.equal(JSON.stringify(report).includes('secret'), false);
  }
  const stable = probeHarness({ computedStyle: { animationName: 'none', transitionDuration: '0s',
    translate: 'none', scale: 'none', rotate: 'none', offsetPath: 'none', zoom: '1' } }).context.CandyDomProbe.sample();
  assert.equal(stable.html.hasMovingEffects, false);
  for (const key of fields) assert.equal(stable.html[key], false);
});

test('transition property diagnosis exports only fixed categories, not author names', () => {
  for (const [transitionProperty, expected] of [
    ['none', 'none'], ['color, opacity', 'paint-only'], ['background-color', 'paint-only'],
    ['all', 'geometry-or-unknown'], ['color, top', 'geometry-or-unknown'],
    ['--secret-author-property', 'geometry-or-unknown'], ['color,'.repeat(5000), 'geometry-or-unknown'],
    [Array(32).fill('color').concat('top').join(','), 'geometry-or-unknown'],
  ]) {
    const report = probeHarness({ computedStyle: { transitionProperty } }).context.CandyDomProbe.sample();
    assert.equal(report.body.transitionPropertyKind, expected);
    assert.equal(JSON.stringify(report).includes('secret-author'), false);
  }
});

test('untrusted nonfinite/nonnumeric/oversize geometry and readiness are normalized without author strings', () => {
  const report = probeHarness({ computedStyle: { position: 'relative', top: 'secret-author-top', paddingTop: '10000001px' },
    flowDepth: 1, box: { x: Infinity, y: NaN, width: '360', height: 10000001 }, documentState: 'secret-author-ready' })
    .context.CandyDomProbe.sample();
  assert.equal(report.readyState, 'other');
  for (const node of [report.html, report.body, report.flowStart[0]]) {
    for (const key of ['x', 'y', 'width', 'height', 'top', 'paddingTop']) assert.equal(node[key], null);
  }
  assert.equal(JSON.stringify(report).includes('secret-author'), false);
});

test('fixed anonymous geometry schema includes frame and input tags but never frame identity', () => {
  assert.match(asset('dom_probe.js'), /"IFRAME", "TEXTAREA", "SELECT"/);
  assert.doesNotMatch(asset('dom_probe.js'), /\.(?:src|origin|textContent|className)\b|\.getAttribute\(/);
});

test('first-flow chain is capped at eight anonymous boxes and never descends into skipped hidden nodes', () => {
  const harness = probeHarness({ computedStyle: { position: 'relative' }, flowDepth: 10000, skippedSiblings: 2 });
  const report = harness.context.CandyDomProbe.sample();
  assert.equal(report.flowStart.length, 8); assert.equal(report.flowStart[0].tag, 'MAIN');
  assert.equal(report.flowStart.every((node) => ['MAIN', 'DIV'].includes(node.tag) && node.y === 0), true);
  assert.ok(harness.counts.reads <= 20);
  const siblings = probeHarness({ computedStyle: { position: 'relative' }, flowDepth: 1, skippedSiblings: 10000 });
  assert.equal(siblings.context.CandyDomProbe.sample().flowStart.length, 0);
  assert.ok(siblings.counts.reads <= 40, 'Sibling inspection must stop after 32 styles');
});

test('manual sample can include optional fixed classifier metadata without changing geometry work', () => {
  const metadata = { active: true, initialized: true, firstReadyState: 'loading', firstAtMillis: 3,
    ownedCount: 1, unknownCount: 2, bodyPending: false, lastBodyDecision: 'applied',
    lastPanelDecision: 'panel-guard-rejected', lastAbsoluteDecision: 'owner-guard-rejected' };
  const harness = probeHarness({ diagnostics: metadata });
  assert.equal(harness.counts.reads, 0);
  assert.deepEqual(JSON.parse(JSON.stringify(harness.context.CandyDomProbe.sample().cssSafeAreaDiagnostics)), metadata);
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

test('owner diagnostics: explicitly sampled active tag is allowlisted and never exports custom author tag', () => {
  for (const [tagName, expected] of [['INPUT', 'INPUT'], ['TEXTAREA', 'TEXTAREA'], ['private-customer-123', 'OTHER']]) {
    const f = probeHarness(); f.context.document.activeElement = {
      tagName,
      get id() { throw new Error('No ID'); }, get className() { throw new Error('No class'); },
      get value() { throw new Error('No input value'); }, get textContent() { throw new Error('No text'); },
    };
    assert.equal(f.context.CandyDomProbe.sample().activeElementTag, expected);
  }
  assert.equal(probeHarness().context.CandyDomProbe.sample().activeElementTag, 'NONE');
});
test('owner diagnostics: active-tag metadata adds no style/rect/hit measurement work', () => {
  const absent = probeHarness(); absent.context.CandyDomProbe.sample();
  const active = probeHarness(); active.context.document.activeElement = { tagName: 'INPUT' };
  active.context.CandyDomProbe.sample(); assert.deepEqual(active.counts, absent.counts);
  assert.deepEqual(active.hits, absent.hits);
});
