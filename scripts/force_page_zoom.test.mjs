import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const script = readFileSync(new URL('../app/src/main/assets/candy_privacy/force_page_zoom.js', import.meta.url), 'utf8');

test('force zoom updates viewport and watches later changes until disabled', () => {
  let content = 'width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no';
  const viewport = {
    getAttribute: (name) => name === 'name' ? 'viewport' : content,
    setAttribute: (_, value) => { content = value; },
  };
  let notify;
  let disconnected = false;
  const context = vm.createContext({
    document: { documentElement: {}, querySelectorAll: () => [viewport] },
    MutationObserver: class {
      constructor(callback) { notify = callback; }
      observe() {}
      disconnect() { disconnected = true; }
    },
  });
  vm.runInContext(script, context);
  context.CandyForcePageZoom.setEnabled(true);
  assert.equal(content, 'width=device-width, initial-scale=1, user-scalable=yes, maximum-scale=10');
  content = 'width=device-width; maximum-scale = 1';
  notify();
  assert.equal(content, 'width=device-width, user-scalable=yes, maximum-scale=10');
  context.CandyForcePageZoom.setEnabled(false);
  assert.equal(disconnected, true);
});
