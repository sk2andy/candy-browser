"use strict";

// Document-local protection only: no input values, element identities, or persistent state.
(() => {
  let hasUserInput = null;
  let documentNonce = null;
  const isUntouchedEmptyBlankDocument = () => {
    if (document.URL !== "about:blank" || document.designMode !== "off" ||
        navigator.userActivation?.hasBeenActive !== false) return false;
    const root = document.documentElement;
    const head = document.head;
    const body = document.body;
    if (!root || !head || !body || root.localName !== "html" ||
        head.localName !== "head" || body.localName !== "body" ||
        root.childNodes.length !== 2 || root.childNodes[0] !== head || root.childNodes[1] !== body ||
        head.childNodes.length !== 0 || body.childNodes.length !== 0) return false;
    // shadowRoot alone hides closed roots. Firefox exposes both to extension scripts.
    return [root, head, body].every((element) =>
      element.isContentEditable === false && element.openOrClosedShadowRoot === null);
  };
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
    // Firefox injects into empty about:blank frames after document_start. Initialize only
    // an untouched empty skeleton; late populated or previously used documents stay unknown.
    if (document.readyState === "loading" || isUntouchedEmptyBlankDocument()) hasUserInput = false;
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
