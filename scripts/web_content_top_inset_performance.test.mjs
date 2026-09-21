import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import vm from 'node:vm';

const source = readFileSync(new URL(
  '../app/src/main/java/dev/sk2andy/materialbrowser/browser/WebContentTopInsetScript.kt',
  import.meta.url,
), 'utf8');
const script = source
  .split('        """\n')[1]
  .split('\n        """.trimIndent()')[0]
  .replaceAll("${'$'}", '$')
  .replaceAll('$bridgeName', 'CandyContentTopInset');
const closingOffset = script.lastIndexOf('})();');
assert.ok(closingOffset >= 0, 'Generated inset script must retain its outer installation closure');
const observerStart = script.indexOf('observer = new MutationObserver((records) => {');
const observerEnd = script.indexOf('const originalAttachShadow', observerStart);
assert.ok(observerStart >= 0 && observerEnd > observerStart);
const observerBody = script.slice(observerStart, observerEnd)
  .replace(/^observer = new MutationObserver\(\(records\) => \{/, '')
  .replace(/\}\);\s*$/, '');
const testableScript = script.slice(0, closingOffset) + `
  globalThis.layoutReads = {
    invokeMutationObserver(records) {
      const attachShadowHookActive = true;
      const observeOpenShadowRoot = () => {};
      ${observerBody}
    },
    withLayoutReadCache,
    readComputedStyle,
    readElementRect,
    readViewportSize,
    isVisiblePositionedElement,
    findCompactViewportWidePeer,
    invalidateLayoutReadCache,
    parentElementOrShadowHost,
    composedContains,
    deepElementsFromPoint,
    setOwnedProperty,
    setOwnedAttribute,
    removeOwnedProperty,
    removeOwnedAttribute,
    mutationNeedsCandidateDiscovery,
    refreshStickyElements,
    refreshKnownStickyElements,
    jsStickyCount() { return ownedJsStickyElements.size; },
    applyStickyTopAnchor,
    clearOwnedSticky,
    canUseCssStickyAnchor,
    isIdentityTransform,
    revalidateOwnedStickyAnchors,
    mutationTouchesOwnedLayout,
    mutationNeedsImmediateOwnedLayout,
    flushPendingOwnedLayoutMutation,
    planLocalOffset,
    scheduleOwnedMutationLayoutCheck,
    cancelOwnedMutationLayoutCheck,
    setPendingOwnedLayoutMutation(value) { pendingOwnedLayoutMutation = value; },
    ownedLayoutMutationPending() { return pendingOwnedLayoutMutation; },
    styleWithoutCandyProperties,
    isRelevantLayoutMutation,
    windowScrollListener,
    scheduleDeferredLayoutCheck,
    suspendRecovery() { suspendedLayoutRecoveryKey = currentPolicyKey(); },
    setDeferredCheckCount(value) { deferredLayoutChecks = value; },
    protectStickyTopAnchors,
    protectDiscoveredStickyElements,
    topContentBackground,
    scanInsetPoints,
    protectTopInset,
    cancelPointDiscovery,
    invalidatePointDiscovery,
    enqueuePrioritySubtree,
    nextPriorityElement,
    pendingPriorityRoots,
    refreshPriorityCandidates,
    priorityWorkPending() { return pendingPriorityRoots.size > 0; },
    pointDiscoveryPending() { return activePointDiscovery !== null; },
    topInsetProtectionPending() { return activeTopInsetProtection !== null; },
    pointDiscoveryCursor() { return pointDiscoveryProgress?.cursor ?? null; },
    failureCounts() { return [deferredLayoutChecks, consecutiveLayoutFailures, scrollVerificationFailures]; },
    discoveryNeeded() { return candidateDiscoveryNeeded; },
    setDiscoveryNeeded(value) { candidateDiscoveryNeeded = value; },
    setShadowRootObserver(observer) { observeDiscoveredShadowRoot = observer; },
    trackOffsetCandidate(element) { ownedOffsetElements.add(element); },
    trackStickyCandidate(element) { ownedStickyElements.add(element); ownedJsStickyElements.add(element); },
  };
` + script.slice(closingOffset);

function harness() {
  const timers = [];
  const frames = [];
  const calls = { styles: 0, rects: 0, points: 0 };
  const state = {
    version: 1, hits: [], generation: 7, policyRevision: 11, inset: 48,
    clock: 0, pointCostMillis: 5,
  };
  const writes = { propertySets: 0, propertyRemovals: 0, attributeSets: 0, attributeRemovals: 0 };
  const document = {
    documentElement: null,
    body: null,
    elementsFromPoint(x, y) {
      calls.points++;
      state.clock += state.pointCostMillis;
      return state.hitTest ? state.hitTest(x, y) : state.hits;
    },
    querySelectorAll() { return []; },
  };
  class FakeElement {
    constructor({ properties = [], attributes = [], parent = null, computed = {}, rect = null, selectors = [] } = {}) {
      const values = new Map(properties);
      const attributeValues = new Map(attributes);
      this.parentElement = parent;
      this.computed = computed;
      this.rect = rect;
      this.isConnected = true;
      this.selectors = new Set(selectors);
      this.style = {
        getPropertyValue(name) { return values.get(name)?.value || ''; },
        getPropertyPriority(name) { return values.get(name)?.priority || ''; },
        setProperty(name, value, priority) {
          writes.propertySets++;
          state.version++;
          values.set(name, { value, priority });
        },
        removeProperty(name) {
          writes.propertyRemovals++;
          state.version++;
          values.delete(name);
        },
      };
      this.getAttribute = (name) => attributeValues.get(name) ?? null;
      this.hasAttribute = (name) => attributeValues.has(name);
      this.setAttribute = (name, value) => {
        writes.attributeSets++;
        state.version++;
        attributeValues.set(name, value);
      };
      this.removeAttribute = (name) => {
        writes.attributeRemovals++;
        state.version++;
        attributeValues.delete(name);
      };
    }

    getBoundingClientRect() {
      calls.rects++;
      return this.rect ? { ...this.rect } : {
        top: state.version * 10, left: 0, right: 40, bottom: 80, width: 40, height: 70,
      };
    }

    matches(selector) {
      return selector.split(',').some((part) => this.selectors.has(part.trim()));
    }

    querySelector() { return null; }

    contains(element) {
      for (let node = element; node; node = node.parentElement) if (node === this) return true;
      return false;
    }

    getRootNode() { return document; }

    get parentNode() {
      const root = this.getRootNode();
      return this.parentElement ?? (root.host ? root : null);
    }
  }
  const element = {
    getBoundingClientRect() {
      calls.rects++;
      return { top: state.version * 10, left: 0, right: 40, bottom: 80, width: 40, height: 70 };
    },
  };
  const context = vm.createContext({
    document,
    Element: FakeElement,
    CandyContentTopInset: {
      topInsetPx: () => state.inset,
      navigationGeneration: () => state.generation,
      policyRevision: () => state.policyRevision,
    },
    devicePixelRatio: 1,
    innerWidth: 25,
    innerHeight: 800,
    scrollX: 0,
    performance: { now: () => state.clock },
    scrollY: 0,
    requestAnimationFrame(callback) { frames.push(callback); return frames.length; },
    cancelAnimationFrame() {},
    setTimeout(callback) { timers.push(callback); return timers.length; },
    clearTimeout() {},
    getComputedStyle(_element, pseudo) {
      calls.styles++;
      return {
        top: `${state.version}px`, pseudo, position: 'fixed',
        display: 'block', visibility: 'visible', opacity: '1',
        overflowY: 'visible', transform: 'none', translate: 'none',
        transitionDuration: '0s', transitionProperty: 'all',
        animationDuration: '0s', animationName: 'none',
        ..._element.computed,
      };
    },
    MutationObserver: class {
      observe() {}
      disconnect() {}
    },
  });
  vm.runInContext(testableScript, context);
  return {
    reads: context.layoutReads, state, calls, writes, element, document, timers, frames,
    context,
    fakeElement: (options) => new FakeElement(options),
    createDom() {
      document.documentElement = new FakeElement({ computed: { position: 'static' } });
      document.body = new FakeElement({ parent: document.documentElement, computed: { position: 'static' } });
      return new FakeElement({ parent: document.body, computed: { position: 'static' } });
    },
  };
}

function schedulerHarness() {
  const fixture = harness();
  fixture.createDom();
  fixture.document.readyState = 'complete';
  fixture.document.querySelector = () => null;
  fixture.state.inset = 0;
  const timers = new Map();
  const frames = new Map();
  const phases = [];
  let nextId = 0;
  fixture.context.CandyContentTopInset.performanceDiagnosticsEnabled = () => true;
  fixture.context.performance.mark = (name) => phases.push(name);
  fixture.context.performance.measure = () => {};
  fixture.context.performance.clearMarks = () => {};
  fixture.context.performance.clearMeasures = () => {};
  fixture.context.setTimeout = (callback, delay) => {
    const id = ++nextId;
    timers.set(id, { callback, due: fixture.state.clock + delay });
    return id;
  };
  fixture.context.clearTimeout = (id) => timers.delete(id);
  fixture.context.requestAnimationFrame = (callback) => {
    const id = ++nextId;
    frames.set(id, callback);
    return id;
  };
  fixture.context.cancelAnimationFrame = (id) => frames.delete(id);
  return {
    ...fixture,
    scheduledTimers: timers,
    scheduledFrames: frames,
    reconcileCount: () => phases.filter((name) => name === 'Candy.SafeArea.Reconcile.start').length,
    quietProtectionCount: () => phases.filter((name) => name === 'Candy.SafeArea.QuietProtection.start').length,
    verificationCount: () => phases.filter((name) => name === 'Candy.SafeArea.QuietVerification.start').length,
    addedPriorityCount: () => phases.filter((name) => name === 'Candy.SafeArea.AddedPriority.start').length,
    advance(milliseconds) {
      const target = fixture.state.clock + milliseconds;
      for (let count = 0; count < 1000; count++) {
        const next = [...timers].filter(([, timer]) => timer.due <= target)
          .sort((a, b) => a[1].due - b[1].due || a[0] - b[0])[0];
        if (!next) { fixture.state.clock = target; return; }
        fixture.state.clock = next[1].due;
        timers.delete(next[0]);
        next[1].callback();
      }
      assert.fail('Bounded scheduler must not spin');
    },
    flushFrames(before = () => {}) {
      const pending = [...frames.values()];
      frames.clear();
      before();
      pending.forEach((callback) => callback());
    },
  };
}

function addedControlFixture() {
  const fixture = schedulerHarness();
  fixture.state.inset = 48;
  fixture.state.pointCostMillis = 0;
  const wrapper = fixture.fakeElement({
    parent: fixture.document.body, computed: { position: 'static' },
    rect: { top: 10000, left: 0, right: 25, bottom: 10020, width: 25, height: 20 },
  });
  const control = fixture.fakeElement({
    parent: wrapper, computed: { position: 'fixed' }, selectors: ['button'],
  });
  control.getBoundingClientRect = () => {
    fixture.calls.rects++;
    const top = Number.parseFloat(control.style.getPropertyValue('--candy-browser-owned-top-inset-offset')) || 0;
    return { top, left: 12, right: 24, bottom: top + 12, width: 12, height: 12 };
  };
  wrapper.firstElementChild = control;
  const add = () => fixture.reads.invokeMutationObserver([{
    type: 'childList', target: fixture.document.body, addedNodes: [wrapper], removedNodes: [],
  }]);
  return { ...fixture, wrapper, control, add };
}

test('new control in an offscreen wrapper gets added priority protection during uninterrupted scroll', () => {
  const fixture = addedControlFixture();
  fixture.add();
  fixture.advance(100);
  fixture.reads.windowScrollListener();
  fixture.flushFrames();
  assert.equal(fixture.control.getAttribute('data-candy-browser-top-inset-offset'), 'true');
  assert.equal(fixture.control.getBoundingClientRect().top, 56);
  assert.equal(fixture.addedPriorityCount(), 1);
  assert.equal(fixture.reconcileCount(), 0);
  assert.equal(fixture.quietProtectionCount(), 0);
});

test('author frame scrolling replaces added execution state without starving the queued wakeup', () => {
  const fixture = addedControlFixture();
  fixture.add();
  fixture.flushFrames(() => fixture.reads.windowScrollListener());
  assert.equal(fixture.control.getBoundingClientRect().top, 56);
  assert.equal(fixture.addedPriorityCount(), 1);
  assert.equal(fixture.reconcileCount(), 0);
});

test('prompt added frames do not drain unrelated attribute roots or iterate on pure scrolling', () => {
  const fixture = addedControlFixture();
  let attributeReads = 0;
  const computed = { get position() { attributeReads++; return 'static'; } };
  fixture.reads.enqueuePrioritySubtree(fixture.fakeElement({ computed }), false);
  fixture.add();
  fixture.flushFrames();
  assert.equal(fixture.control.getBoundingClientRect().top, 56);
  assert.equal(attributeReads, 0);
  assert.equal(fixture.reads.priorityWorkPending(), true, 'Attribute lane remains for full quiet discovery');
  assert.equal(fixture.scheduledFrames.size, 0);
  for (let scroll = 0; scroll < 3; scroll++) {
    fixture.advance(100);
    fixture.reads.windowScrollListener();
    fixture.flushFrames();
  }
  assert.equal(fixture.addedPriorityCount(), 1);
  assert.equal(attributeReads, 0);
});

test('a previously attribute queued subtree is promoted on addition without rewinding its cursor', () => {
  const fixture = addedControlFixture();
  fixture.reads.enqueuePrioritySubtree(fixture.wrapper, false);
  assert.equal(fixture.reads.nextPriorityElement(), fixture.wrapper);
  fixture.add();
  fixture.flushFrames();
  assert.equal(fixture.control.getBoundingClientRect().top, 56);
  assert.equal(fixture.addedPriorityCount(), 1);
});

test('disabled inset does not spin pending additions through animation frames', () => {
  const fixture = addedControlFixture();
  fixture.state.inset = 0;
  fixture.add();
  fixture.flushFrames();
  assert.equal(fixture.scheduledFrames.size, 0);
  assert.equal(fixture.control.getAttribute('data-candy-browser-top-inset-offset'), null);
});

test('reconfiguration invalidates an already queued added wakeup', () => {
  const fixture = addedControlFixture();
  fixture.add();
  const stale = [...fixture.scheduledFrames.values()][0];
  fixture.state.policyRevision++;
  fixture.state.inset = 0;
  fixture.context.__candyReconfigureContentTopInset();
  fixture.state.inset = 48;
  stale();
  assert.equal(fixture.addedPriorityCount(), 0);
  assert.equal(fixture.control.getAttribute('data-candy-browser-top-inset-offset'), null);
});

test('added priority packets bound style work and read no geometry for normal flow nodes', () => {
  const fixture = addedControlFixture();
  const nodes = Array.from({ length: 17 }, () => fixture.fakeElement({
    parent: fixture.wrapper,
    computed: { get position() { fixture.state.clock++; return 'static'; } },
  }));
  fixture.wrapper.firstElementChild = nodes[0];
  nodes.forEach((node, index) => { node.nextElementSibling = nodes[index + 1] ?? null; });
  fixture.add();
  let packets = 0;
  while (fixture.scheduledFrames.size > 0) {
    assert.ok(packets++ < 20, 'Added lane must complete its finite backlog');
    const styles = fixture.calls.styles;
    fixture.flushFrames();
    assert.ok(fixture.calls.styles - styles <= 5, 'Four-ms packet includes root plus four costly styles');
    assert.equal(fixture.calls.rects, 0);
    assert.equal(fixture.calls.points, 0);
  }
  assert.ok(packets > 1, 'One frame cannot consume all seventeen nodes');
  assert.equal(fixture.reconcileCount(), 0);
});

test('scrolling during an added style read retries candidate identity with fresh geometry', () => {
  const fixture = addedControlFixture();
  let scrollOnce = true;
  fixture.control.computed = { get position() {
    if (scrollOnce) { scrollOnce = false; fixture.reads.windowScrollListener(); }
    return 'fixed';
  } };
  fixture.add();
  fixture.flushFrames();
  assert.equal(fixture.control.getAttribute('data-candy-browser-top-inset-offset'), null);
  assert.equal(fixture.calls.rects, 0, 'Interrupted style classification must not read old geometry');
  fixture.flushFrames();
  assert.equal(fixture.control.getBoundingClientRect().top, 56);
});

test('DOM recovery waits for repeated scrolling to stop and runs once after both quiet deadlines', () => {
  const fixture = schedulerHarness();
  fixture.reads.scheduleDeferredLayoutCheck(true);
  fixture.advance(350);
  fixture.reads.windowScrollListener();
  fixture.advance(350);
  assert.equal(fixture.reconcileCount(), 0);
  fixture.reads.windowScrollListener();
  fixture.advance(399);
  assert.equal(fixture.reconcileCount(), 0);
  fixture.advance(1);
  assert.equal(fixture.reconcileCount(), 1);
  fixture.flushFrames();
  assert.equal(fixture.quietProtectionCount(), 0, 'Full DOM recovery replaces redundant scroll protection');
  fixture.advance(400);
  assert.equal(fixture.reconcileCount(), 1);
});

test('a new DOM request after scrolling postpones the scroll quiet pass until DOM is quiet too', () => {
  const fixture = schedulerHarness();
  fixture.reads.windowScrollListener();
  fixture.advance(350);
  fixture.reads.scheduleDeferredLayoutCheck(true);
  fixture.advance(50);
  fixture.flushFrames();
  assert.equal(fixture.reconcileCount(), 0);
  assert.equal(fixture.quietProtectionCount(), 0);
  fixture.advance(349);
  assert.equal(fixture.reconcileCount(), 0);
  fixture.advance(1);
  assert.equal(fixture.reconcileCount(), 1);
  fixture.flushFrames();
  assert.equal(fixture.quietProtectionCount(), 0);
});

test('a cancelled DOM callback cannot run or disturb its replacement request', () => {
  const fixture = schedulerHarness();
  fixture.reads.scheduleDeferredLayoutCheck(true);
  const stale = [...fixture.scheduledTimers.values()][0].callback;
  fixture.advance(200);
  fixture.reads.scheduleDeferredLayoutCheck(false);
  stale();
  assert.equal(fixture.reconcileCount(), 0);
  fixture.advance(400);
  assert.equal(fixture.reconcileCount(), 1);
});

