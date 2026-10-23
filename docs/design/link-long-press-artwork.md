# Link long-press settings artwork

| Item | Value |
| --- | --- |
| Generation | Built-in `image_gen.imagegen`, 21 October 2026 |
| Style references | Existing `settings_approved_link_preview_final_v8.png` and `settings_approved_personal_link_rules.png` |
| Motif | Finger holding an underlined link, hold halo and connected action card |
| Original output | `/Users/sk2andy/.codex/generated_images/01a15ebd-7103-7142-8388-f31cb6ad9d8c/exec-c502200d-fd3c-4ae7-b248-323feaad75b1.png` |
| Project asset | `shared/src/commonMain/composeResources/drawable/settings_link_long_press.png` |
| Dimensions | 1536 × 1024 px, opaque PNG |
| Source window | `IntRect(0, 0, 1536, 1024)`; complete hand, wrist and action card retained |
| Processing | Original PNG copied unchanged; existing paper normalization and live Material primary/page mapping applied at draw time |
| Pixel check | Full-image border minimum 251/255, above the existing 244/255 normalization threshold |
| SHA-256 | `17c0c714a4fed777fe6f287db294313f33dca589d41d167e75e94c626eb5b18c` |
| Runtime | `SettingsHeaderIllustration(SettingsHeaderArtwork.LinkLongPress)`, standard 200 dp header capped at 360 dp wide |
| Ownership | `LinkLongPressSettingsPage` supplies the artwork through the existing gesture-choice page's preview slot; swipe actions retain their previous illustration |
| Accessibility | Decorative; labels and explanations carry meaning |
| Exact prompt | [`link-long-press-artwork-prompt.txt`](link-long-press-artwork-prompt.txt) |
