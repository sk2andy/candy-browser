# Release notes and What’s New

Candy Browser uses one versioned Markdown file for both the in-app What’s New page and the matching
GitHub Release body. This keeps the product presentation and the public release history identical.

## Release contract

| Concern | Contract |
| --- | --- |
| Source | `release-notes/<candy.versionName>.md` |
| Language | English |
| GitHub Actions | The `changelog` workflow input is required and must match the versioned path |
| Android package | Markdown and referenced screenshots are bundled as offline assets |
| Presentation | Shown once on the first regular launcher start after an app update |
| Fresh install | Uses the normal onboarding and does not show historical release notes |
| Persistence | Highest presented Android `versionCode`, stored in dedicated preferences |

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
  -f prerelease=false
```

The workflow publishes the same file with `gh release create --notes-file`; no second release summary
is generated.

Version `0.33` is the bootstrap exception: its tag and GitHub Release predate this shared-note
contract, while `release-notes/0.33.md` seeds the first bundled What’s New page. Starting with the
next release, prepare the shared Markdown file before tagging and publish it unchanged.
