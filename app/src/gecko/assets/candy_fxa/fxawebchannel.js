/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

// Unmodified transport from Mozilla Firefox 5866b3015de8fc803012d8ab800d8c38df1ea5e8:
// mobile/android/android-components/components/feature/accounts/src/main/assets/extensions/fxawebchannel/fxawebchannel.js

/*
Establish communication with native application.
*/
let port = browser.runtime.connectNative("mozacWebchannel");

/*
Handle messages from native application, dispatch them to FxA via an event.
*/
port.onMessage.addListener(event => {
  window.dispatchEvent(
    new CustomEvent("WebChannelMessageToContent", {
      detail: JSON.stringify(event),
    })
  );
});

/*
Handle messages from FxA. Messages are posted to the native application for processing.
*/
window.addEventListener("WebChannelMessageToChrome", function (e) {
  const detail = JSON.parse(e.detail);
  port.postMessage(detail);
});
