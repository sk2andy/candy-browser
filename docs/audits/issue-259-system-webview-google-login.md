# System WebView Google login popup preservation

| Scope | Evidence |
| --- | --- |
| Issue | [#259](https://github.com/sk2andy/candy-browser/issues/259) |
| Report | Figma Google sign-in reports `Unable to get profile information from Google`; GitHub Google sign-in works |
| Reported engine | System WebView on Android 16 |
| Controlled reproduction | Real user-tap popup, opener-bound `postMessage`, native `window.close()` and form-target POST on a local fixture |
| Physical device | Pixel briefly appeared over USB, then disconnected from ADB; live signed-in Figma verification remains pending |

## Cause and correction

| Boundary | Before | After |
| --- | --- | --- |
| Native popup | A JavaScript-disabled temporary WebView captures the first URL, forwards a new-window navigation and destroys the actual native child. The original opener channel and request cannot survive this replacement. | The System factory consumes a one-use prepared-session slot. The controller admits a child in the opener's exact profile/private context and supplies its configured WebView directly through `WebViewTransport`; no URL replay. |
| POST and blank windows | URL capture cannot preserve form-target POST or an initially blank window that navigates later. | Keep blank children pending under the existing bounded timeout. Route the first nonblank native navigation once; use `onPageStarted` for initial POST because WebView skips `shouldOverrideUrlLoading` for POST. |
| Native close | `onCloseWindow` is not forwarded. | Forward to the existing controller close grant, which rechecks the exact current native session and returns to the opener. Manual and recreated sessions have no native close grant. |
| Scoped login offer | Allowed Google SDK requests produce no compatibility observation. The existing user-agent compatibility helper has no System WebView production caller. | Observe known compatibility hosts; recognized HTTPS Google SDK requests reach the existing consent prompt. Only login consent changes the user agent. CAPTCHA/site pause never grants that change. |
| Native identity lifetime | Cookie and user-agent policy cannot inherit the opener's login consent. | Native children inherit consent while blank or on the exact HTTPS Google Accounts host, including chooser redirects outside the initial authentication path. Ordinary site policy resumes after leaving the provider. |
| User-agent transition | Changing WebView's user agent while native popup loading begins can restart the POST and drop its referrer, even before `onPageStarted` is delivered. | Mark loading before transport binding. Keep the gate through blank-window bootstrap, ignore stale completion URLs and defer the identity update until a posted, matching completed navigation. The granted-login regression requires exactly one POST with the original body/referrer/opener. |
| Popup downloads | Standalone Blob capture already transfers through the owning document. | Preserve that behavior for controller-adopted Blob windows, transfer through the opener and remove the temporary child. Filter observations remain attributed to the opener. |

```mermaid
sequenceDiagram
    participant Site
    participant Candy
    participant Google
    Site->>Candy: User-triggered native window
    Candy->>Candy: Admit child in opener profile/private context
    Candy->>Google: Bind exact child WebView; preserve opener and request
    Google->>Site: Deliver result through opener
    Google->>Candy: window.close / onCloseWindow
    Candy->>Candy: Recheck current native session; remove child
    Candy->>Site: Return to opener
```

## Verification

| Check | Result |
| --- | --- |
| Unmodified OAuth fixture | Failed: no adopted native popup appears |
| FullDebug JVM | 1,783 tests, zero failures/errors/skips |
| FossDebug JVM | 1,783 tests, zero failures/errors/skips |
| SystemwebviewDebug JVM | 1,783 tests, zero failures/errors/skips |
| All three debug assemblies | Passed |
| All three debug lint variants | Passed on the final rebased sources |
| Final Android popup and Blob regressions | 14 tests passed on API 36 / System WebView 133.0.6943.137 |
| Code/style review | Native adoption and existing controller boundaries reviewed by separate agents |
| Live signed-in Figma login | Pending reachable Pixel; controlled fixtures establish the browser defects, not a live Figma account result |

Android tests use the session-owned API 36 `codex_issue259_root` emulator with explicit
`adb -s emulator-5592`. No other agent uses this emulator or a physical device. Coverage includes
normal/private OAuth completion, delayed blank windows, ordinary/granted POST, exact JPEG Blob
bytes, no-gesture/always-block admission, manual/stale close rejection, real WebViewClient SDK
observation and tab-scoped cookie/user-agent grant/revocation.

WebView does not provide a pre-navigation POST override callback. The POST popup fallback runs
at native page-start; URL filtering there cannot promise rejection before the first network request.
No authentication cookies, tokens, account identifiers or login-page captures are included here.

| Android contract | Primary source |
| --- | --- |
| Native popup transport | [Chromium popup implementation](https://chromium.googlesource.com/chromium/src/+/refs/tags/139.0.7258.135/android_webview/docs/how-does-on-create-window-work.md) |
| New-window and close callbacks | [WebChromeClient](https://developer.android.com/reference/android/webkit/WebChromeClient) |
| POST override limitation | [WebViewClient.shouldOverrideUrlLoading](https://developer.android.com/reference/android/webkit/WebViewClient#shouldOverrideUrlLoading(android.webkit.WebView,%20android.webkit.WebResourceRequest)) |
| User-agent changes during loading | [WebSettings.setUserAgentString](https://developer.android.com/reference/android/webkit/WebSettings#setUserAgentString(java.lang.String)) |
