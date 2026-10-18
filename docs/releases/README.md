# Release notes and What’s New

Candy Browser uses one versioned Markdown file for both the in-app What’s New page and the matching
GitHub Release body. This keeps the product presentation and the public release history identical.

## Release contract

| Concern | Contract |
| --- | --- |
| Source | `release-notes/<candy.versionName>.md` |
| Language | English |
| GitHub Actions | The `changelog` workflow input is required and must match the versioned path |
| Nightly notes | Scheduled Nightly builds generate version-matched English notes and bundle the same Markdown published in their GitHub prerelease |
| Android package | Markdown and referenced screenshots are bundled as offline assets |
| Presentation | Shown once on the first regular launcher start after an app update |
| Fresh install | Uses only the normal onboarding; bundled notes are not queued afterward |
| Startup order | Resolve onboarding before history lifecycle initialization writes browser-session preferences; first-run history markers must not trigger existing-user migration |
| Update dialog | **Release notes** opens the available version's GitHub Release in a new Candy tab and dismisses the dialog only when opening succeeds; **Download update** and **Later** remain separate actions |
| Update channels | Stable builds use GitHub's latest stable release; Nightly builds inspect a bounded release list and accept only Nightly prereleases for their installed variant |
| Persistence | Highest presented or onboarding-covered Android `versionCode`, stored in dedicated preferences |

The build fails when notes are missing, empty, too large, named for another version, or reference an
unsupported screenshot. Screenshots must be tag-pinned GitHub raw URLs that map to reviewed local
files under `docs/screenshots/`. Links remain normal HTTPS links and open through Candy Browser.

## Supported Markdown

The Android renderer intentionally supports a safe, predictable subset: level 1–3 headings,
paragraphs, ordered and unordered lists, blockquotes, dividers, fenced code blocks, bold, italic,
inline code, HTTPS links, and up to two repository screenshots. HTML and remote runtime image loading
are not supported.

## Preparing a release

Use the project skill at `.agents/skills/release-changelog/SKILL.md`. It creates concise English notes,
selects useful reviewed screenshots, links to stable README sections, and validates the result.

After updating `candy.versionName` and `candy.versionCode`, validate the notes:

```bash
./gradlew :app:validateReleaseNotes \
  -Pcandy.releaseNotesFile=release-notes/<version>.md
```

Then provide that exact file when dispatching the release workflow:

```bash
gh workflow run release.yml \
  -f version=<version> \
  -f changelog=release-notes/<version>.md \
  -f prerelease=false \
  -f play_track=alpha
```

Google Play publication follows the GitHub Release by default. Pass `-f publish_play=false` for a
GitHub-only release; the workflow still builds, signs and verifies the GitHub APKs.

The workflow publishes the same file with `gh release create --notes-file`; no second release summary
is generated.

Version `0.33` is the bootstrap exception: its tag and GitHub Release predate this shared-note
contract, while `release-notes/0.33.md` seeds the first bundled What’s New page. Starting with the
next release, prepare the shared Markdown file before tagging and publish it unchanged.

## Nightly publication

| Concern | Contract |
| --- | --- |
| Schedule | Daily at `00:00 UTC`, fixed `02:00 CEST`; `01:00 CET` during winter. GitHub scheduling may delay the actual start |
| Source | Exact checked-out `main` commit |
| Skip condition | Checked-out SHA matches the tag of the last successfully published Nightly |
| Tag and version | Immutable `v<base-version>-nightly.<UTC YYYYMMDDHHmm>` tag; APK version name omits leading `v`; a stable release-candidate suffix is replaced by the Nightly suffix |
| Version code | UTC minutes since `2020-01-01T00:00:00Z`, independent of the stable version code |
| Signing | Existing release signing key and pinned certificate; optimized release-derived build types |
| GitHub publication | Prerelease, explicitly excluded from the latest stable release; publish after signed APK checks succeed |
| Variants | Standard universal, standard ARM64, FOSS, System WebView, and User CA |
| Asset names | `CandyBrowser-v<base-version>-nightly.<UTC YYYYMMDDHHmm>-<variant-suffix>.apk` plus SHA-256 checksums; suffixes match stable APKs |
| Package IDs | GitHub APKs: stable variant ID plus `.nightly`; Play Nightly: standard package updates existing Candy |
| Update ordering | Nightly timestamp determines recency, independently of the base version |
| FOSS updates | In-app GitHub updater remains disabled; Obtainium can track stable or Nightly FOSS APKs |
| Stable FOSS assets | Ordinary GitHub releases use `-foss-github-release.apk`; the separate reproducible F-Droid reference retains `-foss-release.apk`. Stable Obtainium accepts both compatible package/signature choices; Nightly FOSS uses only `-foss-release.apk` |
| Google Play | Explicit reusable workflow call publishes standard-package Play bundle to closed `alpha`; GitHub Nightly release events remain ignored |
| Failure | Failed builds do not advance the successfully published SHA marker; later runs retry changed `main` |

