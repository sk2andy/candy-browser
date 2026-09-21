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
  safeAreaPadding = null,
  readyAfterFrames = 0,
  nativeTopHeader = false,
} = {}) {
  let frames = 0;
  const delays = [];
  const context = vm.createContext({
    document: {
      documentElement: {
        getAttribute(name) {
          assert.equal(name, 'data-candy-browser-native-top-header');
          return nativeTopHeader ? 'true' : null;
        },
        style: {
          getPropertyValue(name) {
            assert.equal(name, '--candy-browser-content-top-inset');
            return frames >= readyAfterFrames ? rootInset : '';
          },
        },
      },
      querySelector(selector) {
        assert.equal(selector, '#header.safe-area');
        return safeAreaPadding === null ? null : {};
      },
    },
    getComputedStyle() { return { paddingTop: safeAreaPadding }; },
    requestAnimationFrame(callback) { frames++; callback(); },
    setTimeout(callback, delay) { delays.push(delay); callback(); },
  });
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

test('an exposed page function cannot substitute for missing safe-area policy', async () => {
  const fixture = readinessFixture({ rootInset: '', safeAreaPadding: '0px' });
  fixture.context.__candyReconcileContentTopInset = () => {};
  await fixture.context.fixture.settleCandyLayout();
  assert.equal(fixture.frames, 124);
  assert.equal(Boolean(fixture.context.fixture.candyPolicyReady()), false);
});