test('deferred DOM callbacks reject changed roots and policy revisions', () => {
  for (const change of ['root', 'policy']) {
    const fixture = schedulerHarness();
    fixture.reads.scheduleDeferredLayoutCheck(true);
    if (change === 'root') fixture.document.documentElement = fixture.fakeElement();
    else fixture.state.policyRevision++;
    fixture.advance(400);
    assert.equal(fixture.reconcileCount(), 0, change);
  }
});

test('deferred DOM callbacks recheck loading suspension and exhausted recovery budget', () => {
  for (const change of ['loading', 'suspended', 'budget']) {
    const fixture = schedulerHarness();
    fixture.reads.scheduleDeferredLayoutCheck(true);
    if (change === 'loading') fixture.document.readyState = 'loading';
    else if (change === 'suspended') fixture.reads.suspendRecovery();
    else fixture.reads.setDeferredCheckCount(1000);
    fixture.advance(400);
    assert.equal(fixture.reconcileCount(), 0, change);
  }
});

test('a skipped DOM recovery keeps independent scroll stop verification', () => {
  for (const change of ['loading', 'suspended', 'budget']) {
    const fixture = schedulerHarness();
    fixture.reads.scheduleDeferredLayoutCheck(true);
    fixture.reads.windowScrollListener();
    if (change === 'loading') fixture.document.readyState = 'loading';
    else if (change === 'suspended') fixture.reads.suspendRecovery();
    else fixture.reads.setDeferredCheckCount(1000);
    fixture.advance(400);
    assert.equal(fixture.reconcileCount(), 0, change);
    fixture.advance(400);
    assert.equal(fixture.verificationCount(), 1, change);
  }
});

test('skipped scroll recovery reopens actual verification discovery after another pass marked it clean', () => {
  for (const change of ['loading', 'suspended', 'budget']) {
    const fixture = schedulerHarness();
    fixture.state.inset = 48;
    fixture.state.pointCostMillis = 0;
    const style = fixture.fakeElement({ parent: fixture.document.documentElement });
    fixture.document.querySelector = () => style;
    fixture.reads.scheduleDeferredLayoutCheck(false);
    fixture.reads.windowScrollListener();
    fixture.reads.setDiscoveryNeeded(false);
    if (change === 'loading') fixture.document.readyState = 'loading';
    else if (change === 'suspended') fixture.reads.suspendRecovery();
    else fixture.reads.setDeferredCheckCount(1000);
    fixture.advance(400);
    assert.equal(fixture.reconcileCount(), 0, change);
    fixture.advance(400);
    assert.equal(fixture.verificationCount(), 1, change);
    assert.ok(fixture.calls.points > 0, `${change}: verification must actually scan the positive inset`);
    assert.equal(fixture.reads.pointDiscoveryPending(), false, change);
  }
});

test('skipped scroll recovery still requests bounded native fallback for a real hit-test obstruction', () => {
  const fixture = schedulerHarness();
  fixture.state.inset = 48;
  fixture.state.pointCostMillis = 0;
  const style = fixture.fakeElement({ parent: fixture.document.documentElement });
  fixture.document.querySelector = () => style;
  fixture.state.hits = [fixture.fakeElement({
    parent: fixture.document.body,
    computed: { position: 'fixed' },
    rect: { top: 0, left: 0, right: 25, bottom: 20, width: 25, height: 20 },
  })];
  const fallbacks = [];
  fixture.context.CandyContentTopInset.safeAreaRequiredFailureCount = () => 2;
  fixture.context.CandyContentTopInset.fallbackToNative = (...args) => fallbacks.push(args);
  fixture.reads.scheduleDeferredLayoutCheck(false);
  fixture.reads.windowScrollListener();
  fixture.reads.setDiscoveryNeeded(false);
  fixture.reads.setDeferredCheckCount(1000);
  fixture.advance(400);
  fixture.advance(400);
  assert.equal(fixture.verificationCount(), 1);
  assert.deepEqual(fallbacks, [], 'One failed verification is not enough');
  fixture.advance(400);
  assert.equal(fixture.verificationCount(), 2);
  assert.deepEqual(
    fallbacks,
    [[7, 11, null, false]],
    'Fallback retains exact navigation and policy identity',
  );
});

test('successful merged recovery does not reopen discovery after its own clean result', () => {
  const fixture = schedulerHarness();
  fixture.document.querySelector = () => {
    fixture.reads.setDiscoveryNeeded(false);
    return null;
  };
  fixture.reads.scheduleDeferredLayoutCheck(false);
  fixture.reads.windowScrollListener();
  fixture.advance(400);
  assert.equal(fixture.reconcileCount(), 1);
  assert.equal(fixture.reads.discoveryNeeded(), false);
  fixture.state.inset = 48;
  const style = fixture.fakeElement({ parent: fixture.document.documentElement });
  fixture.document.querySelector = () => style;
  fixture.advance(400);
  assert.equal(fixture.verificationCount(), 1);
  assert.equal(fixture.calls.points, 0, 'Successful recovery must not force a redundant verification scan');
});

test('a throwing DOM recovery keeps independent scroll stop verification', () => {
  const fixture = schedulerHarness();
  const failure = new Error('Author query reaction failed');
  let throwOnce = true;
  fixture.document.querySelector = () => {
    if (throwOnce) { throwOnce = false; throw failure; }
    return null;
  };
  fixture.reads.scheduleDeferredLayoutCheck(true);
  fixture.reads.windowScrollListener();
  assert.throws(() => fixture.advance(400), (error) => error === failure);
  assert.equal(fixture.reconcileCount(), 1);
  fixture.advance(400);
  assert.equal(fixture.verificationCount(), 1);
});

test('a cancelled scroll quiet callback stays invalid after its replacement DOM recovery finishes', () => {
  const fixture = schedulerHarness();
  fixture.reads.windowScrollListener();
  const stale = [...fixture.scheduledTimers.values()][0].callback;
  fixture.advance(200);
  fixture.reads.scheduleDeferredLayoutCheck(true);
  fixture.advance(400);
  assert.equal(fixture.reconcileCount(), 1);
  stale();
  fixture.flushFrames();
  assert.equal(fixture.quietProtectionCount(), 0);
  fixture.advance(400);
  assert.equal(fixture.verificationCount(), 1);
});

test('a cancelled verification callback cannot run before its replacement deadline', () => {
  const fixture = schedulerHarness();
  fixture.reads.scheduleDeferredLayoutCheck(true);
  fixture.reads.windowScrollListener();
  fixture.advance(400);
  const stale = [...fixture.scheduledTimers.values()][0].callback;
  fixture.advance(100);
  fixture.reads.scheduleDeferredLayoutCheck(true);
  fixture.advance(400);
  assert.equal(fixture.reconcileCount(), 2);
  stale();
  assert.equal(fixture.verificationCount(), 0);
  fixture.advance(399);
  assert.equal(fixture.verificationCount(), 0);
  fixture.advance(1);
  assert.equal(fixture.verificationCount(), 1);
});

test('a newer DOM request queued inside reconciliation replaces all old completion work', () => {
  const fixture = schedulerHarness();
  let queueOnce = true;
  fixture.document.querySelector = () => {
    if (queueOnce) {
      queueOnce = false;
      fixture.reads.scheduleDeferredLayoutCheck(true);
    }
    return null;
  };
  fixture.reads.scheduleDeferredLayoutCheck(true);
  fixture.reads.windowScrollListener();
  fixture.advance(400);
  assert.equal(fixture.reconcileCount(), 1);
  assert.equal(fixture.scheduledTimers.size, 1, 'Only the newer DOM request survives old completion');
  fixture.advance(400);
  assert.equal(fixture.reconcileCount(), 2);
  assert.equal(fixture.verificationCount(), 0);
  fixture.advance(400);
  assert.equal(fixture.verificationCount(), 1);
});

test('replacement DOM recovery retains discovery authorization without promoting retry only requests', () => {
  for (const discover of [true, false]) {
    const fixture = schedulerHarness();
    fixture.context.__candyReconcileContentTopInset();
    fixture.reads.setDiscoveryNeeded(false);
    fixture.reads.scheduleDeferredLayoutCheck(discover);
    fixture.advance(100);
    fixture.reads.scheduleDeferredLayoutCheck(false);
    fixture.advance(400);
    assert.equal(fixture.reads.discoveryNeeded(), discover);
  }
});

test('reconfiguration invalidates queued DOM quiet and verification callbacks', () => {
  for (const kind of ['DOM', 'quiet', 'verification']) {
    const fixture = schedulerHarness();
    if (kind !== 'quiet') fixture.reads.scheduleDeferredLayoutCheck(true);
    fixture.reads.windowScrollListener();
    if (kind === 'verification') fixture.advance(400);
    const stale = [...fixture.scheduledTimers.values()][0].callback;
    fixture.state.policyRevision++;
    fixture.context.__candyReconfigureContentTopInset();
    const reconciliations = fixture.reconcileCount();
    stale();
    fixture.flushFrames();
    fixture.advance(1200);
    assert.equal(fixture.reconcileCount(), reconciliations, kind);
    assert.equal(fixture.quietProtectionCount(), 0, kind);
    assert.equal(fixture.verificationCount(), 0, kind);
  }
});

test('an early queued DOM callback rechecks the current quiet deadline', () => {
  const fixture = schedulerHarness();
  fixture.reads.scheduleDeferredLayoutCheck(true);
  const early = [...fixture.scheduledTimers.values()][0].callback;
  fixture.advance(100);
  early();
  assert.equal(fixture.reconcileCount(), 0);
  fixture.advance(299);
  assert.equal(fixture.reconcileCount(), 0);
  fixture.advance(1);
  assert.equal(fixture.reconcileCount(), 1);
});

test('actual Kotlin template remains complete valid JavaScript after Gradle substitutions', () => {
  new vm.Script(script);
  assert.ok(!script.includes("${'$'}"));
  assert.ok(!script.includes('$bridgeName'));
});

test('dense discovery yields between point jobs and completes without consuming recovery checks', () => {
  const { reads, document, createDom, timers, calls } = harness();
  createDom();
  const results = [];
  reads.scanInsetPoints(document.documentElement, 24, () => true, (result) => results.push(result));
  assert.equal(calls.points, 0, 'Request must only schedule work');
  assert.equal(reads.pointDiscoveryPending(), true);
  for (let index = 0; index < 16; index++) {
    const before = calls.points;
    timers[index]();
    assert.equal(calls.points - before, 1, 'Each task performs only one document point job');
    assert.deepEqual(Array.from(reads.failureCounts()), [0, 0, 0]);
    assert.deepEqual(results, index < 15 ? [] : [true]);
  }
  assert.equal(reads.pointDiscoveryPending(), false);
});

test('cheap point jobs batch up to eight rather than paying one nested timer per grid coordinate', () => {
  const { reads, document, createDom, timers, calls, state } = harness();
  createDom();
  state.pointCostMillis = 0;
  const results = [];
  let visited = 0;
  reads.scanInsetPoints(document.documentElement, 24, () => { visited++; return true; },
    (result) => results.push(result));
  timers[0]();
  assert.equal(visited, 8);
  assert.ok(calls.points <= 8, 'Synchronous cache may deduplicate seed/raster coordinates');
  assert.deepEqual(results, []);
  timers[1]();
  assert.equal(visited, 16, 'Twelve fresh raster coordinates plus four scheduling-only seeds are visited');
  assert.equal(calls.points, 16, 'Common y1 seeds stay independent of the trailing-first raster');
  assert.deepEqual(results, [true]);
});

test('elapsed native point time limits a chunk before reaching its count cap', () => {
  const { reads, document, createDom, timers, calls, state } = harness();
  createDom();
  state.pointCostMillis = 2;
  reads.scanInsetPoints(document.documentElement, 24, () => true, () => {});
  timers[0]();
  assert.equal(calls.points, 2, 'Two 2ms point jobs consume the 4ms task budget');
});

test('continuous scroll and opaque CSS epochs fairly discover an old later-row control without false completion', () => {
  for (const pointCostMillis of [0, 2]) {
    const { reads, document, createDom, fakeElement, state, context, timers } = harness();
    const parent = createDom();
    context.innerWidth = 852;
    state.pointCostMillis = pointCostMillis;
    const control = fakeElement({ parent, computed: { position: 'static' }, selectors: ['button'] });
    control.getBoundingClientRect = () => {
      const offset = Number.parseFloat(control.style.getPropertyValue('--candy-browser-owned-top-inset-offset')) || 0;
      return { top: 36 + offset, bottom: 38 + offset, left: 829, right: 831, width: 2, height: 2 };
    };
    state.hitTest = (x, y) => {
      const rect = control.getBoundingClientRect();
      return control.computed.position === 'fixed' &&
        x >= rect.left && x < rect.right && y >= rect.top && y < rect.bottom ? [control] : [];
    };
    const results = [];
    let nextTimer = 0;
    let protectedEpoch = null;
    for (let epoch = 0; epoch < 12; epoch++) {
      context.scrollY += 20;
      reads.invalidatePointDiscovery();
      const leaf = fakeElement({ parent, computed: { position: 'static' } });
      assert.equal(reads.mutationNeedsCandidateDiscovery({
        type: 'childList', target: document.body, addedNodes: [leaf], removedNodes: [],
      }), true, 'A body:has leaf may change an old control outside the added subtree');
      control.computed.position = 'fixed';
      reads.enqueuePrioritySubtree(leaf);
      reads.invalidatePointDiscovery();
      state.clock = epoch * 550 + 400;
      const nextScrollAt = epoch * 550 + 550;
      reads.protectTopInset(document.documentElement, document.body, null, 48, true, false,
        (result) => results.push(result));
      let freeTimers = 5;
      while (nextTimer < timers.length) {
        const delay = freeTimers > 0 ? 0 : 4;
        if (state.clock + delay >= nextScrollAt) break;
        state.clock += delay;
        freeTimers--;
        timers[nextTimer++]();
      }
      if (control.hasAttribute('data-candy-browser-top-inset-offset') && protectedEpoch === null) {
        protectedEpoch = epoch + 1;
      }
      assert.deepEqual(results, [], 'Cancelled partial epochs must never accumulate a completed safety proof');
      assert.deepEqual(Array.from(reads.failureCounts()), [0, 0, 0]);
      nextTimer = timers.length;
    }
    assert.notEqual(protectedEpoch, null, `Later-row control must be discovered across epochs with ${pointCostMillis}ms atomic hit-tests`);
    assert.ok(protectedEpoch > 1, 'Fixture exercises rotation, not initial seeds');
    assert.equal(control.getBoundingClientRect().top, 56);
  }
});

test('trailing-row priority discovers an old control with shared portioned sticky bootstrap', () => {
  const { reads, document, createDom, fakeElement, state, context, timers } = harness();
  const parent = createDom();
  context.innerWidth = 360;
  state.inset = 32;
  state.pointCostMillis = 4;
  const control = fakeElement({ parent, computed: { position: 'static' }, selectors: ['button'] });
  control.getBoundingClientRect = () => {
    const offset = Number.parseFloat(control.style.getPropertyValue('--candy-browser-owned-top-inset-offset')) || 0;
    return { top: 30 + offset, bottom: 32 + offset, left: 332, right: 344, width: 12, height: 2 };
  };
  let trailingControlHits = 0;
  state.hitTest = (x, y) => {
    const rect = control.getBoundingClientRect();
    const hit = control.computed.position === 'fixed' &&
      x >= rect.left && x < rect.right && y >= rect.top && y < rect.bottom;
    if (hit && x === 337 && y === 31) trailingControlHits++;
    return hit ? [control] : [];
  };
  const trigger = fakeElement({ parent, computed: { position: 'static' } });
  assert.equal(reads.mutationNeedsCandidateDiscovery({
    type: 'childList', target: document.body, addedNodes: [trigger], removedNodes: [],
  }), true);
  control.computed.position = 'fixed';
  reads.enqueuePrioritySubtree(trigger);
  let nextTimer = 0;
  let protectedEpoch = null;
  const results = [];
  for (let epoch = 0; epoch < 12; epoch++) {
    context.scrollY = epoch % 2 === 0 ? 100 : 200;
    reads.invalidatePointDiscovery();
    reads.setDiscoveryNeeded(true);
    state.clock = epoch * 550 + 400;
    // Known-header repair must not consume a separate synchronous point-query budget.
    reads.protectStickyTopAnchors(document.documentElement, 32);
    reads.protectTopInset(document.documentElement, document.body, null, 32, true, true,
      (result) => results.push(result));
    let freeTimers = 5;
    while (nextTimer < timers.length) {
      const delay = freeTimers > 0 ? 0 : 4;
      if (state.clock + delay >= epoch * 550 + 550) break;
      state.clock += delay;
      freeTimers--;
      timers[nextTimer++]();
    }
    if (control.hasAttribute('data-candy-browser-top-inset-offset') && protectedEpoch === null) {
      protectedEpoch = epoch + 1;
    }
    assert.deepEqual(results, [], 'A short quiet window never completes a whole fresh matrix');
    assert.deepEqual(Array.from(reads.failureCounts()), [0, 0, 0]);
    nextTimer = timers.length;
  }
  assert.notEqual(protectedEpoch, null, 'Existing bottom-edge control must be reached despite bootstrap cost');
  assert.ok(trailingControlHits > 0, 'Fixture must hit the real x337,y31 coordinate, not a common y1 seed');
  assert.equal(control.getBoundingClientRect().top, 40);
});

test('unknown sticky bootstrap performs no separate synchronous grid or rectangle scan', () => {
  const { reads, document, createDom, calls } = harness();
  createDom();
  reads.protectStickyTopAnchors(document.documentElement, 48);
  assert.equal(calls.points, 0);
  assert.equal(calls.rects, 0);
  assert.equal(calls.styles, 0);
});

test('sticky discovery crosses absolute children and Shadow hosts but retains a fixed barrier', () => {
  for (const inShadow of [false, true]) {
    const { reads, document, createDom, fakeElement, calls } = harness();
    const parent = createDom();
    const header = fakeElement({ parent, computed: { position: 'sticky', top: '0px' } });
    const child = fakeElement({ parent: inShadow ? null : header, computed: { position: 'absolute' } });
    if (inShadow) child.getRootNode = () => ({ host: header });
    assert.equal(reads.withLayoutReadCache(() =>
      reads.protectDiscoveredStickyElements([child], document.documentElement, 48)), true);
    assert.equal(header.hasAttribute('data-candy-browser-top-inset-sticky'), true);
    assert.equal(calls.rects, 0);
  }
  const { reads, document, createDom, fakeElement } = harness();
  const parent = createDom();
  const header = fakeElement({ parent, computed: { position: 'sticky', top: '0px' } });
  const fixedChild = fakeElement({ parent: header, computed: { position: 'fixed' } });
  assert.equal(reads.protectDiscoveredStickyElements([fixedChild], document.documentElement, 48), false);
  assert.equal(header.hasAttribute('data-candy-browser-top-inset-sticky'), false);
});

