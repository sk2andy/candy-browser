import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import vm from 'node:vm';

const source = readFileSync(new URL(
  '../app/src/androidTest/java/dev/sk2andy/materialbrowser/browser/EdgeToEdgeSiteFixture.kt',
  import.meta.url,
), 'utf8');
const helpers = source.split('const frame =')[1].split('const headerClass =')[0];
assert.ok(helpers.includes('const candyPolicyReady ='));

function readinessFixture({
  rootInset = '32px',
  prototypeInset = '',
  safeAreaPadding = null,
  readyAfterFrames = 0,
  nativeTopHeader = false,
  systemNativeTopInset = null,
  headerTop = 32,
  headerReadyAfterFrames = 0,
  focusedSearchVisible = false,
  focusedSearchTop = 32,
  focusedSearchReadyAfterFrames = 0,
} = {}) {
  let frames = 0;
  const delays = [];
  const header = {
    getBoundingClientRect: () => ({ top: frames < headerReadyAfterFrames ? 0 : headerTop }),
  };
  const focusedSearch = {
    hidden: !focusedSearchVisible,
    getBoundingClientRect: () => ({
      top: frames < focusedSearchReadyAfterFrames ? 0 : focusedSearchTop,
    }),
  };
  const context = vm.createContext({
    document: {
      documentElement: {
        getAttribute(name) {
          assert.equal(name, 'data-candy-browser-native-top-header');
          return nativeTopHeader ? 'true' : null;
        },
      },
      querySelector(selector) {
        if (selector === '#header') return header;
        if (selector === '#focused-search') return focusedSearch;
        assert.equal(selector, '#header.safe-area');
        return safeAreaPadding === null ? null : header;
      },
    },
    getComputedStyle(element) {
      return {
        paddingTop: element === header ? safeAreaPadding : '0px',
        getPropertyValue(name) {
          assert.ok([
            '--candy-browser-content-top-inset',
            '--candy-safe-area-inset-top',
          ].includes(name));
          if (frames < readyAfterFrames) return '';
          return name === '--candy-safe-area-inset-top' ? prototypeInset : rootInset;
        },
      };
    },
    requestAnimationFrame(callback) { frames++; callback(); },
    setTimeout(callback, delay) { delays.push(delay); callback(); },
  });
  if (systemNativeTopInset !== null) {
    context.CandySystemSafeArea = {
      configuration: () => JSON.stringify({
        nativeTopInsetPx: frames >= readyAfterFrames ? systemNativeTopInset : 0,
      }),
    };
  }
  vm.runInContext(`
    const frame = ${helpers}
    globalThis.fixture = { candyPolicyReady, settleCandyLayout, settleScrollLayout };
  `, context);
  return { context, delays, get frames() { return frames; } };
}

test('Vimeo readiness uses shared DOM without requiring an exposed extension function', async () => {
  const fixture = readinessFixture();
  assert.equal(fixture.context.__candyReconcileContentTopInset, undefined);
  await fixture.context.fixture.settleCandyLayout();
  await fixture.context.fixture.settleCandyLayout();
  await fixture.context.fixture.settleCandyLayout();
  await fixture.context.fixture.settleScrollLayout({ layout: 'LateSticky' });
  await fixture.context.fixture.settleScrollLayout({ layout: 'LateSticky' });
  assert.equal(fixture.frames, 28);
  assert.deepEqual(fixture.delays, [500, 500]);
});

test('readiness waits for actual DOM policy and retains four stabilization frames', async () => {
  const fixture = readinessFixture({ readyAfterFrames: 3 });
  await fixture.context.fixture.settleCandyLayout();
  assert.equal(fixture.frames, 7);
  assert.equal(fixture.context.fixture.candyPolicyReady(), true);
});

test('genuine engine safe-area padding is ready without Candy root CSS ownership', async () => {
  const fixture = readinessFixture({ rootInset: '', safeAreaPadding: '32px' });
  await fixture.context.fixture.settleCandyLayout();
  assert.equal(fixture.frames, 4);
  assert.equal(fixture.context.fixture.candyPolicyReady(), true);
});

test('native top-header mode is ready without Candy root CSS ownership', async () => {
  const fixture = readinessFixture({ rootInset: '', nativeTopHeader: true });
  await fixture.context.fixture.settleCandyLayout();
  assert.equal(fixture.frames, 4);
  assert.equal(fixture.context.fixture.candyPolicyReady(), true);
});

test('shared prototype readiness uses its computed CSS inset', async () => {
  const fixture = readinessFixture({ rootInset: '', prototypeInset: '32px' });
  await fixture.context.fixture.settleCandyLayout();
  assert.equal(fixture.frames, 4);
  assert.equal(fixture.context.fixture.candyPolicyReady(), true);
});

test('an available root inset waits for actual header protection before capture', async () => {
  const fixture = readinessFixture({
    rootInset: '',
    prototypeInset: '32px',
    headerReadyAfterFrames: 9,
  });
  assert.equal(Boolean(fixture.context.fixture.candyPolicyReady()), false);
  await fixture.context.fixture.settleCandyLayout();
  assert.equal(fixture.frames, 13, 'Header protection plus four stabilization frames are required');
  assert.equal(fixture.context.fixture.candyPolicyReady(), true);
});

test('focused search readiness waits for its visible surface rather than the hidden header', async () => {
  const fixture = readinessFixture({
    headerTop: 0,
    focusedSearchVisible: true,
    focusedSearchReadyAfterFrames: 7,
  });
  assert.equal(Boolean(fixture.context.fixture.candyPolicyReady()), false);
  await fixture.context.fixture.settleCandyLayout();
  assert.equal(fixture.frames, 11);
  assert.equal(fixture.context.fixture.candyPolicyReady(), true);
});

test('root-owned safe-area padding can protect a header whose border starts at zero', async () => {
  const fixture = readinessFixture({ headerTop: 0, safeAreaPadding: '32px' });
  await fixture.context.fixture.settleCandyLayout();
  assert.equal(fixture.frames, 4);
  assert.equal(fixture.context.fixture.candyPolicyReady(), true);
});

test('missing surface protection keeps readiness bounded despite an available root inset', async () => {
  const fixture = readinessFixture({ headerTop: 0 });
  await fixture.context.fixture.settleCandyLayout();
  assert.equal(fixture.frames, 124);
  assert.equal(Boolean(fixture.context.fixture.candyPolicyReady()), false);
});

test('System WebView readiness waits for its applied native top margin', async () => {
  const fixture = readinessFixture({
    rootInset: '',
    systemNativeTopInset: 96,
    readyAfterFrames: 3,
  });
  await fixture.context.fixture.settleCandyLayout();
  assert.equal(fixture.frames, 7);
  assert.equal(fixture.context.fixture.candyPolicyReady(), true);
});

test('requested native marker cannot replace an unapplied System WebView margin', async () => {
  const fixture = readinessFixture({
    rootInset: '',
    nativeTopHeader: true,
    systemNativeTopInset: 0,
  });
  await fixture.context.fixture.settleCandyLayout();
  assert.equal(fixture.frames, 124);
  assert.equal(Boolean(fixture.context.fixture.candyPolicyReady()), false);
});

test('an exposed page function cannot substitute for missing safe-area policy', async () => {
  const fixture = readinessFixture({ rootInset: '', safeAreaPadding: '0px' });
  fixture.context.__candyReconcileContentTopInset = () => {};
  await fixture.context.fixture.settleCandyLayout();
  assert.equal(fixture.frames, 124);
  assert.equal(Boolean(fixture.context.fixture.candyPolicyReady()), false);
});
