(() => {
  "use strict";
  if (self !== top) return;
  let policy = null;
  const drafts = CandyFormDraftFactory.create(window, payload => {
    if (policy?.formDraftSavingEnabled !== true) return;
    browser.runtime.sendMessage({ type: "form-draft-snapshot", revision: policy.revision,
      navigationGeneration: policy.navigationGeneration, payload }).catch(() => {});
  });
  function configure(next) {
    policy = next;
    drafts.setEnabled(next?.formDraftSavingEnabled === true);
  }
  browser.runtime.onMessage.addListener(message => {
    if (message?.type === "content-policy") configure(message);
    if (message?.type === "form-draft-restore" && policy?.formDraftSavingEnabled === true &&
        policy.revision === message.revision && policy.navigationGeneration === message.navigationGeneration) {
      return Promise.resolve(drafts.restore(message.payload));
    }
    return undefined;
  });
  browser.runtime.sendMessage({ type: "content-policy-request" }).then(configure).catch(() => {});
})();