test('sticky boundary samples delay a common first-row control seed without discarding it', () => {
  const { reads, document, createDom, fakeElement, state, context, timers } = harness();
  const parent = createDom();
  context.innerWidth = 360;
  state.pointCostMillis = 4;
  const control = fakeElement({ parent, computed: { position: 'fixed' }, selectors: ['button'] });
  control.getBoundingClientRect = () => {
    const offset = Number.parseFloat(control.style.getPropertyValue('--candy-browser-owned-top-inset-offset')) || 0;
    return { top: offset, bottom: 2 + offset, left: 84, right: 87, width: 3, height: 2 };
  };
  let seedHit = false;
  state.hitTest = (x, y) => {
    if (x === 85 && y === 1 && !control.hasAttribute('data-candy-browser-top-inset-offset')) {
      seedHit = true;
      return [control];
    }
    return [];
  };
  reads.protectTopInset(document.documentElement, document.body, null, 48, true, true);
  let next = 0;
  while (!seedHit && next < timers.length && next < 20) timers[next++]();
  assert.equal(seedHit, true, 'Boundary sampling must not discard a first-row seed');
  assert.equal(control.hasAttribute('data-candy-browser-top-inset-offset'), true);
});

test('owned writes preserve task-local viewport dimensions but the next task reads fresh dimensions', () => {
  const { reads, context, element } = harness();
  let width = 360;
  let height = 800;
  let widthReads = 0;
  let heightReads = 0;
  Object.defineProperty(context, 'innerWidth', { configurable: true, get() { widthReads++; return width; } });
  Object.defineProperty(context, 'innerHeight', { configurable: true, get() { heightReads++; return height; } });
  const candidate = { ...element, style: {
    getPropertyValue() { return ''; }, getPropertyPriority() { return ''; }, setProperty() {},
  } };
  reads.withLayoutReadCache(() => {
    assert.equal(reads.readViewportSize().width, 360);
    reads.setOwnedProperty(candidate, '--candy-test', '1px');
    assert.equal(reads.readViewportSize().height, 800);
    assert.equal(reads.readViewportSize().width, 360);
  });
  assert.equal(widthReads, 1);
  assert.equal(heightReads, 1);
  width = 980;
  height = 500;
  reads.withLayoutReadCache(() => {
    assert.equal(reads.readViewportSize().width, 980);
    assert.equal(reads.readViewportSize().height, 500);
  });
  assert.equal(widthReads, 2);
  assert.equal(heightReads, 2);
});

test('style-only read epochs do not read viewport geometry or force unrelated mutation layout', () => {
  const { reads, context, element } = harness();
  let viewportReads = 0;
  Object.defineProperty(context, 'innerWidth', { configurable: true, get() { viewportReads++; return 360; } });
  Object.defineProperty(context, 'innerHeight', { configurable: true, get() { viewportReads++; return 800; } });
  reads.withLayoutReadCache(() => reads.readComputedStyle(element));
  assert.equal(viewportReads, 0, 'Pure policy/style epochs must not request layout viewport values');
});

test('empty write-only and viewport-only epochs allocate no read collections', () => {
  const { reads, context, fakeElement } = harness();
  const allocations = { maps: 0, weakMaps: 0 };
  context.Map = class extends Map { constructor(...args) { super(...args); allocations.maps++; } };
  context.WeakMap = class extends WeakMap { constructor(...args) { super(...args); allocations.weakMaps++; } };
  const candidate = fakeElement();
  reads.withLayoutReadCache(() => {});
  reads.withLayoutReadCache(() => reads.setOwnedProperty(candidate, '--candy-test', '1px'));
  reads.withLayoutReadCache(() => {
    reads.readViewportSize();
    reads.invalidateLayoutReadCache();
    reads.readViewportSize();
  });
  assert.deepEqual(allocations, { maps: 0, weakMaps: 0 });
});

test('each reader lazily allocates only its own collection and reuses null empty and pseudo results', () => {
  for (const kind of ['style', 'rect', 'parent', 'point']) {
    const { reads, context, element, calls } = harness();
    const allocations = { maps: 0, weakMaps: 0 };
    context.Map = class extends Map { constructor(...args) { super(...args); allocations.maps++; } };
    context.WeakMap = class extends WeakMap { constructor(...args) { super(...args); allocations.weakMaps++; } };
    const read = () => {
      if (kind === 'style') return reads.readComputedStyle(element);
      if (kind === 'rect') return reads.readElementRect(element);
      if (kind === 'parent') return reads.parentElementOrShadowHost(element);
      return reads.deepElementsFromPoint(1, 1);
    };
    reads.withLayoutReadCache(() => {
      const first = read();
      assert.equal(read(), first);
      if (kind === 'style') {
        const pseudo = reads.readComputedStyle(element, '::before');
        assert.notEqual(pseudo, first);
        assert.equal(reads.readComputedStyle(element, '::before'), pseudo);
        assert.equal(calls.styles, 2);
      }
      const expected = { maps: kind === 'style' || kind === 'point' ? 1 : 0,
        weakMaps: kind === 'point' ? 0 : 1 };
      assert.deepEqual(allocations, expected, kind);
      reads.invalidateLayoutReadCache();
      assert.deepEqual(allocations, expected, 'Invalidation itself must allocate nothing');
      read();
      assert.deepEqual(allocations, { maps: expected.maps * 2, weakMaps: expected.weakMaps * 2 });
    });
  }
});

test('reentrant rectangle and parent reads return outer values without replacing fresh inner cache', () => {
  for (const kind of ['rect', 'parent']) {
    const { reads, fakeElement } = harness();
    const element = fakeElement();
    const outer = kind === 'rect' ? { top: 10 } : fakeElement();
    const inner = kind === 'rect' ? { top: 20 } : fakeElement();
    let entered = false;
    let queried = 0;
    const read = () => kind === 'rect' ? reads.readElementRect(element) : reads.parentElementOrShadowHost(element);
    const getter = () => {
      queried++;
      if (entered) return inner;
      entered = true;
      reads.invalidateLayoutReadCache();
      assert.equal(read(), inner);
      return outer;
    };
    if (kind === 'rect') element.getBoundingClientRect = getter;
    else Object.defineProperty(element, 'parentElement', { get: getter });
    reads.withLayoutReadCache(() => {
      assert.equal(read(), outer, 'Outer call returns the actual local read');
      assert.equal(read(), inner, 'Old publication must not poison the replacement collection');
      assert.equal(queried, 2);
    });
  }
});

test('reentrant point read cannot publish obsolete hits over a replacement collection', () => {
  const { reads, state, fakeElement } = harness();
  const outer = fakeElement(), inner = fakeElement();
  let entered = false;
  state.hitTest = () => {
    if (entered) return [inner];
    entered = true;
    reads.invalidateLayoutReadCache();
    assert.equal(reads.deepElementsFromPoint(1, 1)[0], inner);
    return [outer];
  };
  reads.withLayoutReadCache(() => {
    assert.equal(reads.deepElementsFromPoint(1, 1)[0], outer);
    assert.equal(reads.deepElementsFromPoint(1, 1)[0], inner);
  });
});

test('reentrant style read cannot publish obsolete values over a replacement collection', () => {
  const { reads, context, element } = harness();
  const outer = { top: '10px' }, inner = { top: '20px' };
  let entered = false;
  context.getComputedStyle = () => {
    if (entered) return inner;
    entered = true;
    reads.invalidateLayoutReadCache();
    assert.equal(reads.readComputedStyle(element), inner);
    return outer;
  };
  reads.withLayoutReadCache(() => {
    assert.equal(reads.readComputedStyle(element), outer);
    assert.equal(reads.readComputedStyle(element), inner);
  });
});

test('reentrant first viewport read does not overwrite fresh dimensions populated during an owned write', () => {
  const { reads, context, fakeElement } = harness();
  const candidate = fakeElement();
  let entered = false;
  Object.defineProperty(context, 'innerWidth', { configurable: true, get() {
    if (entered) return 980;
    entered = true;
    reads.setOwnedProperty(candidate, '--candy-test', '1px');
    assert.equal(reads.readViewportSize().width, 980);
    return 360;
  } });
  reads.withLayoutReadCache(() => {
    assert.equal(reads.readViewportSize().width, 360);
    assert.equal(reads.readViewportSize().width, 980);
  });
});

test('style-hidden positioned elements skip rectangle reads while visible elements retain geometry validation', () => {
  const { reads, fakeElement, calls } = harness();
  for (const computed of [
    { display: 'none' }, { visibility: 'hidden' }, { visibility: 'collapse' },
    { opacity: '0.01' }, { opacity: '0' }, { opacity: 'invalid' },
  ]) {
    assert.equal(reads.isVisiblePositionedElement(fakeElement({ computed })), false);
  }
  assert.equal(calls.rects, 0, 'Style-hidden elements require no layout rectangle');
  assert.equal(reads.isVisiblePositionedElement(fakeElement({
    rect: { width: 0, height: 70 },
  })), false);
  assert.equal(calls.rects, 1, 'Visible elements must still validate physical dimensions');
});

test('existing background hit anchors CSS sticky before owned-style mutations cancel queued discovery', () => {
  const { reads, document, createDom, fakeElement, state, calls, context } = harness();
  const parent = createDom();
  context.innerWidth = 360;
  document.querySelectorAll = () => [];
  const header = fakeElement({ parent, computed: {
    position: 'sticky', top: '0px', backgroundColor: 'rgb(255, 255, 255)',
  } });
  state.hitTest = (x, y) => x === 180 && y === 49 ? [header] : [];
  reads.setDiscoveryNeeded(true);
  reads.withLayoutReadCache(() => reads.topContentBackground(document.documentElement, 48));
  reads.cancelPointDiscovery();
  assert.equal(header.hasAttribute('data-candy-browser-top-inset-sticky'), true,
    'A canceled discovery must not leave an initially visible CSS sticky header unanchored');
  assert.equal(calls.points, 1, 'Reuse the existing first background query, not an extra synchronous grid');
  assert.equal(calls.rects, 0, 'Early registration must remain style-only');
});

test('background hits retain early sequential anchoring for a transitioning TapTap sticky header', () => {
  const { reads, document, createDom, fakeElement, state, calls, context } = harness();
  const parent = createDom();
  context.innerWidth = 360;
  document.querySelectorAll = () => [];
  const header = fakeElement({ parent, computed: {
    position: 'sticky', top: '0px', backgroundColor: 'rgb(255, 255, 255)',
    transitionProperty: 'top', transitionDuration: '200ms',
  } });
  state.hitTest = (x, y) => x === 180 && y === 49 ? [header] : [];
  reads.withLayoutReadCache(() => reads.topContentBackground(document.documentElement, 48));
  reads.cancelPointDiscovery();
  assert.equal(header.hasAttribute('data-candy-browser-top-inset-sticky'), true);
  assert.equal(calls.points, 1);
  assert.equal(header.style.getPropertyValue('--candy-browser-owned-sticky-top'), '48px',
    'Author motion retains numeric sequential anchoring rather than static CSS classification');
});

test('expensive fresh viewport validation still advances one discovery point per packet', () => {
  const { reads, document, createDom, context, state, calls, timers } = harness();
  createDom();
  Object.defineProperty(context, 'innerWidth', { configurable: true, get() {
    state.clock += 5;
    return 25;
  } });
  reads.scanInsetPoints(document.documentElement, 24, () => true, () => {});
  timers[0]();
  assert.equal(calls.points, 1, 'Slow native validation must not cause zero-progress rescheduling');
  timers[1]();
  assert.equal(calls.points, 2, 'Fresh validation remains mandatory on the next packet');
});

test('shared discovery anchors a just-below-inset CSS sticky header without rectangle reads or false completion', () => {
  const { reads, document, createDom, fakeElement, state, calls, context, timers } = harness();
  const parent = createDom();
  context.innerWidth = 360;
  state.pointCostMillis = 5;
  const header = fakeElement({ parent, computed: { position: 'sticky', top: '0px' } });
  state.hitTest = (_x, y) => y === 49 ? [header] : [];
  const results = [];
  reads.protectTopInset(document.documentElement, document.body, null, 48, true, true,
    (result) => results.push(result));
  assert.ok(calls.points <= 1, 'One expensive point must yield, not accumulate the sticky grid');
  assert.equal(header.hasAttribute('data-candy-browser-top-inset-sticky'), false);
  let next = 0;
  while (!header.hasAttribute('data-candy-browser-top-inset-sticky') && next < timers.length && next < 100) {
    const before = calls.points;
    timers[next++]();
    assert.ok(calls.points - before <= 1, 'Sticky points share the real cooperative budget');
  }
  assert.equal(header.hasAttribute('data-candy-browser-top-inset-sticky'), true);
  assert.equal(calls.rects, 0, 'Stable viewport CSS sticky registration requires no rectangle');
  assert.deepEqual(results, [], 'Header registration does not substitute for full dense safety proof');
  assert.deepEqual(Array.from(reads.failureCounts()), [0, 0, 0]);
  reads.invalidatePointDiscovery();
  assert.deepEqual(results, []);
});

test('one atomic slot per cancelled epoch alternates seeds and raster without retaining safety proofs', () => {
  const { reads, document, createDom, fakeElement, state, context } = harness();
  const parent = createDom();
  context.innerWidth = 360;
  state.inset = 32;
  state.pointCostMillis = 5;
  const control = fakeElement({ parent, computed: { position: 'fixed' }, selectors: ['button'] });
  control.getBoundingClientRect = () => {
    const offset = Number.parseFloat(control.style.getPropertyValue('--candy-browser-owned-top-inset-offset')) || 0;
    return { top: 30 + offset, bottom: 32 + offset, left: 332, right: 344, width: 12, height: 2 };
  };
  let exactHits = 0;
  state.hitTest = (x, y) => {
    const rect = control.getBoundingClientRect();
    const hit = x >= rect.left && x < rect.right && y >= rect.top && y < rect.bottom;
    if (hit && x === 337 && y === 31) exactHits++;
    return hit ? [control] : [];
  };
  const results = [];
  let protectedEpoch = null;
  for (let epoch = 0; epoch < 64; epoch++) {
    context.scrollY = epoch % 2 === 0 ? 100 : 200;
    reads.invalidatePointDiscovery();
    reads.protectTopInset(document.documentElement, document.body, null, 32, true, true,
      (result) => results.push(result));
    // The first atomic job exceeds the 4ms chunk budget; no continuation fits before cancellation.
    if (control.hasAttribute('data-candy-browser-top-inset-offset') && protectedEpoch === null) {
      protectedEpoch = epoch + 1;
    }
    assert.deepEqual(results, [], 'Cross-epoch scheduling hints cannot accumulate safety coverage');
    assert.deepEqual(Array.from(reads.failureCounts()), [0, 0, 0]);
  }
  assert.notEqual(protectedEpoch, null, 'Dense raster must progress even when every epoch has only one point slot');
  assert.ok(exactHits > 0);
  assert.equal(control.getBoundingClientRect().top, 40);
});

