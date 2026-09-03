---
name: release-changelog
description: Create or update Candy Browser's English, image-rich, versioned release notes for the in-app What's New presentation and the matching GitHub release. Use before preparing or publishing a new app version.
---

# Candy Browser release changelog

Create one polished Markdown file at `release-notes/<candy.versionName>.md`. This file is both the
in-app What's New content and the exact GitHub release body; never maintain a second release summary.

## Source the release story

- Read `candy.versionName` from `gradle.properties` and inspect changes since the previous release tag.
- Read the relevant feature documentation from `docs/README.md`; link claims to the owning README
  section rather than to implementation files.
- Describe user-visible outcomes. Omit raw commit lists, internal refactors, test counts, and features
  that are not actually shipped.
- Write in concise, natural English. Explain security limitations precisely without marketing claims
  that exceed the documented design.

## Compose the notes

Read [references/template.md](references/template.md) for the supported structure, image/link formats,
and app-renderer Markdown subset. Adapt section count and wording to the release; do not force empty
template sections.

Use one strong product screenshot when available, or at most two when each explains a different user
workflow. Prefer a focused crop of the UI concept being explained over an unrelated full-screen view.
Reuse reviewed files under `docs/screenshots/`. Do not create, edit, or capture screenshots
unless the user requested or authorized that work. Every image needs useful alt text and a tag-pinned
raw GitHub URL so the release remains stable. The matching local screenshot must exist because the
Android build embeds it for offline display.

Use tag-pinned GitHub links for README sections. Keep links useful outside the app and stable after the
default branch moves.

## Verify

Run:

```sh
./gradlew :app:validateReleaseNotes \
  -Pcandy.releaseNotesFile=release-notes/<version>.md
```

Before release dispatch, use the same path as the required workflow input:

```sh
gh workflow run release.yml \
  -f version=<version> \
  -f changelog=release-notes/<version>.md \
  -f prerelease=false
```

Do not publish, tag, push, or dispatch the workflow without explicit user authorization.