The [download and Obtainium guide](../../README.md#nightly-builds) lists each package and provides
filtered setup links for every variant and channel. Nightly links enable prereleases and filter
Nightly release titles; stable links exclude prereleases. Both fix the expected APK suffix so an
update cannot switch between FOSS, System WebView, standard, and User CA packages.

Regenerate the Obtainium table with `python3 scripts/obtainium_links.py`. Verify the encoded settings
and variant isolation in the actual README links with `python3 scripts/test_obtainium_links.py`.

Nightly notes are generated for that build rather than using stable notes with another version
header. They follow the same supported Markdown and offline asset validation rules. The generated
file is the exact GitHub prerelease body and packaged What's New content.

## Play test and version ordering

| Concern | Contract |
| --- | --- |
| Invitation | [Join tester group](https://groups.google.com/g/candy-browser-testers), then [opt in with same Google account](https://play.google.com/apps/testing/dev.sk2andy.materialbrowser) |
| Test track | Existing closed `alpha`; group self-join allowed, membership list visible only to owner |
| Play identity | `fullPlayRelease`, standard application ID, `candy.playNightly=true`, GitHub updater disabled |
| Nightly metadata | Same timestamp/version code and exact GitHub release body; short generated DE/EN Play notes |
| Stable code | Store current UTC minutes since 2020-01-01 in `candy.versionCode` at release preparation; helper: [`release_version_code.py`](../../scripts/release_version_code.py) |
| Ordering | Stable code strictly above latest published Nightly; Nightly code strictly above source stable code; shared publication lock |
| Retry | Rerun failed Play job or manually publish exact Nightly version with `nightly=true`, `alpha`, `completed`; unchanged main skips rebuilding |
| Availability | Google processing/review may delay delivery; leaving test requires waiting for a newer stable version or reinstalling |

## First Play permission declaration and manual recovery

Google requires a [Permissions Declaration Form](https://support.google.com/googleplay/android-developer/answer/9214102)
for restricted permissions. The initial API upload can fail at commit while
`REQUEST_INSTALL_PACKAGES` is undeclared. Complete the first release in Play Console and submit
the declaration for [user-initiated APK installation during web browsing](https://support.google.com/googleplay/android-developer/answer/12085295).
The store descriptions document this download-and-open flow; Play builds keep Candy's GitHub
self-updater disabled. Google must review the declaration before publication can proceed.

| Step | Action |
| --- | --- |
| Retrieve | Download `candy-google-play-signed-<run_id>-<run_attempt>` from the publishing run within three days; it is saved after signing and certificate verification, before Google authentication or API upload |
| Verify | Extract the artifact and run `sha256sum --check SHA256SUMS`; these checksums cover the signed AAB and included R8 mapping, unlike the earlier unsigned build artifact |
| Upload | Upload `CandyBrowser-v<version>-play.aab` to the intended existing test track in Play Console; attach `mapping.txt` if present |
| Declare | Complete the permissions declaration for the actual browser download flow and submit the release for Google review |
| Resume | After approval, later versions can use the normal automated workflow; inspect Play Console before retrying a version that may already be uploaded |

The recovery artifact contains only the signed AAB, optional `mapping.txt`, and `SHA256SUMS`.
Keystores, passwords, and Google credentials are excluded. It is an authenticated Actions download,
not a GitHub Release asset. If signing or verification fails, no recovery artifact is created.
