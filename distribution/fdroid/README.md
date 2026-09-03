# F-Droid submission

> Licensing update (2026-10-25): PolyForm-licensed snapshots are not eligible
> for the official F-Droid repository. The instructions below document the
> earlier MPL-licensed distribution; see [licensing](../../LICENSING.md).


Candy's `foss` flavor is the only variant intended for the official F-Droid repository. It uses the
same application ID as the GitHub build but excludes Google Play services, Google Cast, Google Code
Scanner, and the GitHub self-updater. New FOSS installs keep remote search suggestions disabled
until the user explicitly chooses a provider.

## Submission

1. Run the `Release Android APK` workflow. It must create the version tag from the exact commit that
   contains the matching `candy.versionName` and `candy.versionCode` values.
2. For releases created before signed FOSS assets were enabled, run the backfill workflow from the
   default branch:

   ```bash
   gh workflow run publish-fdroid-reference.yml -f tag=v0.31
   gh workflow run publish-fdroid-reference.yml -f tag=v0.32
   ```

3. Verify that each signed FOSS asset uses the pinned certificate fingerprint and matches the
   independently built F-Droid APK byte for byte apart from signing.
4. Copy `dev.sk2andy.materialbrowser.yml` into a fork of `fdroid/fdroiddata` as
   `metadata/dev.sk2andy.materialbrowser.yml`.
5. Validate from the fdroiddata checkout:

   ```bash
   fdroid readmeta
   fdroid rewritemeta dev.sk2andy.materialbrowser
   fdroid checkupdates dev.sk2andy.materialbrowser
   fdroid lint dev.sk2andy.materialbrowser
   fdroid build -v -l dev.sk2andy.materialbrowser
   ```

6. Open a merge request against `fdroid/fdroiddata`. Include the successful local-build log and
   explain that the `full` flavor contains optional Google integrations while `foss` resolves no
   Google/Firebase/ML Kit runtime dependencies.

The metadata deliberately declares no AntiFeatures: the FOSS flavor performs no automatic update
or search-suggestion request on a new install. If that default changes, reassess F-Droid's
`Tracking` AntiFeature before the next submission.

## Signing boundary

`Binaries` and `AllowedAPKSigningKeys` make F-Droid compare its source build with Candy's signed FOSS
APK. Keep those fields only while every referenced version passes that comparison. This lets F-Droid
publish the upstream-signed APK and avoids a signing-key boundary between F-Droid, GitHub, and
Obtainium installations.

## Release maintenance

For every release, update these source-owned values before tagging:

- `candy.versionName` and `candy.versionCode` in `gradle.properties`
- localized `fastlane/metadata/android/*/changelogs/<versionCode>.txt`
- the newest `Builds` block, `CurrentVersion`, and `CurrentVersionCode` in fdroiddata

`UpdateCheckMode: Tags` reads version metadata from `gradle.properties`; stable tags must match
`v<major>.<minor>` or `v<major>.<minor>.<patch>`.
