# Address-bar settings artwork

| Contract | Value |
| --- | --- |
| Generation | Built-in Imagegen, 21 October 2026 |
| Style references | Existing `settings_approved_gestures.png` and `settings_approved_appearance_tabs.png` |
| Motif | Classic continuous address bar above a segmented address bar; flat grayscale rounded shapes, white paper, no text |
| Runtime | `SettingsAddressBarIllustration`, shared `SettingsRasterIllustration`; Android and iOS |
| Placement | Address-bar appearance section; replaces the generic browser drawing |
| Size | Full original fitted in the former 220 dp high, maximum 400 dp wide slot; no clipping |
| Theme | Existing paper normalization and primary/page tonal mapping; follows live light, dark and AMOLED themes |
| Selection | Decorative static motif shows both styles; radio choices and persistence remain authoritative |
| Accessibility | Decorative, excluded from accessibility announcements |
| Resource | `shared/src/commonMain/composeResources/drawable/settings_address_bar.png` |
| Source | Original Imagegen PNG copied unchanged, 1536 × 1024 pixels |
| SHA-256 | `0a4f09c07fc80f7951f8a1eaefd863be3c89169435695f5b8ea9229c2ca4440e` |
| Prompt | Exact prompt in [address-bar-artwork-prompts.json](address-bar-artwork-prompts.json) |
| Verification | `AddressBarArtworkInstrumentedTest`: live theme borders and ink, fixed slot, working Classic/Segmented radio choices, full-page captures |
