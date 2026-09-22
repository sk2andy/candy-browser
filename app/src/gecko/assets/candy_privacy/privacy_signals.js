"use strict";

(function installPrivacySignalHelpers(global) {
  function requestHeaders(currentHeaders, policy) {
    const names = new Set(["dnt", "sec-gpc"]);
    const headers = Array.isArray(currentHeaders) ?
      currentHeaders.filter((header) =>
        typeof header?.name !== "string" || !names.has(header.name.toLowerCase()),
      ) : [];
    if (policy?.doNotTrackEnabled === true) {
      headers.push({ name: "DNT", value: "1" });
    }
    if (policy?.globalPrivacyControlEnabled === true) {
      headers.push({ name: "Sec-GPC", value: "1" });
    }
    return headers;
  }

  function installDocumentSignals(target, policy) {
    const targetNavigator = target?.navigator;
    if (!targetNavigator) return false;
    const revision = Number.isSafeInteger(policy?.privacySignalRevision) &&
      policy.privacySignalRevision >= 0 ? policy.privacySignalRevision : 0;
    const revisionKey = "__candyPrivacySignalRevision";
    try {
      const descriptor = Object.getOwnPropertyDescriptor(targetNavigator, revisionKey);
      if (Number.isSafeInteger(descriptor?.value) && descriptor.value > revision) return false;
    } catch (_) { }
    const defineSignal = (name, value) => {
      try {
        Object.defineProperty(targetNavigator, name, {
          configurable: true,
          enumerable: true,
          value,
          writable: false,
        });
      } catch (_) { }
    };
    defineSignal("doNotTrack", policy?.doNotTrackEnabled === true ? "1" : null);
    defineSignal("globalPrivacyControl", policy?.globalPrivacyControlEnabled === true);
    try {
      Object.defineProperty(targetNavigator, revisionKey, {
        configurable: true,
        enumerable: false,
        value: revision,
        writable: false,
      });
    } catch (_) { }
    return true;
  }

  function registrationCode(policy, revision) {
    const safeRevision = Number.isSafeInteger(revision) && revision >= 0 ? revision : 0;
    const doNotTrack = policy?.doNotTrackEnabled === true ? '"1"' : "null";
    const globalPrivacyControl = policy?.globalPrivacyControlEnabled === true;
    return `(() => {
  "use strict";
  const page = globalThis.wrappedJSObject || globalThis;
  const pageNavigator = page.navigator;
  if (!pageNavigator) return;
  const revisionKey = "__candyPrivacySignalRevision";
  let installedRevision = -1;
  try {
    const descriptor = Object.getOwnPropertyDescriptor(pageNavigator, revisionKey);
    if (Number.isSafeInteger(descriptor?.value)) installedRevision = descriptor.value;
  } catch (_) {}
  if (installedRevision > ${safeRevision}) return;
  try {
    Object.defineProperty(pageNavigator, "doNotTrack", {
      configurable: true,
      enumerable: true,
      value: ${doNotTrack},
      writable: false,
    });
  } catch (_) {}
  try {
    Object.defineProperty(pageNavigator, "globalPrivacyControl", {
      configurable: true,
      enumerable: true,
      value: ${globalPrivacyControl},
      writable: false,
    });
  } catch (_) {}
  try {
    Object.defineProperty(pageNavigator, revisionKey, {
      configurable: true,
      enumerable: false,
      value: ${safeRevision},
      writable: false,
    });
  } catch (_) {}
})();`;
  }

  global.CandyPrivacySignals = { installDocumentSignals, registrationCode, requestHeaders };
})(globalThis);
