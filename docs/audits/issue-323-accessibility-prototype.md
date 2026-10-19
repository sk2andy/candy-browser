# Issue #323: status-bar scroll integration and test notes

The implementation adds an opt-in accessibility service that places a narrow, invisible touch target in the center of Android's status bar while Candy is foreground and the current page permits it. A tap requests vertical scroll offset zero from the active browser engine. The target is an `ACCESSIBILITY_OVERLAY`; it receives input instead of allowing the original touch stream to pass through.

The service declares only window state and window set change events and `canRetrieveWindowContent=false`. It reads event package names to track whether Candy is foreground, but does not retrieve view trees, text, or page content. A one-finger downward drag that begins within the target is consumed and translated to Android's public notification shade action. A two-finger downward drag is mapped to the public Quick Settings action. These actions cannot reproduce System UI's native drag progress and velocity, and the two-finger path has not been validated on a physical device. Touches beginning outside the target remain system-owned.

The target width is configurable (within a bounded range). Its slider stays visible but disabled while the feature is off, retaining the saved percentage and emitting no preview events until enabled. The feature can be disabled globally and per site/profile. Enabling it requires the user to turn on Candy's Accessibility service in Android Settings. An Android signature permission protects the in-app scroll command broadcast.

## Verification

On `emulator-5554`, after building and installing both APKs, the main-app accessibility service was enabled and bound through Android's regular Settings UI. I opened Candy's actual Gestures & controls page and dragged its production width slider from 20% to 43%. The status-bar preview visibly resized in the center of the system bar; at status-bar pixel `(350, 20)`, the screenshot changed from RGB `(251, 249, 254)` to `(182, 193, 219)` and returned to `(251, 249, 254)` after the fade. Screenshots are saved as `baseline-manual.png`, `preview-wide-manual.png`, and `faded-final.png` under `/tmp` on the test host.

The screenshot-based live preview instrumentation test was removed from the acceptance class: Android force-stops the target app package before starting `am instrument`, which also kills its enabled accessibility service while leaving its secure enabled-services entry intact. ActivityManager may still report stale service connections, so an in-test check is not reliable. The separate manual UI run validates the live service and fade behavior. The other four focused instrumentation tests cover production settings visibility, slider enable/disable behavior, independent menu visibility, and persisted per-domain state.

On the Android 16 emulator `emulator-5554`, debug APKs were built and installed for GeckoView and System WebView. With each engine on a long Wikipedia Android article, a page swipe moved the viewport down and a tap at the center of the status bar returned the viewport to the article heading. A one-finger downward drag beginning at the status bar target opened Android's notification shade for both engines. The accessibility overlay was visible in WindowManager at `[445,0][634,132]` (72dp wide on this emulator) and was owned by Candy.

An earlier Gecko gesture run produced an input-dispatch ANR. The saved bugreport showed the main thread idle in the Android message queue and an outstanding MotionEvent while the notification shade had taken focus; system CPU and I/O pressure were elevated. The trace did not identify a blocked app method, so the exact cause is not proven. The service was changed to coalesce bursts of accessibility window events and the Gecko scroll/tap and shade checks were repeated successfully afterward. Continue treating this ANR as a residual issue to monitor; the emulator run is not evidence that it cannot recur under load.

The two-finger Quick Settings mapping remains unverified. No physical-device test has been run. No production publication, release, or issue update has been made. This branch remains an implementation for user review.

Platform references: [Accessibility services](https://developer.android.com/guide/topics/ui/accessibility/service), [AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService), and [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY).

## Combined settings integration

The settings redesign integrates this patch from the common `b0cc027f` baseline. Status-bar enable and width controls share one connected group. Width remains visible but disabled while the feature is off, preserving its value and rejecting preview/save callbacks. Both controls have Settings search entries; width has localized accessibility label/value semantics. Menu availability filtering preserves stored locations and existing platform exclusions.

The accessibility overlay now clears multi-pointer tracking on pointer-up and guards pointer-count before reading pointer index 1. The remainder of that multi-touch sequence cannot be interpreted as a tap or a fresh one-finger notification gesture. This prevents an out-of-range pointer read after one finger is lifted; live multi-touch verification remains required.

The integration request included activation on the dedicated review emulator. Automatic approval review subsequently denied both service activation and installation of the updated test APK on `emulator-5832`; neither denied action was executed or retried. Current device validation remains blocked. Historical emulator references above document the originating patch only and do not validate the combined Settings revision.


## Remote PR #325 reconciliation

Reconciled the acceptance test and audit update from PR #325 head `8669c87d7f8f4d10f82b230acad13c5787b0e1b8`, based on `b0cc027f4e33b09c714fb09fca4bb2c4644b6fe4`. The live pixel test is removed, not counted as an ignored or passing automated test; live service evidence remains a separate manual check. The four remaining imported acceptance tests include disabled width and retained-value assertions.

The combined implementation retains the existing connected groups, search routes, named slider semantics, full-width stacked width header verified at 320dp/200% font, and pointer-up/index guard. The remote disabled-slider change was already covered by the combined implementation. The remote `translatable="false"` change was intentionally not adopted: the twelve strings remain translatable with the existing English/German resources and 27 additional localized resource files. No runtime code changed during this reconciliation.

After PR #325 merged, the Settings selection-page work was aligned to main commit
`ee833dc9412f4d080ff74ddced663877f091a239`. The imported #323 runtime, store and manifest are already
part of that base; the additional pointer-up/index guard remains the only service change. Live
multi-touch checks and current selection-page visual validation remain outstanding.
