# Google Cast agent guide

Candy can send compatible direct HTML5 video streams from regular tabs to Google Cast devices.
This is remote media playback through the Google Cast Android Sender SDK, not tab or screen
mirroring.

## Supported path

| Stage | Owner | Contract |
| --- | --- | --- |
| Media discovery | `WebMediaBridgeScript` | Reports bounded `currentSrc`, selected source MIME type and poster URL with existing media identity |
| Eligibility | `CastMediaRules` | Accepts selected regular-tab video with direct HTTP(S) MP4, WebM, HLS or DASH source |
| Device selection | `CastRouteButton` | Uses the SDK-owned Cast route chooser |
| Remote playback | `CastSessionController` | Loads the exact accepted candidate into the Default Media Receiver and publishes remote state |
| Local handoff | `BrowserController` | Pauses only the still-current matching WebView media endpoint after remote load succeeds |
| Browser controls | `CastMiniController` and expanded Compose sheet | Shows title, device, seek, volume, play/pause, device switch and disconnect above browser chrome |

`MainActivity` must remain an `AppCompatActivity` with an AppCompat-based theme: AndroidX
`MediaRouteButton` presents its route chooser through the support fragment manager and its chooser
is an AppCompat dialog. A plain `ComponentActivity` or framework Material theme crashes on click.

## Privacy and identity

- Private tabs never produce a Cast candidate or expose their media metadata to the Cast SDK.
- URLs and metadata remain memory-only and are sent to the chosen Cast device only after explicit
  user interaction.
- A candidate binds tab, navigation generation, document, media element and origin. Late remote-load
  success cannot pause a replacement player or another tab.
- Switching to a private tab hides Candy's remote media metadata and mini-controller. An existing
  external Cast session remains reachable through the SDK route button so the user can disconnect.
- Never log or persist Cast source URLs. They can contain short-lived access tokens.
- Google documents that the Sender SDK collects Cast-device interaction activity and can send parts
  of it to Google's logging service. Keep store/privacy disclosures aligned with the SDK's current
  data-safety documentation.

## Compatibility limits

The Default Media Receiver fetches the media itself. It does not inherit WebView cookies, request
headers or authenticated browser state. Blob/data URLs, Media Source Extensions, unsupported DRM,
expired signed URLs and sites that conceal the direct stream are not castable. Local playback is
left untouched when the receiver rejects a load.

The project pins `play-services-cast-framework` 21.4.0 because newer releases contain Kotlin 2.1+
metadata that the current Kotlin 1.9 compiler cannot consume. Upgrade the app toolchain before
upgrading the Cast SDK; never suppress Kotlin metadata compatibility checks.

## Verification

| Change | Minimum check |
| --- | --- |
| Candidate or URL/MIME policy | `CastMediaRulesTest` and `WebMediaContractTest` |
| Bridge source reporting | Focused `WebMediaBridgeInstrumentedTest` on dedicated API 34+ emulator |
| Compose controls | `CastControlsInstrumentedTest` on the same dedicated emulator |
| SDK, manifest or resources | `lintDebug assembleDebug` |
| Store release | Re-review Google Cast SDK Data Safety disclosure before publishing |
| Receiver behavior | Manual smoke test with a physical Cast device and public MP4/HLS/DASH samples |
