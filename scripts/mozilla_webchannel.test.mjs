import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const source = readFileSync(new URL('../app/src/gecko/assets/candy_fxa/fxawebchannel.js', import.meta.url), 'utf8');
const manifest = JSON.parse(readFileSync(new URL('../app/src/gecko/assets/candy_fxa/manifest.json', import.meta.url), 'utf8'));

test('Mozilla WebChannel forwards the unchanged account envelope in both directions', () => {
  let fromNative;
  let fromPage;
  let nativeMessage;
  let contentEvent;
  const context = {
    browser: { runtime: { connectNative(name) {
      assert.equal(name, 'mozacWebchannel');
      return {
        onMessage: { addListener(listener) { fromNative = listener; } },
        postMessage(message) { nativeMessage = message; },
      };
    } } },
    window: {
      addEventListener(name, listener) {
        assert.equal(name, 'WebChannelMessageToChrome');
        fromPage = listener;
      },
      dispatchEvent(event) { contentEvent = event; },
    },
    CustomEvent: class { constructor(type, options) { this.type = type; this.detail = options.detail; } },
  };
  vm.runInNewContext(source, context);
  const request = { id: 'account_updates', message: { command: 'fxaccounts:fxa_status', messageId: 'test' } };
  fromPage({ detail: JSON.stringify(request) });
  assert.equal(JSON.stringify(nativeMessage), JSON.stringify(request));
  const response = { id: 'account_updates', message: { command: 'fxaccounts:fxa_status', messageId: 'test', data: { signedInUser: null } } };
  fromNative(response);
  assert.equal(contentEvent.type, 'WebChannelMessageToContent');
  assert.deepEqual(JSON.parse(contentEvent.detail), response);
});

test('extension targets only the production account top frame outside private browsing', () => {
  assert.equal(manifest.incognito, 'not_allowed');
  assert.deepEqual(manifest.content_scripts[0].matches, ['https://accounts.firefox.com/*']);
  assert.equal(manifest.content_scripts[0].all_frames, false);
  assert.equal(manifest.content_scripts[0].run_at, 'document_start');
  assert.ok(manifest.permissions.includes('mozillaAddons'));
  assert.ok(manifest.permissions.includes('nativeMessagingFromContent'));
});
