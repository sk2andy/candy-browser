# F-Droid submission

> Licensing update (2026-10-25): PolyForm-licensed snapshots are not eligible
> for the official F-Droid repository. The instructions below document the
> earlier MPL-licensed distribution; see [licensing](../../LICENSING.md).


Candy's `foss` flavor is the only variant intended for the official F-Droid repository. It uses the
same application ID as the GitHub build but excludes Google Play services, Google Cast, Google Code
Scanner, and the GitHub self-updater. New FOSS installs keep remote search suggestions disabled
until the user explicitly chooses a provider.

## First submission

1. Merge these changes and run the `Release Android APK` workflow for `0.31`. The workflow must
   create tag `v0.31` from the exact commit that contains `candy.versionName=0.31` and
   `candy.versionCode=31000`.
2. Resolve the tag to its immutable commit:

   ```bash
   git rev-list -n 1 v0.31
   ```

3. Copy `dev.sk2andy.materialbrowser.yml` into a fork of `fdroid/fdroiddata` as
   `metadata/dev.sk2andy.materialbrowser.yml`. Replace `REPLACE_WITH_V0.31_COMMIT_SHA` with the
   40-character result from step 2.
4. Validate from the fdroiddata checkout:

   ```bash
   fdroid readmeta
   fdroid rewritemeta dev.sk2andy.materialbrowser
   fdroid checkupdates dev.sk2andy.materialbrowser
   fdroid lint dev.sk2andy.materialbrowser
   fdroid build -v -l dev.sk2andy.materialbrowser
   ```

5. Open a merge request against `fdroid/fdroiddata`. Include the successful local-build log and
   explain that the `full` flavor contains optional Google integrations while `foss` resolves no
   Google/Firebase/ML Kit runtime dependencies.

The metadata deliberately declares no AntiFeatures: the FOSS flavor performs no automatic update
or search-suggestion request on a new install. If that default changes, reassess F-Droid's
`Tracking` AntiFeature before the next submission.

## Signing boundary

The first submission uses normal F-Droid signing. Users cannot update directly between an
F-Droid-signed installation and the upstream GitHub/Obtainium installation. Reproducible builds
with upstream signature verification can be evaluated later; do not add `Binaries` or
`AllowedAPKSigningKeys` until byte-for-byte reproduction has been proven independently.

## Release maintenance

For every release, update these source-owned values before tagging:

- `candy.versionName` and `candy.versionCode` in `gradle.properties`
- localized `fastlane/metadata/android/*/changelogs/<versionCode>.txt`
- the newest `Builds` block, `CurrentVersion`, and `CurrentVersionCode` in fdroiddata

`UpdateCheckMode: Tags` reads version metadata from `gradle.properties`; stable tags must match
`v<major>.<minor>` or `v<major>.<minor>.<patch>`.
