# Gesture and address-bar settings artwork

| Item | Gestures | Address-bar buttons |
| --- | --- | --- |
| Generation | Built-in `image_gen.imagegen`, 21 October 2026 | Built-in `image_gen.imagegen`, 21 October 2026 |
| Motif | Hand dragging a floating address pill upward to a page edge | Star, share and menu tiles above an address bar with placement slots |
| Asset | `shared/src/commonMain/composeResources/drawable/settings_gesture_controls.png` | `shared/src/commonMain/composeResources/drawable/settings_address_bar_buttons.png` |
| Original | `/Users/sk2andy/.codex/generated_images/01a15ec1-bb77-73a1-9fb9-4a1e0bbb06dc/exec-1ecdb8de-58ef-4217-9466-3257ac018f71.png` | `/Users/sk2andy/.codex/generated_images/01a15ec1-bb77-73a1-9fb9-4a1e0bbb06dc/exec-522026cc-7bf3-4b4f-898d-a1ed8602613b.png` |
| Dimensions | 1536 × 1024 px | 1536 × 1024 px |
| Runtime window | `IntRect(180, 140, 1380, 900)` | `IntRect(80, 150, 1460, 830)` |
| Window border minimum | 253/255 | 253/255 |
| SHA-256 | `329a53c01992cad2d73ccd5d3174cf2baf63e3bb80f4f3889fd35eb3a7c507d1` | `f24fceeeae4a0e01ecdf0ffbd9341c54a86205ed3ba3ce732a5923a3c57ca03e` |
| Prompt | [Exact prompt](gesture-controls-artwork-prompt.txt) | [Exact prompt](address-bar-buttons-artwork-prompt.txt) |
| Runtime identity | `SettingsHeaderArtwork.GestureControls` | `SettingsHeaderArtwork.AddressBarButtons` |

| Contract | Ownership |
| --- | --- |
| Original pixels | Copied unchanged from generated output; only runtime source windows select the motif |
| Theme | Existing two-pass paper normalization and live Material primary/page coloring |
| Layout | Existing centered 200 dp header, maximum width 360 dp; complete hand and tiles remain inside windows |
| Navigation | `TabsAndGesturesSettingsPage` selects artwork for each section; overview retains `Gestures`, link interactions retain `LinkPreview` |
| Accessibility | Decorative only; existing labels and controls carry meaning |
| Verification | `gestureSectionsUseDistinctArtworkAcrossThemes` captures all three real pages in light, dark and AMOLED modes; checks distinct pixels, full-tone detail and seamless page borders |

| Check on 21 October 2026 | Result |
| --- | --- |
| `SettingsHeaderArtworkRulesTest` | 7 tests passed |
| `assembleFullDebug`, `assembleFossDebug` | Passed |
| `assembleFullDebugAndroidTest` | Passed after disambiguating an unrelated attached/detached preview test name |
| `gestureSectionsUseDistinctArtworkAcrossThemes` | Passed on dedicated API 35 `emulator-5892`; three pages × three themes |
| Emulator capture conditions | Initial 1080 × 2400 capture timed out and a retry process crashed; final run passed at 720 × 1600 / 280 dpi, retaining the same logical viewport |
| Lint | Not confirmed: combined run failed on the unrelated duplicate test name after 22 minutes; standalone retry interrupted after over 5 minutes waiting on concurrent compiler work |
| Capture directory | `/Users/sk2andy/.codex/visualizations/2026/10/21/01a15ec1-bb77-73a1-9fb9-4a1e0bbb06dc/gesture-artwork/settings-header-artwork/` |
