# Candy Player mode artwork

| Contract | Value |
| --- | --- |
| Generation | Built-in Imagegen, 21 October 2026 |
| Style references | Existing `settings_approved_player_waveform.png` and `settings_approved_gestures.png` |
| Art direction | Flat Material Expressive, monochrome rounded shapes, simple player symbols, white paper; no photographic scenery, perspective or shadows |
| Runtime | `SettingsPlayerModeIllustration`, shared `SettingsRasterIllustration` |
| Size | Original images share identical pixel dimensions; fixed 240 dp high slot capped at 400 dp wide, matching the former preview |
| Theme | Existing paper normalization and primary/page tonal mapping; updates with light, dark, AMOLED and palette changes |
| Selection | Explicit mapping of all five `InlineMediaPlayerMode` values; no ordinal coupling |
| Assets | Original Imagegen PNG bytes copied unchanged into shared Compose resources; full image fitted without clipping |
| Accessibility | Decorative; radio labels and explanations carry meaning |
| Verification | `PlayerModeArtworkInstrumentedTest`: mode switching, identical bounds, distinct rendered artwork, page-colored borders and 15 light/dark/AMOLED captures |

| Resource | Pixels | SHA-256 |
| --- | --- | --- |
| `settings_player_mode_disabled.png` | 1619 × 971 | `599c95b949bc414949085d8cd9c970487a214a964c51fed88e24a1a00d7b1e46` |
| `settings_player_mode_button_fullscreen.png` | 1619 × 971 | `15b8a5e17381308adbfd1f5f35a13ab203ba7498d51dd29481a20c1fed1bb1f9` |
| `settings_player_mode_button_inline_fullscreen.png` | 1619 × 971 | `cb09b6cdf0c343388676b9db3cbdfe74c9805d333296e5710613b9cefd556b32` |
| `settings_player_mode_always_fullscreen.png` | 1619 × 971 | `9ee5213c957962e24b89440c8120a0a4e79052dee42a1d747b9f37fa88d8c1d1` |
| `settings_player_mode_automatic.png` | 1619 × 971 | `a4465ec6357a116133947de9c79f2c0acce7ccac28403af57b8944f35ef7a9fb` |

## Prompts

Exact prompt set: [`player-mode-artwork-prompts.json`](player-mode-artwork-prompts.json). The inline image uses the two existing header images as references; all other modes use that inline image as their shared style/layout reference.

## Validation on 21 October 2026

| Check | Result |
| --- | --- |
| Shared Android unit tests | 342 passed, no failures or skips |
| Full / FOSS debug assembly | Both passed |
| Full / FOSS debug lint | Both passed |
| Player settings, all five modes, disabled support and double font scale | 5 existing instrumented tests passed |
| Artwork selection and live themes | New instrumented test passed; 15 distinct mode/theme captures at identical bounds |
| Device | Dedicated API 35 emulator `emulator-5846` |
| Visual review | Source images and rendered light, dark and AMOLED screenshots inspected |

The artwork test reads the unmerged semantics tree because the preview intentionally clears
accessibility semantics. No content description or interaction is added to decorative artwork.
