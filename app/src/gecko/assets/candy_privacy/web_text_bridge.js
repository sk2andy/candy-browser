(() => {
  "use strict";
  if (self !== top) return;
  let policy = null;
  function configure(next) {
    if (!next || !Number.isSafeInteger(next.revision) || !Number.isSafeInteger(next.navigationGeneration)) return;
    if (!policy || next.revision >= policy.revision) policy = next;
  }
  browser.runtime.onMessage.addListener(message => {
    if (message?.type === 'content-policy') configure(message);
    if (message?.type !== 'web-text-operation' || !policy ||
        policy.revision !== message.revision || policy.navigationGeneration !== message.navigationGeneration) return undefined;
    return Promise.resolve(CandyWebTextOperations.apply(message.payload));
  });
  browser.runtime.sendMessage({ type: 'content-policy-request' }).then(configure).catch(() => {});
})();
