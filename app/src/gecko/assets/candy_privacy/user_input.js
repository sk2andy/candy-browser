"use strict";

// Document-local protection only: no input values, element identities, or persistent state.
(() => {
  let hasUserInput = null;
  let documentNonce = null;
  try {
    const bytes = new Uint8Array(16);
    crypto.getRandomValues(bytes);
    documentNonce = Array.from(bytes, (value) => value.toString(16).padStart(2, "0")).join("");
    const recordInput = (event) => {
      if (event.isTrusted === true) hasUserInput = true;
    };
    for (const type of ["beforeinput", "input", "change"]) {
      // Capture observes composed shadow events after retargeting, without reading their target.
      document.addEventListener(type, recordInput, true);
    }
    // A late registration cannot prove that earlier user input was absent.
    if (document.readyState === "loading") hasUserInput = false;
  } catch (_error) { }
  globalThis.CandyUserInput = Object.freeze({
    sample(nonce, expectedDocumentNonce) {
      return {
        nonce,
        documentNonce,
        hasUserInput: expectedDocumentNonce && expectedDocumentNonce !== documentNonce
          ? null : hasUserInput,
      };
    },
  });
  browser.runtime.onMessage.addListener((message) => {
    if (message?.type === "user-input-query" && typeof message.nonce === "string") {
      return Promise.resolve(globalThis.CandyUserInput.sample(message.nonce, message.documentNonce));
    }
    return undefined;
  });
})();
