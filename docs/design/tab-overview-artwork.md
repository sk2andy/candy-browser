# Tab overview artwork

| Contract | Value |
| --- | --- |
| Generation | Built-in Imagegen, 21 October 2026 |
| Style reference | Existing `settings_player_mode_button_inline_fullscreen.png`; Coverflow then supplies the common reference for Grid and List |
| Art direction | Flat Material Expressive, rounded monochrome browser cards and list rows, white paper, no readable text |
| Motifs | Coverflow: three overlapping cards; Grid: four cards in two columns; List: five rows without thumbnails |
| Runtime | Shared `TabOverviewSelectionPage`, existing `SettingsRasterIllustration` |
| Size | Existing 260 dp high preview slot capped at 400 dp wide; complete originals fitted without clipping |
| Theme | Existing paper normalization and primary/page tonal mapping follows light, dark, AMOLED and palette changes |
| Selection | Explicit `TabOverviewMode` mapping; overview and stack-folder selectors share the same artwork |
| Assets | Original Imagegen PNG bytes copied unchanged into shared Compose resources |
| Accessibility | Decorative; existing radio labels expose mode and selection |
| Verification | `TabOverviewArtworkInstrumentedTest` checks mode switching, fixed bounds, distinct artwork and page-colored corners in three live themes; existing `TabOverviewSelectionInstrumentedTest` covers Back and large text |

| Resource | Pixels | SHA-256 |
| --- | --- | --- |
| `settings_tab_overview_coverflow.png` | 1619 × 971 | `8c6e6e4b2cec390742a45c95bc7cecc7cc6c8020498213bc167a18972f36ab29` |
| `settings_tab_overview_grid.png` | 1619 × 971 | `1d658c0bc0a079a8c05f76184ba637b8eb01443d051744d7e6ccf5f92387384c` |
| `settings_tab_overview_list.png` | 1620 × 971 | `4d503494d4b1399a8332604f0b93b664c8ce9a09be7ff8f47bc7d0f10c693abd` |

Exact prompt set: [`tab-overview-artwork-prompts.json`](tab-overview-artwork-prompts.json).
The source list image is one pixel wider; the shared renderer preserves its aspect ratio inside
the same preview bounds. Instrumented screenshots are written to the app's external-files
`tab-overview-artwork` directory.

## Validation on 21 October 2026

| Check | Result |
| --- | --- |
| Shared Android unit tests | 344 passed |
| Full / FOSS unit tests | 2,092 passed in each variant; no failures or skips |
| Full / FOSS debug assembly | Both passed |
| Artwork, selection, Back and 320 dp / double font scale | All three instrumented tests passed on dedicated API 35 emulator `emulator-5852` |
| Live theme rendering | Nine mode/theme captures; fixed preview bounds, distinct motifs and page-colored corners passed |
| Visual review | All three light motifs plus dark Coverflow and AMOLED Grid inspected |
| Full / FOSS lint | Not completed; full-project analysis was stopped after several minutes without completion |
| Style review | Separate read-only agent review found no actionable issues |

Verification used an isolated source copy because concurrent builds were overwriting the shared
checkout's intermediate files. The original Android test compilation was also blocked by an
unrelated duplicate method in `ExternalLinkPreviewSafeAreaInstrumentedTest`. Only the two tab
overview test sources were retained in the isolated copy; repository test files were preserved.
The temporary test build used in-process Kotlin compilation with `-Xno-optimize` after prolonged
bytecode optimization. No project compiler setting was changed.

The Gradle-connected run reported the artwork test alone. A subsequent direct
`AndroidJUnitRunner` invocation explicitly selected both test classes and reported `OK (3 tests)`;
its nine screenshots were copied before stopping the dedicated emulator.
