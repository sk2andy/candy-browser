# Issue 248: System WebView blob downloads and CSP

## Scope and live evidence

| Evidence | Result |
| --- | --- |
| Issue attachment filename | Ends in `dev.sk2andy.materialbrowser.systemwebview.jpg`; no readable image was attached |
| User's local Gecko test | Download works; no Gecko change is included |
| Dedicated Android emulator | Android 16 / API 36, System WebView `133.0.6943.137` |
| Signed Candy System WebView release | Version 0.45.1, official APK checksum verified |
| Gemini free, signed out | Submitted a request for a red circle on a white background; Gemini answered that image creation requires signing in |
| Original Gemini image/download trigger | Not reproduced: no signed-in image-generation session was available |

The CSP defect below is proven in a controlled fixture. Its relationship to the original Gemini
report remains unconfirmed. Do not treat the fixture as a successful live Gemini acceptance test.

## Controlled reproduction

| Scenario on unchanged production code | Result |
| --- | --- |
| Genuine touch opens a same-origin PNG blob popup without CSP | Download completes |
| Same popup with `connect-src 'self'` | Transfer reports `Network`; Chromium logs a CSP refusal for `fetch(blob:...)` |
| App-owned HTML, same origin and default profile | The original PNG can be read from another WebView |

The fixture uses a valid 2-by-1 PNG with a red and green pixel. Assertions check the exact bytes,
decoded dimensions and colors, stored MIME type, extension, and completed MediaStore row.
The popup regression uses a real touch and the production popup/download routing.

`connect-src 'self'` does not authorize a `blob:` fetch from an HTTPS/HTTP document. Candy's injected
JavaScript previously inherited that restriction. The website can display or open its image while
Candy's native transfer fails. See the [CSP source matching rules](https://www.w3.org/TR/CSP/#match-url-to-source-expression).

## Transfer ownership

```mermaid
flowchart LR
    A[Source page creates PNG blob] --> B[User opens blob popup]
    B --> C[Validate same origin]
    C --> D[Temporary app HTML with source origin and profile]
    D --> E[Bounded original-byte chunks]
    E --> F[Scoped MediaStore file]
    F --> G[Destroy helper]
    D --> H[Failure or cancellation]
    H --> G
```

The helper copies the exact source profile before any settings or navigation. Profile lookup errors
fail the transfer rather than falling back to the default profile. It blocks network, file and
content access and executes only app-owned HTML and transfer JavaScript. The source page's CSP is
unchanged. Native messages retain origin, main-frame, helper-identity, token and sequence checks;
existing size, chunk, timeout, cancellation and storage rules remain in force.

Helper cleanup runs on the main thread on every terminal outcome. `close()` destroys all live
helpers synchronously, including terminal operations awaiting posted cleanup. Repeated cleanup
is idempotent. Tests inspect real WebView destruction directly: loaded profiles cannot be assumed
immediately deletable by [ProfileStore](https://developer.android.com/reference/androidx/webkit/ProfileStore#deleteProfile(java.lang.String)).

## Verification

| Check | Coverage |
| --- | --- |
| `SystemWebViewBlobDownloadInstrumentedTest` | Native popup, CSP popup, private popup, original PNG, isolated source profile, foreign-profile refusal, revoked URL, pending storage cancellation, synchronous isolated/private helper close |
| `testFullDebugUnitTest`, `testFossDebugUnitTest` | Shared Android/JVM regressions |
| `lintSystemwebviewDebug` | Android/WebView contracts |
| `assembleSystemwebviewDebug`, `assembleSystemwebviewDebugAndroidTest` | APK and instrumentation compilation |
| Manual controlled-page download | Candy's Downloads screen shows `download.png`, Finished, 72 B / 72 B |

Run device tests only on the session's dedicated emulator and use its explicit ADB serial.
Reproduce the original Gemini issue with a signed-in free account before marking issue 248 resolved.