test('scheduling cursor survives scroll and author invalidation but resets for root policy and grid changes', () => {
  for (const change of [
    (f) => f.state.generation++,
    (f) => f.state.policyRevision++,
    (f) => f.state.inset++,
    (f) => f.context.devicePixelRatio = 2,
    (f) => f.context.innerWidth++,
    (f) => f.context.innerHeight++,
    (f) => f.createDom(),
  ]) {
    const f = harness();
    f.createDom();
    f.reads.scanInsetPoints(f.document.documentElement, 24, () => true, () => {});
    f.timers[0]();
    f.timers[1]();
    assert.equal(f.reads.pointDiscoveryCursor(), 1);
    f.reads.invalidatePointDiscovery();
    f.context.scrollY++;
    f.reads.scanInsetPoints(f.document.documentElement, 24, () => true, () => {});
    assert.equal(f.reads.pointDiscoveryCursor(), 1, 'Scroll invalidates geometry, not the scheduling hint');
    f.reads.invalidatePointDiscovery();
    change(f);
    f.reads.scanInsetPoints(f.document.documentElement, 24, () => true, () => {});
    assert.equal(f.reads.pointDiscoveryCursor(), 0, 'New root/policy/grid starts with no old hint');
  }
  assert.match(source, /runtimeState\.dispose = \(\) => \{[\s\S]*?pointDiscoveryProgress = null;/,
    'Disposal must release the retained root and scheduling cursor');
});

test('a coalesced recovery-authorized request upgrades completion without double counting', () => {
  const { reads, document, createDom, state, timers, calls } = harness();
  const flow = createDom();
  state.hitTest = () => calls.points >= 2 ? [flow] : [];
  const results = [];
  reads.protectTopInset(document.documentElement, document.body, null, 48, false, false,
    () => results.push('non-suspending'), false);
  reads.protectTopInset(document.documentElement, document.body, null, 48, false, false,
    (result) => results.push(['recovery', result]), true);
  assert.equal(timers.length, 1, 'Same snapshot must keep one discovery job');
  timers[0]();
  assert.deepEqual(results, [['recovery', false]]);
});

test('a throwing hit-test or visitor cancels the active job and permits replacement discovery', () => {
  for (const throwInHitTest of [true, false]) {
    const { reads, document, createDom, timers, state } = harness();
    createDom();
    if (throwInHitTest) state.hitTest = () => { throw new Error('site hit-test failed'); };
    reads.scanInsetPoints(document.documentElement, 24, () => {
      if (!throwInHitTest) throw new Error('site visitor failed');
      return true;
    }, () => {});
    assert.throws(() => timers[0](), /site .* failed/);
    assert.equal(reads.pointDiscoveryPending(), false);
    assert.equal(reads.discoveryNeeded(), true);
    assert.deepEqual(Array.from(reads.failureCounts()), [0, 0, 0]);
    state.hitTest = null;
    reads.scanInsetPoints(document.documentElement, 24, () => true, () => {});
    timers[1]();
    assert.equal(reads.pointDiscoveryPending(), true);
  }
});

test('a terminal positioned-plan exception releases protection so the same snapshot can retry', () => {
  const { reads, document, createDom, fakeElement, timers, state } = harness();
  const parent = createDom();
  let terminal = false;
  const computed = {};
  Object.defineProperty(computed, 'position', {
    enumerable: true,
    get() {
      if (terminal) throw new Error('terminal positioned style failed');
      return 'fixed';
    },
  });
  const control = fakeElement({
    parent, computed,
    rect: { top: 0, bottom: 20, left: 0, right: 25, width: 25, height: 20 },
  });
  reads.trackOffsetCandidate(control);
  state.hits = [control];
  document.querySelectorAll = () => {
    terminal = true;
    reads.invalidateLayoutReadCache();
    return [];
  };
  reads.protectTopInset(document.documentElement, document.body, null, 48, true);
  let nextTimer = 0;
  let caught = false;
  while (reads.pointDiscoveryPending() && !caught) {
    assert.ok(nextTimer < 100);
    try {
      timers[nextTimer++]();
    } catch (error) {
      assert.match(error.message, /terminal positioned style failed/);
      caught = true;
    }
  }
  assert.equal(caught, true, 'Fixture must reach the terminal positioned-plan phase');
  assert.equal(reads.topInsetProtectionPending(), false);
  assert.equal(reads.discoveryNeeded(), true);
  assert.deepEqual(Array.from(reads.failureCounts()), [0, 0, 0]);
  terminal = false;
  state.hits = [];
  document.querySelectorAll = () => [];
  const results = [];
  reads.protectTopInset(document.documentElement, document.body, null, 48, true, false,
    (result) => results.push(result));
  assert.equal(reads.topInsetProtectionPending(), true, 'Identical signature must create a fresh operation');
  while (nextTimer < timers.length) timers[nextTimer++]();
  assert.deepEqual(results, [true]);
});

test('a terminal exception cannot clear a newer replacement protection operation', () => {
  const { reads, document, createDom, fakeElement, timers, state } = harness();
  const parent = createDom();
  const control = fakeElement({
    parent, computed: { position: 'fixed' },
    rect: { top: 0, bottom: 20, left: 0, right: 25, width: 25, height: 20 },
  });
  reads.trackOffsetCandidate(control);
  state.hits = [control];
  const results = [];
  document.querySelectorAll = () => {
    reads.invalidatePointDiscovery();
    state.hits = [];
    reads.protectTopInset(document.documentElement, document.body, null, 48, true, false,
      (result) => results.push(result));
    throw new Error('old terminal callback failed');
  };
  reads.protectTopInset(document.documentElement, document.body, null, 48, true);
  let nextTimer = 0;
  let caught = false;
  while (!caught && nextTimer < timers.length) {
    assert.ok(nextTimer < 100);
    try {
      timers[nextTimer++]();
    } catch (error) {
      assert.match(error.message, /old terminal callback failed/);
      caught = true;
    }
  }
  assert.equal(caught, true);
  assert.equal(reads.pointDiscoveryPending(), true);
  assert.equal(reads.topInsetProtectionPending(), true);
  document.querySelectorAll = () => [];
  while (nextTimer < timers.length) timers[nextTimer++]();
  assert.deepEqual(results, [true]);
  assert.equal(reads.topInsetProtectionPending(), false);
});

test('discovery chunks cannot retain computed style or rectangle snapshots across yields', () => {
  const { reads, document, createDom, timers, state, element } = harness();
  createDom();
  const snapshots = [];
  reads.scanInsetPoints(document.documentElement, 24, () => {
    snapshots.push([reads.readComputedStyle(element).top, reads.readElementRect(element).top]);
    return true;
  }, () => {});
  timers[0]();
  state.version = 3;
  timers[1]();
  assert.deepEqual(snapshots, [['1px', 10], ['3px', 30]]);
});

test('policy navigation viewport and inset changes cancel partial discovery without failures', () => {
  for (const change of [
    (fixture) => fixture.state.policyRevision++,
    (fixture) => fixture.state.generation++,
    (fixture) => fixture.state.inset++,
    (fixture) => fixture.context.innerWidth++,
    (fixture) => fixture.context.scrollY++,
    (fixture) => { fixture.document.documentElement = fixture.fakeElement(); },
  ]) {
    const fixture = harness();
    fixture.createDom();
    const results = [];
    fixture.reads.scanInsetPoints(fixture.document.documentElement, 24, () => true,
      (result) => results.push(result));
    fixture.timers[0]();
    change(fixture);
    fixture.timers[1]();
    assert.equal(fixture.calls.points, 1);
    assert.equal(fixture.reads.pointDiscoveryPending(), false);
    assert.equal(fixture.reads.discoveryNeeded(), true);
    assert.deepEqual(results, []);
    assert.deepEqual(Array.from(fixture.reads.failureCounts()), [0, 0, 0]);
  }
});

test('a Candy write cancels old geometry and stale callback cannot clear a replacement job', () => {
  const { reads, document, createDom, timers, fakeElement, calls } = harness();
  createDom();
  const results = [];
  reads.scanInsetPoints(document.documentElement, 24, () => true, () => results.push('old'));
  timers[0]();
  reads.setOwnedProperty(fakeElement(), 'top', '48px');
  timers[1]();
  assert.equal(reads.pointDiscoveryPending(), false);
  reads.scanInsetPoints(document.documentElement, 24, () => true, () => results.push('new'));
  timers[1]();
  assert.equal(reads.pointDiscoveryPending(), true);
  timers[2]();
  assert.equal(calls.points, 2);
  assert.deepEqual(results, []);
});

test('explicit mutation cancellation retains dirty state and cannot consume failure confirmations', () => {
  const { reads, document, createDom, timers, calls } = harness();
  createDom();
  const results = [];
  reads.scanInsetPoints(document.documentElement, 24, () => true, (result) => results.push(result));
  timers[0]();
  reads.invalidatePointDiscovery();
  timers[1]();
  assert.equal(calls.points, 1);
  assert.deepEqual(results, []);
  assert.deepEqual(Array.from(reads.failureCounts()), [0, 0, 0]);
  assert.equal(reads.discoveryNeeded(), true);
});

test('failed point validation completes once rather than counting every pending chunk as failure', () => {
  const { reads, document, createDom, timers, calls } = harness();
  createDom();
  const results = [];
  reads.scanInsetPoints(document.documentElement, 24, () => calls.points < 3,
    (result) => results.push(result));
  timers[0]();
  timers[1]();
  assert.deepEqual(results, []);
  timers[2]();
  assert.deepEqual(results, [false]);
  assert.equal(reads.pointDiscoveryPending(), false);
  assert.deepEqual(Array.from(reads.failureCounts()), [0, 0, 0]);
});

test('retained nested Shadow roots keep discovery coverage after each yielded point job', () => {
  const { reads, document, createDom, timers, state, calls } = harness();
  createDom();
  let shadowQueries = 0;
  const leaf = {};
  const host = { shadowRoot: { elementsFromPoint() { shadowQueries++; return [leaf]; } } };
  state.hits = [host];
  const hits = [];
  reads.scanInsetPoints(document.documentElement, 24, (elements) => {
    hits.push(Array.from(elements));
    return true;
  }, () => {});
  timers[0]();
  timers[1]();
  assert.equal(calls.points, 2);
  assert.equal(shadowQueries, 2, 'Scope descent remains part of each atomic point job');
  assert.deepEqual(hits, [[leaf, host], [leaf, host]]);
});

test('opaque structural CSS still invalidates broad discovery rather than acquiring a once-safe flag', () => {
  const { reads, fakeElement, createDom, document } = harness();
  const parent = createDom();
  const existing = fakeElement({ parent, computed: { position: 'static' } });
  assert.equal(reads.mutationNeedsCandidateDiscovery({
    type: 'childList', target: document.body, addedNodes: [existing], removedNodes: [],
  }), true, 'Body child insertion can change :has/nth-child rules on old static nodes');
  const stylesheet = fakeElement({ parent, selectors: ['style'] });
  assert.equal(reads.mutationNeedsCandidateDiscovery({
    type: 'childList', target: stylesheet, addedNodes: [{}], removedNodes: [],
  }), true);
});

test('chunked protection still discovers a tiny fixed control inside an offscreen wrapper', () => {
  const { reads, document, createDom, fakeElement, state, timers } = harness();
  const parent = createDom();
  const wrapper = fakeElement({
    parent, computed: { position: 'static' },
    rect: { top: 4000, bottom: 4100, left: 0, right: 25, width: 25, height: 100 },
  });
  const tiny = fakeElement({ parent: wrapper, computed: { position: 'fixed' }, selectors: ['button'] });
  tiny.getBoundingClientRect = () => {
    const top = Number.parseFloat(tiny.style.getPropertyValue('--candy-browser-owned-top-inset-offset')) || 0;
    return { top, bottom: top + 2, left: 1, right: 3, width: 2, height: 2 };
  };
  state.hitTest = (x, y) => {
    const rect = tiny.getBoundingClientRect();
    return x >= rect.left && x < rect.right && y >= rect.top && y < rect.bottom ? [tiny] : [];
  };
  const results = [];
  reads.protectTopInset(document.documentElement, document.body, null, 48, true, false,
    (result) => results.push(result));
  assert.equal(tiny.getBoundingClientRect().top, 56,
    'Initial contextual hit must protect this control synchronously after fresh peer validation');
  assert.deepEqual(results, [], 'Early target protection does not claim completed global verification');
  for (let index = 0; index < timers.length; index++) {
    assert.ok(index < 100, 'Fixture must finish within bounded number of tasks');
    timers[index]();
  }
  assert.deepEqual(results, [true]);
  assert.equal(tiny.getBoundingClientRect().top, 56, 'Compact control retains safe inset plus 8px padding');
  assert.equal(tiny.hasAttribute('data-candy-browser-top-inset-offset'), true);
});

test('added subtree priority protects a tiny fixed control between grid coordinates before full scan completion', () => {
  const { reads, document, createDom, fakeElement, state, timers } = harness();
  const parent = createDom();
  const wrapper = fakeElement({ parent, computed: { position: 'static' } });
  const tiny = fakeElement({ parent: wrapper, computed: { position: 'fixed' }, selectors: ['button'] });
  wrapper.firstElementChild = tiny;
  tiny.getBoundingClientRect = () => {
    const top = Number.parseFloat(tiny.style.getPropertyValue('--candy-browser-owned-top-inset-offset')) || 0;
    return { top, bottom: top + 2, left: 5, right: 7, width: 2, height: 2 };
  };
  state.hitTest = (x, y) => {
    const rect = tiny.getBoundingClientRect();
    return x >= rect.left && x < rect.right && y >= rect.top && y < rect.bottom ? [tiny] : [];
  };
  reads.enqueuePrioritySubtree(wrapper);
  const results = [];
  reads.protectTopInset(document.documentElement, document.body, null, 48, true, false,
    (result) => results.push(result));
  timers[0]();
  assert.equal(tiny.getBoundingClientRect().top, 56);
  assert.deepEqual(results, [], 'Early target protection is not a completed global verification');
  assert.equal(reads.pointDiscoveryPending(), true);
  assert.deepEqual(Array.from(reads.failureCounts()), [0, 0, 0]);
  for (let index = 1; index < timers.length; index++) {
    assert.ok(index < 100);
    timers[index]();
  }
  assert.deepEqual(results, [true]);
});

test('initial and dense-first bounded chunks protect two edge controls after fresh-layout restarts', () => {
  for (const denseFirstAfterCancellation of [false, true]) {
    const { reads, document, createDom, fakeElement, state, context, calls } = harness();
    const parent = createDom();
    context.innerWidth = 400;
    if (denseFirstAfterCancellation) {
      state.pointCostMillis = 5;
      reads.protectTopInset(document.documentElement, document.body, null, 48, true);
      reads.invalidatePointDiscovery();
    }
    state.pointCostMillis = 0;
    const controls = [1, 391].map((left) => {
      const control = fakeElement({ parent, computed: { position: 'fixed' }, selectors: ['button'] });
      control.getBoundingClientRect = () => {
        const top = Number.parseFloat(control.style.getPropertyValue('--candy-browser-owned-top-inset-offset')) || 0;
        return { top, bottom: top + 4, left, right: left + 4, width: 4, height: 4 };
      };
      return control;
    });
    state.hitTest = (x, y) => controls.filter((control) => {
      const rect = control.getBoundingClientRect();
      return x >= rect.left && x < rect.right && y >= rect.top && y < rect.bottom;
    });
    const results = [];
    reads.protectTopInset(document.documentElement, document.body, null, 48, true, false,
      (result) => results.push(result));
    assert.deepEqual(controls.map((control) => control.getBoundingClientRect().top), [56, 56]);
    assert.deepEqual(results, [], 'Local synchronous ownership does not complete broad verification');
    assert.ok(calls.points <= 64, 'Initial control validation stays within the native task-call regression bound');
  }
});

test('a static identity transform on the fixed header itself permits first-chunk protection', () => {
  for (const transform of [
    'matrix(1, 0, 0, 1, 0, 0)',
    'matrix3d(1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1)',
  ]) {
    const { reads, document, createDom, fakeElement, state, context } = harness();
    const parent = createDom();
    context.innerWidth = 360;
    state.inset = 32;
    state.pointCostMillis = 5;
    const header = fakeElement({ parent, computed: { position: 'fixed', transform } });
    header.getBoundingClientRect = () => {
      const offset = Number.parseFloat(header.style.getPropertyValue('--candy-browser-owned-top-inset-offset')) || 0;
      return { top: offset, bottom: offset + 64, left: 0, right: 360, width: 360, height: 64 };
    };
    state.hitTest = (_x, y) => {
      const rect = header.getBoundingClientRect();
      return y >= rect.top && y < rect.bottom ? [header] : [];
    };
    const results = [];
    reads.protectTopInset(document.documentElement, document.body, null, 32, true, false,
      (result) => results.push(result));
    assert.equal(header.getAttribute('data-candy-browser-top-inset-offset'), 'true');
    assert.equal(header.getBoundingClientRect().top, 32);
    assert.deepEqual(results, [], 'Early identity offset is not a completed global safety proof');
    const sticky = fakeElement({ parent, computed: { position: 'sticky', top: '0px', transform } });
    assert.equal(reads.canUseCssStickyAnchor(sticky, document.documentElement), false,
      'The CSS-sticky path must remain strict even for an identity transform');
  }
});

test('early identity exception excludes transformed ancestors scaling and active author motion', () => {
  const identity = 'matrix(1, 0, 0, 1, 0, 0)';
  for (const { ancestor = {}, own = {} } of [
    { ancestor: { transform: identity } },
    { own: { transform: 'matrix(2, 0, 0, 2, 0, 0)' } },
    { own: { animationName: 'moving-header', animationDuration: '1s' } },
    { own: { transitionProperty: 'transform', transitionDuration: '0.2s' } },
    { own: { scale: '2' } },
    { own: { rotate: '90deg' } },
    { own: { perspective: '100px' } },
    { own: { translate: '0px 0px' } },
  ]) {
    const { reads, document, createDom, fakeElement, state, context } = harness();
    const parent = createDom();
    Object.assign(parent.computed, ancestor);
    context.innerWidth = 360;
    state.inset = 32;
    const header = fakeElement({
      parent, computed: { position: 'fixed', transform: identity, ...own },
      rect: { top: 0, bottom: 64, left: 0, right: 360, width: 360, height: 64 },
    });
    state.hits = [header];
    reads.protectTopInset(document.documentElement, document.body, null, 32, true);
    assert.equal(header.getAttribute('data-candy-browser-top-inset-offset'), null);
    assert.equal(header.style.getPropertyValue('translate'), '', 'Early protection must not take author-motion ownership');
  }
});

test('identity matrix validation is exact and rejects incomplete malformed or nonidentity values', () => {
  const { reads } = harness();
  assert.equal(reads.isIdentityTransform('matrix(1.0, -0, 0, 1e0, 0, 0)'), true);
  for (const value of [
    'none', 'translateZ(0)', 'matrix(1, 0, 0, 1, 0)', 'matrix(1,,0,1,0,0)',
    'matrix(1, 0, 0, 1, 0.001, 0)', 'matrix(1.000001, 0, 0, 1, 0, 0)',
    'matrix3d(1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 1, 1)',
  ]) assert.equal(reads.isIdentityTransform(value), false, value);
});

test('new wrapper descendants run before an older Flow-registration backlog', () => {
  const { reads, document, createDom, fakeElement, state } = harness();
  const parent = createDom();
  for (let index = 0; index < 100; index++) {
    reads.enqueuePrioritySubtree(fakeElement({ parent, computed: { position: 'static' } }));
  }
  const wrapper = fakeElement({ parent, computed: { position: 'static' } });
  const tiny = fakeElement({ parent: wrapper, computed: { position: 'fixed' }, selectors: ['button'] });
  wrapper.firstElementChild = tiny;
  tiny.getBoundingClientRect = () => {
    const top = Number.parseFloat(tiny.style.getPropertyValue('--candy-browser-owned-top-inset-offset')) || 0;
    return { top, bottom: top + 2, left: 5, right: 7, width: 2, height: 2 };
  };
  state.hitTest = () => [];
  reads.enqueuePrioritySubtree(wrapper);
  reads.withLayoutReadCache(() => reads.refreshPriorityCandidates(document.documentElement, 48));
  assert.equal(tiny.getBoundingClientRect().top, 56);
  assert.equal(reads.priorityWorkPending(), true, 'Older Flow work stays queued, not discarded as a safety proof');
});

test('recent priority descendants and older FIFO roots both progress without scanning the backlog', () => {
  const { reads, createDom, fakeElement } = harness();
  const parent = createDom();
  const older = fakeElement({ parent });
  const newest = fakeElement({ parent });
  const first = fakeElement({ parent: newest });
  const second = fakeElement({ parent: newest });
  newest.firstElementChild = first;
  first.nextElementSibling = second;
  reads.enqueuePrioritySubtree(older);
  reads.enqueuePrioritySubtree(newest);
  let iterations = 0;
  const iterate = reads.pendingPriorityRoots[Symbol.iterator];
  reads.pendingPriorityRoots[Symbol.iterator] = function () {
    iterations++;
    return iterate.call(this);
  };
  assert.equal(reads.nextPriorityElement(), newest);
  assert.equal(reads.nextPriorityElement(), older, 'FIFO receives a slot before the newest subtree completes');
  assert.equal(reads.nextPriorityElement(), first);
  assert.equal(reads.nextPriorityElement(), second);
  assert.equal(reads.nextPriorityElement(), null);
  assert.equal(iterations, 0, 'Selection needs no full global Set pass');
  assert.equal(reads.priorityWorkPending(), false);
});

test('repeated author changes retain an active priority cursor and coalesce one fresh follow-up', () => {
  const { reads, createDom, fakeElement } = harness();
  const parent = createDom();
  const active = fakeElement({ parent });
  const child = fakeElement({ parent: active });
  active.firstElementChild = child;
  reads.enqueuePrioritySubtree(active, false);
  assert.equal(reads.nextPriorityElement(), active);
  for (let index = 0; index < 100; index++) reads.enqueuePrioritySubtree(active, false);
  reads.enqueuePrioritySubtree(active, true);
  assert.equal(reads.nextPriorityElement(), child);
  const late = fakeElement({ parent: active });
  child.nextElementSibling = late;
  reads.enqueuePrioritySubtree(active, false);
  assert.equal(reads.nextPriorityElement(), active, 'Already captured null sibling requires one fresh pass');
  assert.equal(reads.nextPriorityElement(), child);
  assert.equal(reads.nextPriorityElement(), late);
  assert.equal(reads.nextPriorityElement(), null);
  assert.equal(reads.priorityWorkPending(), false, 'One hundred changes do not queue one hundred restarts');
});

test('priority root removal prunes lane state and preserves the 256-root cap without losing remaining roots', () => {
  const { reads, createDom, fakeElement } = harness();
  const parent = createDom();
  const roots = Array.from({ length: 257 }, () => fakeElement({ parent }));
  for (const root of roots) reads.enqueuePrioritySubtree(root);
  assert.equal(reads.pendingPriorityRoots.size, 256);
  assert.equal(reads.pendingPriorityRoots.has(roots[0]), false);
  roots[256].isConnected = false;
  reads.invokeMutationObserver([{ type: 'childList', target: parent, addedNodes: [], removedNodes: [roots[256]] }]);
  assert.equal(reads.pendingPriorityRoots.size, 255);
  const visited = new Set();
  for (let count = 0; reads.priorityWorkPending(); count++) {
    assert.ok(count < 300);
    const element = reads.nextPriorityElement();
    if (element) {
      assert.equal(visited.has(element), false);
      visited.add(element);
    }
  }
  assert.deepEqual(visited, new Set(roots.slice(1, 256)));
  assert.equal(reads.nextPriorityElement(), null);
});

test('both priority lanes admit their first root even when the other lane fills the global cap', () => {
  for (const incomingAdded of [true, false]) {
    const { reads, createDom, fakeElement } = harness();
    const parent = createDom();
    const backlog = Array.from({ length: 256 }, () => fakeElement({ parent, computed: { position: 'static' } }));
    for (const root of backlog) reads.enqueuePrioritySubtree(root, !incomingAdded);
    const incoming = fakeElement({ parent, computed: { position: 'static' } });
    reads.enqueuePrioritySubtree(incoming, incomingAdded);
    assert.equal(reads.pendingPriorityRoots.size, 256);
    assert.equal(reads.pendingPriorityRoots.has(incoming), true);
    assert.equal(reads.pendingPriorityRoots.has(backlog[0]), false);
    const first = reads.nextPriorityElement();
    const second = reads.nextPriorityElement();
    assert.ok(first === incoming || second === incoming, 'New nonempty lane receives one of the first two slots');
  }
});

test('recurring actual feed mutations cannot restart priority discovery ahead of a newly added tiny control', () => {
  const { reads, fakeElement, createDom, document } = harness();
  const container = createDom();
  const feed = fakeElement({ parent: container, computed: { position: 'static' } });
  const visited = new Set();
  let previous = null;
  for (let index = 0; index < 4096; index++) {
    const card = fakeElement({ parent: feed, computed: { get position() { visited.add(index); return 'static'; } } });
    if (previous) previous.nextElementSibling = card;
    else feed.firstElementChild = card;
    previous = card;
  }
  const wrapper = fakeElement({ parent: container, computed: { position: 'static' },
    rect: { top: 2400, height: 40, width: 25 } });
  const control = fakeElement({ parent: wrapper, computed: { position: 'fixed' }, selectors: ['button'] });
  wrapper.firstElementChild = control;
  control.getBoundingClientRect = () => {
    const top = Number.parseFloat(control.style.getPropertyValue('--candy-browser-owned-top-inset-offset')) || 0;
    return { top, bottom: top + 12, left: 0, right: 12, width: 12, height: 12 };
  };
  reads.invokeMutationObserver([{ type: 'childList', target: container, addedNodes: [wrapper], removedNodes: [] }]);
  for (let chunk = 0; chunk < 64; chunk++) {
    reads.invokeMutationObserver([{ type: 'attributes', attributeName: 'class', target: feed }]);
    reads.withLayoutReadCache(() => reads.refreshPriorityCandidates(document.documentElement, 48));
    assert.equal(control.getAttribute('data-candy-browser-top-inset-offset'), 'true');
    assert.equal(control.getBoundingClientRect().top, 56);
  }
  assert.ok(visited.size >= 128, 'Attribute lane keeps distinct forward progress instead of restarting at feed root');
  assert.equal(reads.discoveryNeeded(), true, 'Priority is not full opaque CSS coverage');
  assert.equal(reads.ownedLayoutMutationPending(), true);
});

test('new Added pressure leaves FIFO Added cursors and Attribute jobs progressing', () => {
  const { reads, createDom, fakeElement, document } = harness();
  const parent = createDom();
  const seen = [new Set(), new Set()];
  for (let lane = 0; lane < 2; lane++) {
    const root = fakeElement({ parent, computed: { position: 'static' } });
    let previous = null;
    for (let index = 0; index < 64; index++) {
      const leaf = fakeElement({ parent: root, computed: { get position() { seen[lane].add(index); return 'static'; } } });
      if (previous) previous.nextElementSibling = leaf;
      else root.firstElementChild = leaf;
      previous = leaf;
    }
    reads.enqueuePrioritySubtree(root, lane === 0);
  }
  for (let chunk = 0; chunk < 64; chunk++) {
    const root = fakeElement({ parent, computed: { position: 'static' } });
    root.firstElementChild = fakeElement({ parent: root, computed: { position: 'static' } });
    reads.enqueuePrioritySubtree(root);
    reads.withLayoutReadCache(() => reads.refreshPriorityCandidates(document.documentElement, 48));
  }
  assert.ok(seen[0].size >= 8, 'Older Added root receives FIFO progress despite genuinely new roots');
  assert.equal(seen[1].size, 64, 'New Added roots cannot starve Attribute work');
});

test('removed or reparented priority child cursors restart fresh without losing original following siblings', () => {
  for (const removed of [true, false]) {
    const { reads, createDom, fakeElement } = harness();
    const parent = createDom();
    const root = fakeElement({ parent });
    const cursor = fakeElement({ parent: root });
    const following = fakeElement({ parent: root });
    const foreign = fakeElement({ parent });
    root.firstElementChild = cursor;
    cursor.nextElementSibling = following;
    reads.enqueuePrioritySubtree(root);
    assert.equal(reads.nextPriorityElement(), root);
    cursor.isConnected = !removed;
    cursor.parentElement = removed ? null : parent;
    cursor.nextElementSibling = removed ? null : foreign;
    root.firstElementChild = following;
    assert.equal(reads.nextPriorityElement(), root, 'Invalid saved cursor requests a fresh follow-up');
    assert.equal(reads.nextPriorityElement(), following);
    assert.equal(reads.nextPriorityElement(), null);
    assert.equal(reads.priorityWorkPending(), false);
  }
});

test('priority traversal bookkeeping respects the time budget and resumes its retained stack', () => {
  const { reads, createDom, fakeElement, state } = harness();
  const root = createDom();
  const chain = [root];
  for (let index = 0; index < 64; index++) {
    const leaf = fakeElement({ parent: chain.at(-1) });
    chain.at(-1).firstElementChild = leaf;
    chain.push(leaf);
  }
  reads.enqueuePrioritySubtree(root);
  for (const element of chain) assert.equal(reads.nextPriorityElement(), element);
  for (const element of chain.slice(1)) {
    Object.defineProperty(element, 'parentNode', { get() { state.clock++; return element.parentElement; } });
  }
  assert.equal(reads.nextPriorityElement(), null);
  assert.ok(state.clock <= 4, 'Pop/skip bookkeeping yields without needing another fresh styled element');
  assert.equal(reads.priorityWorkPending(), true);
  for (let count = 0; reads.priorityWorkPending(); count++) {
    assert.ok(count < 32);
    reads.nextPriorityElement();
  }
});

test('priority advancement cannot resurrect a synchronously evicted job or corrupt its replacement', () => {
  for (const replace of [false, true]) {
    const { reads, createDom, fakeElement } = harness();
    const parent = createDom();
    const active = fakeElement({ parent, computed: { position: 'static' } });
    const newcomers = Array.from({ length: 256 }, () => fakeElement({ parent, computed: { position: 'static' } }));
    let reacted = false;
    Object.defineProperty(active, 'firstElementChild', { get() {
      if (!reacted) {
        reacted = true;
        for (const root of newcomers) reads.enqueuePrioritySubtree(root);
        if (replace) reads.enqueuePrioritySubtree(active);
      }
      return null;
    } });
    reads.enqueuePrioritySubtree(active);
    const first = reads.nextPriorityElement();
    assert.equal(reads.pendingPriorityRoots.size, 256);
    assert.equal(reads.pendingPriorityRoots.has(active), replace);
    assert.ok(newcomers.includes(first) || (replace && first === active));
    const seen = new Set([first]);
    for (let count = 0; reads.priorityWorkPending(); count++) {
      assert.ok(count < 300);
      const element = reads.nextPriorityElement();
      if (element) {
        assert.equal(seen.has(element), false);
        seen.add(element);
      }
      assert.ok(reads.pendingPriorityRoots.size <= 256);
    }
    assert.equal(seen.size, 256);
    assert.equal(seen.has(active), replace, 'Only the replacement identity may be visited');
  }
});

test('an expensive priority style query stops its chunk without losing the remaining roots', () => {
  const { reads, createDom, fakeElement, state, calls, document } = harness();
  const parent = createDom();
  for (let index = 0; index < 10; index++) {
    reads.enqueuePrioritySubtree(fakeElement({ parent,
      computed: { get position() { state.clock += 5; return 'static'; } } }));
  }
  for (let chunk = 0; reads.priorityWorkPending(); chunk++) {
    assert.ok(chunk < 16);
    const before = calls.styles;
    reads.withLayoutReadCache(() => reads.refreshPriorityCandidates(document.documentElement, 48));
    assert.ok(calls.styles - before <= 1, 'One non-interruptible style query may overshoot, not a second');
  }
  assert.equal(calls.styles, 10);
  assert.equal(calls.rects, 0);
  assert.equal(calls.points, 0);
});

test('terminal priority connected-state reactions cannot mutate an evicted job or its replacement', () => {
  for (const replace of [false, true]) {
    const { reads, createDom, fakeElement } = harness();
    const parent = createDom();
    const active = fakeElement({ parent, computed: { position: 'static' } });
    const newcomers = Array.from({ length: 256 }, () => fakeElement({ parent, computed: { position: 'static' } }));
    let connectedReads = 0;
    Object.defineProperty(active, 'isConnected', { get() {
      if (++connectedReads === 3) {
        for (const root of newcomers) reads.enqueuePrioritySubtree(root);
        if (replace) reads.enqueuePrioritySubtree(active);
      }
      return true;
    } });
    reads.enqueuePrioritySubtree(active);
    assert.equal(reads.nextPriorityElement(), active);
    reads.enqueuePrioritySubtree(active);
    const seen = new Set();
    for (let count = 0; reads.priorityWorkPending(); count++) {
      assert.ok(count < 300);
      const element = reads.nextPriorityElement();
      if (element) {
        assert.equal(seen.has(element), false);
        seen.add(element);
      }
      assert.ok(reads.pendingPriorityRoots.size <= 256);
    }
    assert.equal(seen.size, 256);
    assert.equal(seen.has(active), replace);
  }
});

test('compact wide peers reject invisible styles before any rectangle read', () => {
  for (const computed of [
    { display: 'none' }, { visibility: 'hidden' }, { visibility: 'collapse' },
    { opacity: '0' }, { opacity: '0.01' }, { opacity: 'invalid' },
  ]) {
    const { reads, createDom, fakeElement, document, calls } = harness();
    createDom();
    const excluded = fakeElement();
    const candidate = fakeElement({ parent: document.documentElement, computed });
    assert.equal(reads.findCompactViewportWidePeer(candidate, document.documentElement, excluded), null);
    assert.equal(calls.rects, 0, JSON.stringify(computed));
  }
});

test('invisible compact peer children retain visible ancestor and exact geometry boundaries', () => {
  const { reads, createDom, fakeElement, document, calls, context } = harness();
  createDom();
  context.innerWidth = 100;
  const excluded = fakeElement();
  const ancestor = fakeElement({ parent: document.documentElement,
    computed: { position: 'static', opacity: '0.011' },
    rect: { top: 0, bottom: 399, left: 0, right: 80, width: 80, height: 399 } });
  const hidden = fakeElement({ parent: ancestor, computed: { display: 'none' } });
  assert.equal(reads.findCompactViewportWidePeer(hidden, document.documentElement, excluded), ancestor);
  assert.equal(calls.rects, 1, 'Hidden child is skipped, not its eligible ancestor');
  for (const rect of [{ width: 79.9, height: 399 }, { width: 80, height: 1 }, { width: 80, height: 400 }]) {
    ancestor.rect = rect;
    assert.equal(reads.findCompactViewportWidePeer(ancestor, document.documentElement, excluded), null);
  }
  context.innerWidth = 1;
  ancestor.rect = { width: 1, height: 2 };
  assert.equal(reads.findCompactViewportWidePeer(ancestor, document.documentElement, excluded), ancestor,
    'Do not add the separate positioned-candidate width > 1 rule to compact peers');
});

test('priority traversal visits new retained Shadow feed subtrees linearly without Flow geometry reads', () => {
  const { reads, document, createDom, fakeElement, calls } = harness();
  const parent = createDom();
  const host = fakeElement({ parent, computed: { position: 'static' } });
  const feed = fakeElement({ computed: { position: 'static' } });
  host.shadowRoot = { mode: 'open', host, firstElementChild: feed };
  feed.getRootNode = () => host.shadowRoot;
  reads.enqueuePrioritySubtree(host);
  while (reads.priorityWorkPending()) {
    reads.withLayoutReadCache(() => reads.refreshPriorityCandidates(document.documentElement, 48));
  }
  const baseline = calls.styles;
  for (let batch = 0; batch < 10; batch++) {
    for (let index = 0; index < 100; index++) {
      const card = fakeElement({ parent: feed, computed: { position: 'static' } });
      let last = null;
      for (let child = 0; child < 3; child++) {
        const leaf = fakeElement({ parent: card, computed: { position: 'static' } });
        if (last) last.nextElementSibling = leaf;
        else card.firstElementChild = leaf;
        last = leaf;
      }
      reads.enqueuePrioritySubtree(card);
    }
    let chunks = 0;
    while (reads.priorityWorkPending()) {
      assert.ok(chunks++ < 100);
      const before = calls.styles;
      reads.withLayoutReadCache(() => reads.refreshPriorityCandidates(document.documentElement, 48));
      assert.ok(calls.styles - before <= 8);
    }
  }
  assert.equal(calls.styles - baseline, 4000, 'Each added card plus three Flow children is read once');
  assert.equal(calls.rects, 0);
  assert.equal(calls.points, 0, 'Priority registration alone is style-only, not a global safety proof');
});

test('priority registers offscreen stable sticky once without requiring any geometry', () => {
  const { reads, document, createDom, fakeElement, calls } = harness();
  const parent = createDom();
  const sticky = fakeElement({
    parent, computed: { position: 'sticky', top: '0px' },
    rect: { top: 4000, bottom: 4064, left: 0, right: 25, width: 25, height: 64 },
  });
  reads.enqueuePrioritySubtree(sticky);
  reads.withLayoutReadCache(() => reads.refreshPriorityCandidates(document.documentElement, 48));
  assert.equal(sticky.hasAttribute('data-candy-browser-top-inset-sticky'), true);
  assert.equal(calls.rects, 0);
  assert.equal(calls.points, 0);
  const baseline = { ...calls };
  for (let index = 0; index < 1000; index++) reads.refreshStickyElements([sticky], 48);
  assert.deepEqual(calls, baseline);
});

test('priority retains author motion on a fixed control ancestor and skips detached roots', () => {
  const { reads, document, createDom, fakeElement, calls } = harness();
  const parent = createDom();
  parent.computed.transitionProperty = 'top';
  parent.computed.transitionDuration = '1s';
  const movingControl = fakeElement({ parent, computed: { position: 'fixed' }, selectors: ['button'] });
  reads.enqueuePrioritySubtree(movingControl);
  reads.withLayoutReadCache(() => reads.refreshPriorityCandidates(document.documentElement, 48));
  assert.equal(movingControl.hasAttribute('data-candy-browser-top-inset-offset'), false);
  assert.equal(calls.rects, 0);
  assert.equal(calls.points, 0);
  const detached = fakeElement({ computed: { position: 'fixed' }, selectors: ['button'] });
  detached.isConnected = false;
  const baselineStyles = calls.styles;
  reads.enqueuePrioritySubtree(detached);
  while (reads.priorityWorkPending()) {
    reads.withLayoutReadCache(() => reads.refreshPriorityCandidates(document.documentElement, 48));
  }
  assert.equal(calls.styles, baselineStyles);
});

test('an offscreen leaf class change preserves global static-to-fixed and CSS sticky declassification invalidation', () => {
  const { reads, createDom, fakeElement } = harness();
  const parent = createDom();
  const sticky = fakeElement({
    parent, computed: { position: 'sticky', top: '12px' },
    properties: [['top', { value: '12px', priority: '' }]],
  });
  reads.applyStickyTopAnchor(sticky, 12, 48);
  const leaf = fakeElement({
    parent, computed: { position: 'static' },
    rect: { top: 4000, bottom: 4020, left: 0, right: 20, width: 20, height: 20 },
  });
  assert.equal(reads.mutationNeedsCandidateDiscovery({
    type: 'attributes', attributeName: 'class', target: leaf,
  }), true, 'body:has(.leaf) may reposition an old static node outside this subtree');
  sticky.computed.position = 'static';
  reads.revalidateOwnedStickyAnchors(48);
  assert.equal(sticky.hasAttribute('data-candy-browser-top-inset-sticky'), false);
  assert.equal(sticky.style.getPropertyValue('top'), '12px');
  const observer = script.split('observer = new MutationObserver((records) =>')[1]
    .split('const originalAttachShadow')[0];
  assert.ok(observer.includes('pendingOwnedLayoutMutation = true'),
    'Opaque CSS mutations must preserve a pending GLOBAL owned-layout reread');
  assert.ok(observer.indexOf('scheduleDeferredLayoutCheck(true)') <
    observer.indexOf('flushPendingOwnedLayoutMutation(physicalPixels / density)'),
  'Recovery must be queued before a synchronous native read can throw');
  const pendingFlush = script.split('const flushPendingOwnedLayoutMutation =')[1]
    .split('\n              const ')[0];
  assert.ok(pendingFlush.includes('revalidateOwnedStickyAnchors(cssPixels)'),
    'Deferred global reread must not restrict sticky declassification to local ancestry');
});

test('remote feed mutations retain global discovery without forcing owned layout reads', () => {
  const { reads, createDom, fakeElement, calls, writes } = harness();
  const container = createDom();
  const header = fakeElement({ parent: container, computed: { position: 'fixed' } });
  reads.trackOffsetCandidate(header);
  const feed = fakeElement({ parent: container, computed: { position: 'static' } });
  const leaf = fakeElement({ parent: feed, computed: { position: 'static' } });
  const record = { type: 'attributes', attributeName: 'class', target: leaf };
  const baseline = { calls: { ...calls }, writes: { ...writes } };
  for (let index = 0; index < 1000; index++) {
    assert.equal(reads.mutationNeedsCandidateDiscovery(record), true);
    assert.equal(reads.mutationTouchesOwnedLayout(record), false);
  }
  assert.deepEqual({ calls, writes }, baseline);
});

test('actual mutation callback shares ancestry reads across records without layout and refreshes next task', () => {
  const { reads, createDom, fakeElement, calls, context } = harness();
  const container = createDom();
  const header = fakeElement({ parent: container, computed: { position: 'fixed' } });
  const feed = fakeElement({ parent: container, computed: { position: 'static' } });
  const leaf = fakeElement({ parent: feed, computed: { position: 'static' } });
  reads.trackOffsetCandidate(header);
  let headerParentReads = 0;
  let leafParentReads = 0;
  let viewportReads = 0;
  Object.defineProperty(header, 'parentElement', { get() { headerParentReads++; return container; } });
  Object.defineProperty(leaf, 'parentElement', { get() { leafParentReads++; return feed; } });
  for (const name of ['innerWidth', 'innerHeight']) {
    Object.defineProperty(context, name, { get() { viewportReads++; return 800; } });
  }
  const records = Array.from({ length: 32 }, () => ({
    type: 'attributes', attributeName: 'class', target: leaf,
  }));
  reads.invokeMutationObserver(records);
  assert.equal(headerParentReads, 1);
  assert.equal(leafParentReads, 1);
  assert.equal(viewportReads, 0);
  assert.deepEqual(calls, { styles: 0, rects: 0, points: 0 });
  assert.equal(reads.ownedLayoutMutationPending(), true, 'Opaque author CSS retains global pending repair');
  reads.invokeMutationObserver(records);
  assert.equal(headerParentReads, 2, 'A completed callback never retains native ancestry');
  assert.equal(leafParentReads, 2);
});

test('actual mutation callback releases read epoch on predicate failure and keeps recovery queued', () => {
  const { reads, createDom, fakeElement, timers } = harness();
  const container = createDom();
  const header = fakeElement({ parent: container });
  const feed = fakeElement({ parent: container });
  const leaf = fakeElement({ parent: feed });
  reads.trackOffsetCandidate(header);
  const failure = new Error('author selector failure');
  const throwing = fakeElement({ parent: feed });
  throwing.matches = () => { throw failure; };
  assert.throws(() => reads.invokeMutationObserver([
    { type: 'attributes', attributeName: 'class', target: leaf },
    { type: 'attributes', attributeName: 'class', target: throwing },
  ]), (error) => error === failure);
  assert.equal(reads.ownedLayoutMutationPending(), true);
  assert.equal(timers.length, 1, 'Quiet recovery was queued before the fresh predicate failed');
  const newParent = fakeElement({ parent: container });
  leaf.parentElement = newParent;
  assert.equal(reads.parentElementOrShadowHost(leaf), newParent,
    'Throwing callback must not leak cached pre-failure parent');
});

test('actual mutation callback invalidates ancestry populated by synchronous owned-write reactions', () => {
  const { reads, createDom, fakeElement } = harness();
  const container = createDom();
  const header = fakeElement({ parent: container });
  const feed = fakeElement({ parent: container });
  const replacement = fakeElement({ parent: container });
  const leaf = fakeElement({ parent: feed });
  reads.trackOffsetCandidate(header);
  const trigger = fakeElement({ parent: feed });
  const originalSetAttribute = leaf.setAttribute;
  leaf.setAttribute = (name, value) => {
    assert.equal(reads.parentElementOrShadowHost(leaf), feed);
    leaf.parentElement = replacement;
    originalSetAttribute(name, value);
  };
  let reactions = 0;
  trigger.matches = () => {
    if (reactions++ === 0) {
      reads.setOwnedAttribute(leaf, 'data-candy-test-reaction', 'true');
      assert.equal(reads.parentElementOrShadowHost(leaf), replacement,
        'Reaction read then reparent must not publish stale ancestry');
    }
    return false;
  };
  reads.invokeMutationObserver([
    { type: 'attributes', attributeName: 'class', target: leaf },
    { type: 'attributes', attributeName: 'class', target: trigger },
  ]);
  assert.equal(leaf.parentElement, replacement);
  assert.equal(reads.ownedLayoutMutationPending(), true);
});

test('actual immediate mutation flush rereads a nested sticky scrollport after an author reaction', () => {
  const { reads, createDom, fakeElement } = harness();
  const container = createDom();
  const outer = fakeElement({ parent: container, computed: { position: 'sticky', top: '0px' } });
  const oldScrollport = fakeElement({ parent: outer,
    computed: { position: 'static', overflowY: 'auto', borderTopWidth: '0px' },
    rect: { top: 5, height: 200, width: 25 },
  });
  const newScrollport = fakeElement({ parent: container,
    computed: { position: 'static', overflowY: 'auto', borderTopWidth: '0px' },
    rect: { top: 20, height: 200, width: 25 },
  });
  const inner = fakeElement({ parent: oldScrollport, computed: { position: 'sticky', top: '0px' } });
  const leaf = fakeElement({ parent: inner, computed: { position: 'static' } });
  reads.applyStickyTopAnchor(outer, 0, 48);
  reads.applyStickyTopAnchor(inner, 0, 48);
  assert.equal(inner.style.getPropertyValue('--candy-browser-owned-sticky-top'), '43px');
  const previousOuterStyle = 'top: var(--candy-browser-owned-sticky-top, 0px) !important';
  outer.style.setProperty('top', '12px', '');
  outer.setAttribute('style', 'top: 12px;');
  outer.computed.top = '12px';
  const originalRemoveAttribute = outer.removeAttribute;
  let reactions = 0;
  outer.removeAttribute = (name) => {
    originalRemoveAttribute(name);
    if (name === 'data-candy-browser-top-inset-sticky') {
      reactions++;
      assert.equal(reads.parentElementOrShadowHost(inner), oldScrollport);
      inner.parentElement = newScrollport;
    }
  };
  const outerMutation = {
    type: 'attributes', attributeName: 'style', target: outer, oldValue: previousOuterStyle,
  };
  assert.equal(reads.isRelevantLayoutMutation(outerMutation), true);
  reads.invokeMutationObserver([
    { type: 'attributes', attributeName: 'class', target: leaf },
    outerMutation,
  ]);
  assert.equal(reactions, 1);
  assert.equal(inner.style.getPropertyValue('--candy-browser-owned-sticky-top'), '28px',
    'Sequential nested repair uses the new 20px scrollport, not its cached 5px predecessor');
  assert.equal(outer.style.getPropertyValue('--candy-browser-owned-sticky-original-top'), '12px');
  assert.equal(outer.style.getPropertyValue('top'), 'var(--candy-browser-owned-sticky-top, 0px)');
  assert.equal(reads.ownedLayoutMutationPending(), false, 'Immediate repair completed in the actual callback');
});

test('an owned header mutation revalidates a sibling sticky author top changed by opaque CSS before paint', () => {
  const { reads, createDom, fakeElement } = harness();
  const container = createDom();
  const trigger = fakeElement({ parent: container, computed: { position: 'fixed', top: '0px' }, selectors: ['button'] });
  trigger.getBoundingClientRect = () => {
    const top = Number.parseFloat(trigger.style.getPropertyValue('--candy-browser-owned-top-inset-offset')) || 0;
    return { top, bottom: top + 12, left: 0, right: 12, width: 12, height: 12 };
  };
  const sibling = fakeElement({ parent: container, computed: {
    position: 'sticky', get top() { return trigger.getAttribute('class') === 'changed' ? '80px' : '0px'; },
  } });
  reads.trackOffsetCandidate(trigger);
  reads.applyStickyTopAnchor(sibling, 0, 48);
  assert.equal(sibling.style.getPropertyValue('--candy-browser-owned-sticky-original-top'), '0px');
  trigger.setAttribute('class', 'changed');
  reads.invokeMutationObserver([{ type: 'attributes', attributeName: 'class', target: trigger, oldValue: '' }]);
  assert.equal(sibling.style.getPropertyValue('--candy-browser-owned-sticky-original-top'), '80px',
    'body:has(.changed) can change another established header outside the mutated subtree');
  assert.equal(sibling.style.getPropertyValue('--candy-browser-owned-sticky-top'), 'max(80px, var(--candy-browser-content-top-inset, 0px))');
  assert.equal(reads.ownedLayoutMutationPending(), false);
});

test('direct owned mutations and global stylesheet changes retain the immediate layout path', () => {
  const { reads, createDom, fakeElement, calls } = harness();
  const container = createDom();
  const header = fakeElement({ parent: container, computed: { position: 'fixed' } });
  const child = fakeElement({ parent: header });
  reads.trackOffsetCandidate(header);
  const sticky = fakeElement({ parent: container, computed: { position: 'sticky' } });
  reads.trackStickyCandidate(sticky);
  const baseline = { ...calls };
  for (const target of [header, child, container, sticky]) {
    assert.equal(reads.mutationTouchesOwnedLayout({
      type: 'attributes', attributeName: 'class', target,
    }), true);
    assert.equal(reads.mutationNeedsImmediateOwnedLayout({
      type: 'attributes', attributeName: 'class', target,
    }), true);
  }
  const stylesheet = fakeElement({ selectors: ['style'] });
  assert.equal(reads.mutationTouchesOwnedLayout({
    type: 'childList', target: container, addedNodes: [stylesheet],
  }), true);
  assert.equal(reads.mutationNeedsImmediateOwnedLayout({
    type: 'childList', target: container, addedNodes: [stylesheet],
  }), true);
  assert.equal(reads.mutationNeedsImmediateOwnedLayout({
    type: 'childList', target: header, addedNodes: [child],
  }), true);
  assert.equal(reads.mutationNeedsImmediateOwnedLayout({
    type: 'childList', target: container, addedNodes: [fakeElement({ parent: container })],
  }), false);
  assert.deepEqual(calls, baseline);
});

test('deferred global owned reread removes remotely declassified CSS sticky ownership', () => {
  const { reads, createDom, fakeElement } = harness();
  const parent = createDom();
  const sticky = fakeElement({ parent, computed: { position: 'sticky', top: '12px' },
    properties: [['top', { value: '12px', priority: '' }]],
  });
  reads.applyStickyTopAnchor(sticky, 12, 48);
  sticky.computed.position = 'static';
  reads.setPendingOwnedLayoutMutation(true);
  reads.flushPendingOwnedLayoutMutation(48);
  assert.equal(sticky.hasAttribute('data-candy-browser-top-inset-sticky'), false);
  assert.equal(sticky.style.getPropertyValue('top'), '12px');
  assert.equal(reads.ownedLayoutMutationPending(), false);
});

test('declassified sticky headers release ownership without reading their resolved top', () => {
  for (const position of ['static', 'relative', 'absolute', 'fixed']) {
    const { reads, createDom, fakeElement, context } = harness();
    const parent = createDom();
    const header = fakeElement({ parent, computed: { position: 'sticky', top: '12px' },
      properties: [['top', { value: '12px', priority: '' }]],
    });
    reads.applyStickyTopAnchor(header, 12, 48);
    header.computed.position = position;
    const originalGetComputedStyle = context.getComputedStyle;
    let topReads = 0;
    context.getComputedStyle = (element, pseudo) => {
      const style = originalGetComputedStyle(element, pseudo);
      return {
        ...style,
        get top() {
          topReads++;
          return style.top;
        },
      };
    };
    reads.revalidateOwnedStickyAnchors(48);
    assert.equal(topReads, 0, position);
    assert.equal(header.hasAttribute('data-candy-browser-top-inset-sticky'), false, position);
    assert.equal(header.style.getPropertyValue('top'), '12px', position);
    assert.equal(reads.jsStickyCount(), 0, position);
  }
});

test('failed or throwing deferred owned geometry never clears the pending global reread', () => {
  const { reads, createDom, fakeElement } = harness();
  const parent = createDom();
  const header = fakeElement({ parent, computed: { position: 'fixed', top: '0px' },
    rect: { top: 0, bottom: 40, left: 0, right: 25, width: 25, height: 40 },
  });
  reads.trackOffsetCandidate(header);
  reads.setPendingOwnedLayoutMutation(true);
  assert.equal(reads.flushPendingOwnedLayoutMutation(48), false,
    'Fake geometry intentionally remains unsafe despite applying an offset');
  assert.equal(reads.ownedLayoutMutationPending(), true);
  const failure = new Error('native geometry failure');
  header.getBoundingClientRect = () => { throw failure; };
  assert.throws(() => reads.flushPendingOwnedLayoutMutation(48), (error) => error === failure);
  assert.equal(reads.ownedLayoutMutationPending(), true);
  header.isConnected = false;
  assert.equal(reads.flushPendingOwnedLayoutMutation(48), true);
  assert.equal(reads.ownedLayoutMutationPending(), false);
});

test('direct owned mutation repairs coalesce before repaint without synchronous geometry', () => {
  const { reads, createDom, fakeElement, calls, writes, frames } = harness();
  const parent = createDom();
  const sticky = fakeElement({ parent, computed: { position: 'sticky', top: '12px' },
    properties: [['top', { value: '12px', priority: '' }]],
  });
  reads.applyStickyTopAnchor(sticky, 12, 48);
  sticky.computed.position = 'static';
  reads.setPendingOwnedLayoutMutation(true);
  const baseline = { calls: { ...calls }, writes: { ...writes } };
  for (let index = 0; index < 1000; index++) reads.scheduleOwnedMutationLayoutCheck();
  assert.equal(frames.length, 1);
  assert.deepEqual({ calls, writes }, baseline);
  assert.equal(reads.ownedLayoutMutationPending(), true);
  frames[0]();
  assert.equal(sticky.hasAttribute('data-candy-browser-top-inset-sticky'), false);
  assert.equal(reads.ownedLayoutMutationPending(), false);
});

test('cancelled owned repair frame cannot clear a replacement or reuse obsolete policy', () => {
  const { reads, createDom, frames, state } = harness();
  createDom();
  reads.setPendingOwnedLayoutMutation(true);
  reads.scheduleOwnedMutationLayoutCheck();
  reads.cancelOwnedMutationLayoutCheck();
  reads.scheduleOwnedMutationLayoutCheck();
  frames[0]();
  assert.equal(reads.ownedLayoutMutationPending(), true);
  frames[1]();
  assert.equal(reads.ownedLayoutMutationPending(), false);
  reads.setPendingOwnedLayoutMutation(true);
  reads.scheduleOwnedMutationLayoutCheck();
  state.policyRevision++;
  frames[2]();
  assert.equal(reads.ownedLayoutMutationPending(), true);
});

test('scrolling defers unknown sticky discovery until quiet and then reopens it', () => {
  const { reads, timers } = harness();
  reads.setDiscoveryNeeded(false);
  reads.windowScrollListener();
  assert.equal(reads.discoveryNeeded(), false);
  assert.equal(timers.length, 1);
  timers[0]();
  assert.equal(reads.discoveryNeeded(), true);
});

test('offscreen wrappers and shadow hosts cannot hide viewport fixed descendants from discovery', () => {
  const fixture = harness();
  const parent = fixture.createDom();
  const wrapper = fixture.fakeElement({
    parent,
    computed: { position: 'static' },
    rect: { top: 200, bottom: 300 },
  });
  const record = { type: 'childList', target: parent, addedNodes: [wrapper] };
  wrapper.childElementCount = 1;
  assert.equal(fixture.reads.mutationNeedsCandidateDiscovery(record), true);
  wrapper.childElementCount = 0;
  wrapper.shadowRoot = {};
  assert.equal(fixture.reads.mutationNeedsCandidateDiscovery(record), true);
});

test('one transaction reuses computed style and rect reads for the same element', () => {
  const { reads, element, calls } = harness();
  const result = reads.withLayoutReadCache(() => {
    const style = reads.readComputedStyle(element);
    const rect = reads.readElementRect(element);
    assert.equal(reads.readComputedStyle(element), style);
    assert.equal(reads.readElementRect(element), rect);
    return 'callback result';
  });
  assert.equal(result, 'callback result');
  assert.deepEqual(calls, { styles: 1, rects: 1, points: 0 });
});

test('style reads distinguish different elements and pseudo selectors', () => {
  const { reads, element, calls } = harness();
  const otherElement = {};
  reads.withLayoutReadCache(() => {
    const ordinary = reads.readComputedStyle(element);
    const before = reads.readComputedStyle(element, '::before');
    const other = reads.readComputedStyle(otherElement);
    assert.notEqual(before, ordinary);
    assert.notEqual(other, ordinary);
    assert.equal(before.pseudo, '::before');
    assert.equal(reads.readComputedStyle(element, '::before'), before);
    assert.equal(reads.readComputedStyle(otherElement), other);
  });
  assert.equal(calls.styles, 3);
});

test('point discovery reuses only identical coordinates inside one transaction', () => {
  const { reads, state, calls, element } = harness();
  state.hits = [element];
  reads.withLayoutReadCache(() => {
    assert.deepEqual(Array.from(reads.deepElementsFromPoint(1, 23)), [element]);
    assert.deepEqual(Array.from(reads.deepElementsFromPoint(1, 23)), [element]);
    assert.equal(calls.points, 1);
    reads.deepElementsFromPoint(12, 3);
    reads.deepElementsFromPoint(1, 23.5);
  });
  assert.equal(calls.points, 3);
});

test('normal offset plans do not query layout-resolved CSS height', () => {
  for (const [position, width, height] of [
    ['fixed', 25, 64], ['sticky', 25, 64], ['absolute', 25, 64],
    ['sticky', 12, 700], ['absolute', 12, 700],
  ]) {
    const { reads, fakeElement, context } = harness();
    const computedStyle = context.getComputedStyle;
    let heightReads = 0;
    context.getComputedStyle = (...args) => {
      const style = computedStyle(...args);
      Object.defineProperty(style, 'height', { get() { heightReads++; return '64px'; } });
      return style;
    };
    const header = fakeElement({ computed: { position },
      rect: { top: 0, bottom: height, left: 0, right: width, width, height },
    });
    const plan = reads.withLayoutReadCache(() => reads.planLocalOffset(header, 48));
    assert.equal(plan.offset, 48, position);
    assert.equal(plan.panelMaxHeight, null, position);
    assert.equal(heightReads, 0, 'Non-panel plans must not request resolved layout height');
  }
});

test('tall fixed panel plans retain the resolved-height box-extras calculation', () => {
  const { reads, fakeElement, context } = harness();
  const computedStyle = context.getComputedStyle;
  let heightReads = 0;
  context.getComputedStyle = (...args) => {
    const style = computedStyle(...args);
    Object.defineProperty(style, 'height', { get() { heightReads++; return '660px'; } });
    return style;
  };
  const panel = fakeElement({ computed: { position: 'fixed' },
    rect: { top: 0, bottom: 700, left: 0, right: 12, width: 12, height: 700 },
  });
  const plan = reads.withLayoutReadCache(() => reads.planLocalOffset(panel, 48));
  assert.equal(plan.offset, 48);
  assert.equal(plan.panelMaxHeight, 612);
  assert.equal(heightReads, 1, 'Fixed panels still need fresh box extras');
});

test('parent and shadow-host reads including null are shared only inside one read epoch', () => {
  const { reads } = harness();
  for (const mode of ['parent', 'host', 'null']) {
    const parent = {};
    let parentReads = 0;
    let rootReads = 0;
    const element = {
      get parentElement() { parentReads++; return mode === 'parent' ? parent : null; },
      getRootNode() { rootReads++; return { host: mode === 'host' ? parent : null }; },
    };
    reads.withLayoutReadCache(() => {
      assert.equal(reads.parentElementOrShadowHost(element), mode === 'null' ? null : parent);
      assert.equal(reads.parentElementOrShadowHost(element), mode === 'null' ? null : parent);
      assert.equal(reads.parentElementOrShadowHost(null), null);
      assert.equal(reads.parentElementOrShadowHost(undefined), null);
    });
    assert.equal(parentReads, 1, mode);
    assert.equal(rootReads, mode === 'parent' ? 0 : 1, mode);
    reads.parentElementOrShadowHost(element);
    assert.equal(parentReads, 2, 'Next task must read native ancestry again');
  }
});

test('parent reads refresh after invalidation and across completed read epochs', () => {
  const { reads } = harness();
  const oldParent = {};
  const newParent = {};
  const element = { parentElement: oldParent };
  reads.withLayoutReadCache(() => {
    assert.equal(reads.parentElementOrShadowHost(element), oldParent);
    element.parentElement = newParent;
    reads.invalidateLayoutReadCache();
    assert.equal(reads.parentElementOrShadowHost(element), newParent);
    assert.equal(reads.composedContains(oldParent, element), false);
    assert.equal(reads.composedContains(newParent, element), true);
  });
  element.parentElement = oldParent;
  reads.withLayoutReadCache(() => assert.equal(reads.parentElementOrShadowHost(element), oldParent));
});

test('owned setters invalidate reads populated by synchronous author reactions even when they throw', () => {
  for (const operation of ['setProperty', 'removeProperty', 'setAttribute', 'removeAttribute']) {
    for (const throws of [false, true]) {
      const { reads, fakeElement, state } = harness();
      const oldParent = {};
      const newParent = {};
      const element = fakeElement({ parent: oldParent,
        properties: [['--test', { value: '1px', priority: 'important' }]],
        attributes: [['data-test', 'old']],
      });
      const receiver = operation.endsWith('Property') ? element.style : element;
      const original = receiver[operation];
      const failure = new Error('author reaction failure');
      receiver[operation] = (...args) => {
        original(...args);
        reads.withLayoutReadCache(() => {
          reads.parentElementOrShadowHost(element);
          reads.readComputedStyle(element);
          reads.readElementRect(element);
        });
        element.parentElement = newParent;
        state.version = 7;
        if (throws) throw failure;
      };
      const write = () => {
        if (operation === 'setProperty') reads.setOwnedProperty(element, '--test', '2px');
        if (operation === 'removeProperty') reads.removeOwnedProperty(element, '--test');
        if (operation === 'setAttribute') reads.setOwnedAttribute(element, 'data-test', 'new');
        if (operation === 'removeAttribute') reads.removeOwnedAttribute(element, 'data-test');
      };
      reads.withLayoutReadCache(() => {
        reads.parentElementOrShadowHost(element);
        if (throws) assert.throws(write, (error) => error === failure);
        else write();
        assert.equal(reads.parentElementOrShadowHost(element), newParent, operation);
        assert.equal(reads.readComputedStyle(element).top, '7px', operation);
        assert.equal(reads.readElementRect(element).top, 70, operation);
      });
    }
  }
});

test('write invalidation refreshes computed styles rects and identical point coordinates', () => {
  const { reads, state, calls, element } = harness();
  const replacement = {};
  state.hits = [element];
  reads.withLayoutReadCache(() => {
    assert.equal(reads.readComputedStyle(element).top, '1px');
    assert.equal(reads.readElementRect(element).top, 10);
    assert.deepEqual(Array.from(reads.deepElementsFromPoint(10, 10)), [element]);
    state.version = 2;
    state.hits = [replacement];
    reads.invalidateLayoutReadCache();
    assert.equal(reads.readComputedStyle(element).top, '2px');
    assert.equal(reads.readElementRect(element).top, 20);
    assert.deepEqual(Array.from(reads.deepElementsFromPoint(10, 10)), [replacement]);
    reads.readComputedStyle(element);
    reads.readElementRect(element);
    reads.deepElementsFromPoint(10, 10);
  });
  assert.deepEqual(calls, { styles: 2, rects: 2, points: 2 });
});

test('nested transactions share reads with their outer transaction', () => {
  const { reads, calls, element } = harness();
  reads.withLayoutReadCache(() => {
    const style = reads.readComputedStyle(element);
    const rect = reads.readElementRect(element);
    reads.deepElementsFromPoint(10, 10);
    reads.withLayoutReadCache(() => {
      assert.equal(reads.readComputedStyle(element), style);
      assert.equal(reads.readElementRect(element), rect);
      reads.deepElementsFromPoint(10, 10);
    });
    assert.equal(reads.readComputedStyle(element), style);
    assert.equal(reads.readElementRect(element), rect);
    reads.deepElementsFromPoint(10, 10);
  });
  assert.deepEqual(calls, { styles: 1, rects: 1, points: 1 });
});

test('nested write invalidation also invalidates reads observed by the outer transaction', () => {
  const { reads, state, calls, element } = harness();
  reads.withLayoutReadCache(() => {
    reads.readComputedStyle(element);
    reads.readElementRect(element);
    reads.deepElementsFromPoint(10, 10);
    reads.withLayoutReadCache(() => {
      state.version = 2;
      reads.invalidateLayoutReadCache();
      assert.equal(reads.readComputedStyle(element).top, '2px');
    });
    assert.equal(reads.readComputedStyle(element).top, '2px');
    assert.equal(reads.readElementRect(element).top, 20);
    reads.deepElementsFromPoint(10, 10);
  });
  assert.deepEqual(calls, { styles: 2, rects: 2, points: 2 });
});

test('completed transactions cannot leave stale snapshots for the next turn', () => {
  const { reads, state, calls, element } = harness();
  reads.withLayoutReadCache(() => {
    reads.readComputedStyle(element);
    reads.readElementRect(element);
    reads.deepElementsFromPoint(10, 10);
  });
  state.version = 3;
  reads.withLayoutReadCache(() => {
    assert.equal(reads.readComputedStyle(element).top, '3px');
    assert.equal(reads.readElementRect(element).top, 30);
    reads.deepElementsFromPoint(10, 10);
  });
  assert.deepEqual(calls, { styles: 2, rects: 2, points: 2 });
});

test('original callback exceptions propagate and finally clears transaction snapshots', () => {
  const { reads, state, calls, element } = harness();
  const failure = new Error('callback failed');
  assert.throws(() => reads.withLayoutReadCache(() => {
    reads.readComputedStyle(element);
    reads.readElementRect(element);
    reads.deepElementsFromPoint(10, 10);
    throw failure;
  }), (error) => error === failure);
  state.version = 4;
  reads.withLayoutReadCache(() => {
    assert.equal(reads.readComputedStyle(element).top, '4px');
    assert.equal(reads.readElementRect(element).top, 40);
    reads.deepElementsFromPoint(10, 10);
  });
  assert.deepEqual(calls, { styles: 2, rects: 2, points: 2 });
});

test('reads outside a transaction do not reuse snapshots from an earlier read', () => {
  const { reads, state, calls, element } = harness();
  assert.equal(reads.readComputedStyle(element).top, '1px');
  assert.equal(reads.readElementRect(element).top, 10);
  reads.deepElementsFromPoint(10, 10);
  state.version = 5;
  assert.equal(reads.readComputedStyle(element).top, '5px');
  assert.equal(reads.readElementRect(element).top, 50);
  reads.deepElementsFromPoint(10, 10);
  assert.deepEqual(calls, { styles: 2, rects: 2, points: 2 });
});

test('cached point discovery still traverses nested shadow roots and observes each visited scope', () => {
  const { reads, state, calls } = harness();
  const leaf = {};
  let outerQueries = 0;
  let innerQueries = 0;
  const innerRoot = {
    elementsFromPoint() { innerQueries++; return [leaf]; },
  };
  const innerHost = { shadowRoot: innerRoot };
  const outerRoot = {
    elementsFromPoint() { outerQueries++; return [innerHost]; },
  };
  const outerHost = { shadowRoot: outerRoot };
  state.hits = [outerHost];
  const observed = [];
  reads.setShadowRootObserver((root) => observed.push(root));
  reads.withLayoutReadCache(() => {
    assert.deepEqual(Array.from(reads.deepElementsFromPoint(10, 10)), [leaf, innerHost, outerHost]);
    assert.deepEqual(Array.from(reads.deepElementsFromPoint(10, 10)), [leaf, innerHost, outerHost]);
  });
  assert.equal(calls.points, 1);
  assert.equal(outerQueries, 1);
  assert.equal(innerQueries, 1);
  assert.deepEqual(observed, [outerRoot, innerRoot]);
});

test('point discovery stops shadow-root lookup at the first unvisited root and preserves all hit layers', () => {
  const { reads, document, state, calls } = harness();
  const leaf = {};
  const root = { elementsFromPoint: () => [leaf, leaf] };
  const host = { shadowRoot: root };
  let unrelatedShadowReads = 0;
  const sibling = { get shadowRoot() { unrelatedShadowReads++; return null; } };
  // Repeated CSS boxes and a cycle back to the document must remain harmless.
  state.hits = [host, sibling, host, { shadowRoot: document }];
  const hits = Array.from(reads.deepElementsFromPoint(10, 10));
  assert.deepEqual(hits.slice(0, 3), [leaf, host, sibling]);
  assert.equal(hits.length, 4, 'Duplicate boxes are deduplicated across all layers');
  assert.equal(unrelatedShadowReads, 0, 'Later shadow-root getters do not contribute to the chosen scope');
  assert.equal(calls.points, 1);
});

const writeCases = [
  {
    helper: 'setOwnedProperty', initial: {}, args: ['--owned-test', '12px'],
    expectedWrites: { propertySets: 1, propertyRemovals: 0, attributeSets: 0, attributeRemovals: 0 },
  },
  {
    helper: 'setOwnedAttribute', initial: {}, args: ['data-owned-test', 'true'],
    expectedWrites: { propertySets: 0, propertyRemovals: 0, attributeSets: 1, attributeRemovals: 0 },
  },
  {
    helper: 'removeOwnedProperty',
    initial: { properties: [['--owned-test', { value: '12px', priority: 'important' }]] },
    args: ['--owned-test'],
    expectedWrites: { propertySets: 0, propertyRemovals: 1, attributeSets: 0, attributeRemovals: 0 },
  },
  {
    helper: 'removeOwnedAttribute', initial: { attributes: [['data-owned-test', 'true']] },
    args: ['data-owned-test'],
    expectedWrites: { propertySets: 0, propertyRemovals: 0, attributeSets: 0, attributeRemovals: 1 },
  },
];

for (const fixture of writeCases) {
  test(`${fixture.helper} invalidates all reads only for an actual write`, () => {
    const { reads, fakeElement, state, calls, writes } = harness();
    const element = fakeElement(fixture.initial);
    state.hits = [element];
    reads.withLayoutReadCache(() => {
      reads.readComputedStyle(element);
      reads.readElementRect(element);
      reads.deepElementsFromPoint(10, 10);
      reads[fixture.helper](element, ...fixture.args);
      assert.equal(reads.readComputedStyle(element).top, '2px');
      assert.equal(reads.readElementRect(element).top, 20);
      reads.deepElementsFromPoint(10, 10);
      reads[fixture.helper](element, ...fixture.args);
      reads.readComputedStyle(element);
      reads.readElementRect(element);
      reads.deepElementsFromPoint(10, 10);
    });
    assert.deepEqual(writes, fixture.expectedWrites);
    assert.deepEqual(calls, { styles: 2, rects: 2, points: 2 });
  });
}

test('already correct owned values and absent removals preserve cached reads', () => {
  const { reads, fakeElement, calls, writes } = harness();
  const element = fakeElement({
    properties: [['--owned-test', { value: '12px', priority: 'important' }]],
    attributes: [['data-owned-test', 'true']],
  });
  reads.withLayoutReadCache(() => {
    const style = reads.readComputedStyle(element);
    const rect = reads.readElementRect(element);
    reads.deepElementsFromPoint(10, 10);
    reads.setOwnedProperty(element, '--owned-test', '12px');
    reads.setOwnedAttribute(element, 'data-owned-test', 'true');
    reads.removeOwnedProperty(element, '--absent-owned-test');
    reads.removeOwnedAttribute(element, 'data-absent-owned-test');
    assert.equal(reads.readComputedStyle(element), style);
    assert.equal(reads.readElementRect(element), rect);
    reads.deepElementsFromPoint(10, 10);
  });
  assert.deepEqual(writes, { propertySets: 0, propertyRemovals: 0, attributeSets: 0, attributeRemovals: 0 });
  assert.deepEqual(calls, { styles: 1, rects: 1, points: 1 });
});

test('an owned property with correct value but wrong priority requires one corrective write', () => {
  const { reads, fakeElement, calls, writes } = harness();
  const element = fakeElement({ properties: [['--owned-test', { value: '12px', priority: '' }]] });
  reads.withLayoutReadCache(() => {
    const previous = reads.readComputedStyle(element);
    reads.setOwnedProperty(element, '--owned-test', '12px');
    assert.notEqual(reads.readComputedStyle(element), previous);
    reads.setOwnedProperty(element, '--owned-test', '12px');
  });
  assert.equal(element.style.getPropertyPriority('--owned-test'), 'important');
  assert.equal(writes.propertySets, 1);
  assert.equal(calls.styles, 2);
});

test('offscreen leaf insertion conservatively invalidates CSS without reading layout in the mutation callback', () => {
  const { reads, fakeElement, createDom, calls } = harness();
  const container = createDom();
  const card = fakeElement({
    parent: container, computed: { position: 'static' },
    rect: { top: 400, bottom: 600, left: 0, right: 400, width: 400, height: 200 },
  });
  assert.equal(reads.mutationNeedsCandidateDiscovery({
    type: 'childList', target: container, addedNodes: [card], removedNodes: [],
  }), true);
  assert.equal(calls.rects, 0);
  assert.equal(calls.styles, 0);
  assert.equal(calls.points, 0);
});

test('adding a new fixed upper control requests candidate discovery', () => {
  const { reads, fakeElement, createDom } = harness();
  const container = createDom();
  const control = fakeElement({
    parent: container, computed: { position: 'fixed' },
    rect: { top: 0, bottom: 40, left: 0, right: 40, width: 40, height: 40 },
  });
  assert.equal(reads.mutationNeedsCandidateDiscovery({
    type: 'childList', target: container, addedNodes: [control], removedNodes: [],
  }), true);
});

test('unrelated attributes stay irrelevant but removed or reinserted controls retain structural CSS invalidation', () => {
  const { reads, fakeElement, createDom } = harness();
  const container = createDom();
  const flow = fakeElement({ parent: container, computed: { position: 'static' } });
  assert.equal(reads.mutationNeedsCandidateDiscovery({ type: 'attributes', target: flow }), false);
  const disconnected = fakeElement({ parent: container, computed: { position: 'fixed' } });
  disconnected.isConnected = false;
  assert.equal(reads.mutationNeedsCandidateDiscovery({
    type: 'childList', target: container, addedNodes: [disconnected],
  }), true);
});

test('root body viewport and stylesheet changes request discovery', () => {
  const { reads, fakeElement, createDom, document } = harness();
  const container = createDom();
  const viewport = fakeElement({ selectors: ['meta[name="viewport"]'] });
  const style = fakeElement({ selectors: ['style'] });
  const stylesheet = fakeElement({ selectors: ['link[rel~="stylesheet"]'] });
  for (const target of [document.documentElement, document.body, viewport, style, stylesheet]) {
    assert.equal(reads.mutationNeedsCandidateDiscovery({ type: 'attributes', attributeName: 'class', target }), true);
  }
  for (const node of [style, stylesheet]) {
    assert.equal(reads.mutationNeedsCandidateDiscovery({
      type: 'childList', target: container, addedNodes: [node], removedNodes: [],
    }), true);
    assert.equal(reads.mutationNeedsCandidateDiscovery({
      type: 'childList', target: container, addedNodes: [], removedNodes: [node],
    }), true);
  }
});

test('known offset and sticky candidate mutations retain discovery invalidation', () => {
  const { reads, fakeElement, createDom } = harness();
  const container = createDom();
  for (const register of [reads.trackOffsetCandidate, reads.trackStickyCandidate]) {
    const known = fakeElement({ parent: container });
    const child = fakeElement({ parent: known, computed: { position: 'static' } });
    register(known);
    assert.equal(reads.mutationNeedsCandidateDiscovery({ type: 'attributes', attributeName: 'class', target: known }), true);
    assert.equal(reads.mutationNeedsCandidateDiscovery({ type: 'attributes', attributeName: 'class', target: child }), true);
    assert.equal(reads.mutationNeedsCandidateDiscovery({ type: 'attributes', attributeName: 'class', target: container }), true);
  }
});

test('removing a detached known candidate or a subtree containing it requests discovery', () => {
  const { reads, fakeElement, createDom } = harness();
  const container = createDom();
  const detached = fakeElement();
  detached.isConnected = false;
  reads.trackOffsetCandidate(detached);
  assert.equal(reads.mutationNeedsCandidateDiscovery({
    type: 'childList', target: container, removedNodes: [detached], addedNodes: [],
  }), true);
  const removedSubtree = fakeElement();
  const knownChild = fakeElement({ parent: removedSubtree });
  knownChild.isConnected = false;
  reads.trackStickyCandidate(knownChild);
  assert.equal(reads.mutationNeedsCandidateDiscovery({
    type: 'childList', target: container, removedNodes: [removedSubtree], addedNodes: [],
  }), true);
});

test('nested owned sticky headers use the scrollport geometry after its ancestor anchor write', () => {
  const { reads, fakeElement, createDom, calls } = harness();
  const container = createDom();
  const originalTop = '--candy-browser-owned-sticky-original-top';
  const anchoredTop = '--candy-browser-owned-sticky-top';
  const ownedAttribute = 'data-candy-browser-top-inset-sticky';
  const outerScrollport = fakeElement({
    parent: container,
    computed: {
      position: 'sticky', overflowY: 'auto', borderTopWidth: '0px',
      transitionProperty: 'top', transitionDuration: '1s',
    },
    properties: [
      [originalTop, { value: '0px', priority: 'important' }],
      [anchoredTop, { value: '0px', priority: 'important' }],
    ],
    attributes: [[ownedAttribute, 'true']],
  });
  outerScrollport.getBoundingClientRect = () => {
    calls.rects++;
    const top = Number.parseFloat(outerScrollport.style.getPropertyValue(anchoredTop)) || 0;
    return { top, bottom: top + 400, left: 0, right: 400, width: 400, height: 400 };
  };
  const innerHeader = fakeElement({
    parent: outerScrollport,
    computed: { position: 'sticky' },
    properties: [
      [originalTop, { value: '0px', priority: 'important' }],
      [anchoredTop, { value: '48px', priority: 'important' }],
    ],
    attributes: [[ownedAttribute, 'true']],
  });
  innerHeader.getBoundingClientRect = () => {
    calls.rects++;
    const scrollportTop = outerScrollport.getBoundingClientRect().top;
    const localTop = Number.parseFloat(innerHeader.style.getPropertyValue(anchoredTop)) || 0;
    return { top: scrollportTop + localTop, bottom: scrollportTop + localTop + 64 };
  };
  reads.trackStickyCandidate(outerScrollport);
  reads.trackStickyCandidate(innerHeader);
  reads.withLayoutReadCache(() => {
    assert.equal(reads.readElementRect(outerScrollport).top, 0);
    reads.refreshStickyElements([outerScrollport, innerHeader], 48);
    assert.equal(reads.readElementRect(outerScrollport).top, 48);
    assert.equal(reads.readElementRect(innerHeader).top, 48);
  });
  assert.equal(outerScrollport.style.getPropertyValue(anchoredTop), '48px');
  assert.equal(innerHeader.style.getPropertyValue(anchoredTop), '');
  assert.equal(innerHeader.hasAttribute(ownedAttribute), false);
});

test('stable viewport sticky uses inherited CSS inset and no layout work over 1000 refreshes', () => {
  const { reads, fakeElement, createDom, calls, writes } = harness();
  const container = createDom();
  const header = fakeElement({ parent: container, computed: { position: 'sticky', top: '0px' } });
  assert.equal(reads.canUseCssStickyAnchor(header, container), true);
  reads.applyStickyTopAnchor(header, 0, 48);
  assert.match(header.style.getPropertyValue('--candy-browser-owned-sticky-top'), /^max\(/);
  assert.ok(header.style.getPropertyValue('--candy-browser-owned-sticky-top')
    .includes('var(--candy-browser-content-top-inset'));
  assert.equal(header.style.getPropertyValue('top'), 'var(--candy-browser-owned-sticky-top, 0px)');
  assert.equal(header.style.getPropertyPriority('top'), 'important');
  const baselineCalls = { ...calls };
  const baselineWrites = { ...writes };
  for (let index = 0; index < 1000; index++) reads.refreshStickyElements([header], 48);
  assert.deepEqual(calls, baselineCalls, 'CSS must own scroll movement without native DOM queries');
  assert.deepEqual(writes, baselineWrites, 'CSS must own scroll movement without JS property rewrites');
});

test('known CSS sticky headers schedule no scroll repair frame and are never visited by JS refresh', () => {
  const { reads, fakeElement, createDom, calls, writes, frames } = harness();
  const container = createDom();
  const header = fakeElement({ parent: container, computed: { position: 'sticky', top: '0px' } });
  reads.applyStickyTopAnchor(header, 0, 48);
  let connectedReads = 0;
  Object.defineProperty(header, 'isConnected', { get() { connectedReads++; return true; } });
  const baselineCalls = { ...calls };
  const baselineWrites = { ...writes };
  for (let index = 0; index < 1000; index++) reads.refreshKnownStickyElements(48);
  reads.windowScrollListener();
  assert.equal(reads.jsStickyCount(), 0);
  assert.equal(connectedReads, 0, 'CSS-owned headers must not even enter scroll-time element iteration');
  assert.equal(frames.length, 0, 'No per-scroll animation-frame callback when CSS owns all sticky movement');
  assert.deepEqual(calls, baselineCalls);
  assert.deepEqual(writes, baselineWrites);
});

test('known sticky JS membership follows nested CSS transitions and ownership cleanup', () => {
  const { reads, fakeElement, createDom } = harness();
  const container = createDom();
  const scrollport = fakeElement({ parent: container, computed: { position: 'relative', overflowY: 'auto' },
    rect: { top: 20, bottom: 220, left: 0, right: 25, width: 25, height: 200 } });
  const header = fakeElement({ parent: scrollport, computed: { position: 'sticky', top: '0px' } });
  reads.applyStickyTopAnchor(header, 0, 48);
  assert.equal(reads.jsStickyCount(), 1);
  assert.equal(header.style.getPropertyValue('--candy-browser-owned-sticky-top'), '28px');
  scrollport.computed.overflowY = 'visible';
  reads.applyStickyTopAnchor(header, 0, 48);
  assert.equal(reads.jsStickyCount(), 0);
  assert.equal(header.style.getPropertyValue('--candy-browser-owned-sticky-top'),
    'max(0px, var(--candy-browser-content-top-inset, 0px))');
  scrollport.computed.overflowY = 'auto';
  reads.applyStickyTopAnchor(header, 0, 48);
  assert.equal(reads.jsStickyCount(), 1);
  reads.clearOwnedSticky(header);
  assert.equal(reads.jsStickyCount(), 0);
  assert.equal(header.hasAttribute('data-candy-browser-top-inset-sticky'), false);
});

test('scroll from a synchronous sticky admission reaction still refreshes the newly registered JS header', () => {
  const { reads, fakeElement, createDom, frames } = harness();
  const container = createDom();
  const scrollport = fakeElement({ parent: container, computed: { position: 'relative', overflowY: 'auto' },
    rect: { top: 20, bottom: 220, left: 0, right: 25, width: 25, height: 200 } });
  const header = fakeElement({ parent: scrollport, computed: { position: 'sticky', top: '0px' } });
  const setAttribute = header.setAttribute;
  header.setAttribute = (name, value) => {
    setAttribute(name, value);
    if (name === 'data-candy-browser-top-inset-sticky') {
      scrollport.rect.top = 40;
      reads.windowScrollListener();
    }
  };
  reads.applyStickyTopAnchor(header, 0, 48);
  assert.equal(reads.jsStickyCount(), 1);
  assert.equal(frames.length, 1, 'Scroll inside an owned DOM setter must not miss subsequent JS admission');
  frames.shift()();
  assert.equal(header.style.getPropertyValue('--candy-browser-owned-sticky-top'), '8px',
    'The frame uses fresh post-reaction scrollport geometry rather than its initial 20px top');
});

test('clearing a CSS sticky anchor restores original inline top and its priority', () => {
  const { reads, fakeElement, createDom } = harness();
  const container = createDom();
  const header = fakeElement({
    parent: container,
    computed: { position: 'sticky', top: '12px' },
    properties: [['top', { value: '12px', priority: 'important' }]],
  });
  reads.applyStickyTopAnchor(header, 12, 48);
  reads.clearOwnedSticky(header);
  assert.equal(header.style.getPropertyValue('top'), '12px');
  assert.equal(header.style.getPropertyPriority('top'), 'important');
  assert.equal(header.hasAttribute('data-candy-browser-top-inset-sticky'), false);
  assert.equal(header.style.getPropertyValue('--candy-browser-owned-sticky-top'), '');
});

test('clearing ownership preserves an externally replaced author top', () => {
  const { reads, fakeElement, createDom } = harness();
  const container = createDom();
  const header = fakeElement({ parent: container, computed: { position: 'sticky', top: '0px' } });
  reads.applyStickyTopAnchor(header, 0, 48);
  header.style.setProperty('top', '80px', 'important');
  reads.clearOwnedSticky(header);
  assert.equal(header.style.getPropertyValue('top'), '80px');
  assert.equal(header.style.getPropertyPriority('top'), 'important');
});

test('nested scrollports and moving ancestors cannot use a static CSS sticky anchor', () => {
  const { reads, fakeElement, createDom } = harness();
  const root = createDom();
  for (const overflowY of ['auto', 'scroll', 'hidden', 'overlay']) {
    const scrollport = fakeElement({ parent: root, computed: { overflowY } });
    const header = fakeElement({ parent: scrollport, computed: { position: 'sticky' } });
    assert.equal(reads.canUseCssStickyAnchor(header, root), false, overflowY);
  }
  for (const transitionProperty of ['top', 'inset', 'transform', 'translate', 'all']) {
    const moving = fakeElement({
      parent: root, computed: { transitionProperty, transitionDuration: '1s' },
    });
    const header = fakeElement({ parent: moving, computed: { position: 'sticky' } });
    assert.equal(reads.canUseCssStickyAnchor(header, root), false, transitionProperty);
  }
});

test('CSS sticky revalidation declassifies an author position change without masking it', () => {
  const { reads, fakeElement, createDom } = harness();
  const container = createDom();
  const header = fakeElement({
    parent: container, computed: { position: 'sticky', top: '12px' },
    properties: [['top', { value: '12px', priority: '' }]],
  });
  reads.applyStickyTopAnchor(header, 12, 48);
  header.computed.position = 'static';
  reads.revalidateOwnedStickyAnchors(48);
  assert.equal(header.style.getPropertyValue('top'), '12px');
  assert.equal(header.hasAttribute('data-candy-browser-top-inset-sticky'), false);
});

test('owned CSS variables and translate writes cannot reopen candidate discovery', () => {
  const { reads, fakeElement } = harness();
  const authorStyle = 'color: red;';
  for (const translate of ['0', '0px']) {
    const ownedStyle = authorStyle +
      ' --candy-browser-owned-top-inset-offset: 48px !important;' +
      ` translate: ${translate} var(--candy-browser-owned-top-inset-offset, 0px) !important;`;
    assert.equal(reads.styleWithoutCandyProperties(ownedStyle), authorStyle);
    const element = fakeElement({ attributes: [['style', ownedStyle]] });
    assert.equal(reads.isRelevantLayoutMutation({
      type: 'attributes', attributeName: 'style', target: element, oldValue: authorStyle,
    }), false, 'Candy-owned offsets must not invalidate themselves');
  }
});

test('style normalization preserves author translate and real author property changes', () => {
  const { reads, fakeElement } = harness();
  const authorStyle = 'translate: 0px 20px; color: red;';
  assert.equal(reads.styleWithoutCandyProperties(authorStyle), authorStyle);
  const element = fakeElement({ attributes: [['style', 'translate: 0px 20px; color: blue;']] });
  assert.equal(reads.isRelevantLayoutMutation({
    type: 'attributes', attributeName: 'style', target: element, oldValue: authorStyle,
  }), true);
});

test('CSS sticky inline anchor is an owned mutation while external author top remains relevant', () => {
  const { reads, fakeElement, createDom } = harness();
  const parent = createDom();
  for (const priority of ['', 'important']) {
    const authorStyle = `color: red; top: 12px${priority ? ' !important' : ''};`;
    const header = fakeElement({
      parent, computed: { position: 'sticky', top: '12px' },
      properties: [['top', { value: '12px', priority }]],
    });
    reads.applyStickyTopAnchor(header, 12, 48);
    header.setAttribute('style', 'color: red; top: var(--candy-browser-owned-sticky-top, 0px) !important;' +
      ' --candy-browser-owned-sticky-top: max(12px, var(--candy-browser-content-top-inset, 0px)) !important;');
    assert.equal(reads.isRelevantLayoutMutation({
      type: 'attributes', attributeName: 'style', target: header, oldValue: authorStyle,
    }), false, 'Owned inline top must not self-invalidate');
    header.setAttribute('style', 'color: red; top: 80px !important;');
    assert.equal(reads.isRelevantLayoutMutation({
      type: 'attributes', attributeName: 'style', target: header, oldValue: authorStyle,
    }), true, 'Author top replacement must trigger protection');
  }
});

test('unrelated nonleaf feed mutations do not remove and rewrite stable CSS sticky anchors', () => {
  const { reads, fakeElement, createDom, calls, writes } = harness();
  const parent = createDom();
  const header = fakeElement({ parent, computed: { position: 'sticky', top: '0px' } });
  const feed = fakeElement({ parent, computed: { position: 'static' } });
  reads.applyStickyTopAnchor(header, 0, 48);
  const baselineCalls = { ...calls };
  const baselineWrites = { ...writes };
  for (let index = 0; index < 1000; index++) {
    const card = fakeElement({ parent: feed, computed: { position: 'static' } });
    card.childElementCount = 4;
    reads.revalidateOwnedStickyAnchors(48, [{
      type: 'childList', target: feed, addedNodes: [card], removedNodes: [],
    }]);
  }
  assert.deepEqual(calls, baselineCalls);
  assert.deepEqual(writes, baselineWrites);
});

test('CSS sticky classification crosses Shadow DOM hosts and honors their nested scrollports', () => {
  const { reads, fakeElement, createDom } = harness();
  const parent = createDom();
  const host = fakeElement({ parent, computed: { position: 'static', overflowY: 'visible' } });
  const header = fakeElement({ computed: { position: 'sticky', top: '0px' } });
  header.getRootNode = () => ({ host });
  assert.equal(reads.canUseCssStickyAnchor(header, parent), true);
  reads.applyStickyTopAnchor(header, 0, 48);
  assert.equal(header.style.getPropertyValue('top'), 'var(--candy-browser-owned-sticky-top, 0px)');
  host.computed.overflowY = 'auto';
  assert.equal(reads.canUseCssStickyAnchor(header, parent), false);
});

test('sibling insertion in a sticky ancestor revalidates structural author positioning', () => {
  const { reads, fakeElement, createDom } = harness();
  const parent = createDom();
  const header = fakeElement({ parent, computed: { position: 'sticky', top: '0px' } });
  reads.applyStickyTopAnchor(header, 0, 48);
  // A preceding sibling invalidates an author :first-child sticky rule.
  header.computed.position = 'static';
  const sibling = fakeElement({ parent, computed: { position: 'static' } });
  reads.revalidateOwnedStickyAnchors(48, [{
    type: 'childList', target: parent, addedNodes: [sibling], removedNodes: [],
  }]);
  assert.equal(header.style.getPropertyValue('top'), '');
  assert.equal(header.hasAttribute('data-candy-browser-top-inset-sticky'), false);
});

test('independent scaling rotation zoom and their transitions exclude CSS sticky anchoring', () => {
  const { reads, fakeElement, createDom } = harness();
  const parent = createDom();
  for (const [name, value] of [['scale', '0.5'], ['rotate', '20deg'], ['zoom', '0.5']]) {
    const ancestor = fakeElement({ parent, computed: { [name]: value } });
    const header = fakeElement({ parent: ancestor, computed: { position: 'sticky' } });
    assert.equal(reads.canUseCssStickyAnchor(header, parent), false, name);
    ancestor.computed[name] = name === 'zoom' ? '1' : 'none';
    ancestor.computed.transitionProperty = name;
    ancestor.computed.transitionDuration = '1s';
    assert.equal(reads.canUseCssStickyAnchor(header, parent), false, `${name} transition`);
  }
});

test('slotted headers and slotted ancestors retain the conservative JS path', () => {
  const { reads, fakeElement, createDom } = harness();
  const parent = createDom();
  const ancestor = fakeElement({ parent });
  const header = fakeElement({ parent: ancestor, computed: { position: 'sticky' } });
  header.assignedSlot = {};
  assert.equal(reads.canUseCssStickyAnchor(header, parent), false);
  header.assignedSlot = null;
  ancestor.assignedSlot = {};
  assert.equal(reads.canUseCssStickyAnchor(header, parent), false);
});

test('removing an unknown wrapper with a stylesheet requests discovery despite an offscreen leaf addition', () => {
  const { reads, fakeElement, createDom, calls } = harness();
  const container = createDom();
  const removedWrapper = fakeElement({ computed: { position: 'static' } });
  removedWrapper.isConnected = false;
  removedWrapper.childElementCount = 1;
  const removedStyle = fakeElement({ parent: removedWrapper, selectors: ['style'] });
  removedStyle.isConnected = false;
  removedWrapper.children = [removedStyle];
  const addedLeaf = fakeElement({
    parent: container, computed: { position: 'static' },
    rect: { top: 400, bottom: 600, left: 0, right: 400, width: 400, height: 200 },
  });
  assert.equal(reads.mutationNeedsCandidateDiscovery({
    type: 'childList', target: container,
    removedNodes: [removedWrapper], addedNodes: [addedLeaf],
  }), true);
  assert.deepEqual(calls, { styles: 0, rects: 0, points: 0 });
});
