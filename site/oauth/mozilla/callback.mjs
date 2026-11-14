const callbackParameters = ["code", "state", "keys", "error", "error_description", "error_uri", "action"];

export function mozillaAppCallback(search) {
  const parameters = new URLSearchParams(search);
  const code = parameters.get("code");
  const error = parameters.get("error");
  if (!parameters.get("state") || Boolean(code) === Boolean(error) ||
      callbackParameters.some((name) => parameters.getAll(name).length > 1)) {
    return null;
  }
  const callback = new URL("dev.sk2andy.materialbrowser://oauth/callback");
  for (const name of callbackParameters) {
    if (parameters.has(name)) callback.searchParams.set(name, parameters.get(name));
  }
  return { url: callback.href, failed: Boolean(error) };
}

export function showMozillaCallback(page, document) {
  const callback = mozillaAppCallback(page.location.search);
  // Keep the one-time response out of this page's history entry and subsequent copied URLs.
  page.history.replaceState(null, "", page.location.pathname);
  const title = document.getElementById("callback-title");
  const message = document.getElementById("callback-message");
  if (!callback) {
    title.textContent = "Start sign-in in Candy";
    message.textContent = "This page has no complete sign-in response. Return to Candy and start Mozilla sign-in again.";
    return;
  }
  title.textContent = callback.failed ? "Sign-in was not completed" : "Continue in Candy";
  message.textContent = callback.failed
    ? "Return to Candy to close this sign-in attempt. You can try again from Sync settings."
    : "Open Candy on this device to finish connecting Mozilla Sync.";
  const link = document.getElementById("open-candy");
  link.href = callback.url;
  link.hidden = false;
  document.getElementById("callback-help").textContent =
    "If Candy does not open, check that it is installed on this Android device, then tap Open Candy again. On another device, start sign-in from Candy instead.";
}

if (typeof window !== "undefined") showMozillaCallback(window, document);
