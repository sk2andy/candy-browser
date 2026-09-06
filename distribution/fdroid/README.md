# F-Droid submission

Candy's `foss` flavor is the only variant intended for the official F-Droid repository. It uses the
isolated application ID `dev.sk2andy.materialbrowser.foss` and excludes Google Play services,
Google Cast, Google Code Scanner, and the GitHub self-updater. New FOSS installs keep remote search
suggestions disabled until the user explicitly chooses a provider.

## Submission

The isolated listing starts at v0.37. Do not copy the v0.31–v0.36 build blocks from the former
`dev.sk2andy.materialbrowser` submission: those APKs use the universal application ID and are not
valid versions of `dev.sk2andy.materialbrowser.foss`.

1. Run the `Release Android APK` workflow. It must create the version tag from the exact commit that
   contains the matching `candy.versionName` and `candy.versionCode` values.
2. Allowlist the immutable tag commit in `publish-fdroid-reference.yml`, then publish its signed FOSS
   reference APK:

   ```bash
   gh workflow run publish-fdroid-reference.yml -f tag=v0.37
   ```

3. Verify that each signed FOSS asset uses the pinned certificate fingerprint and matches the
   independently built F-Droid APK byte for byte apart from signing.
4. Copy `dev.sk2andy.materialbrowser.foss.yml` into a fork of `fdroid/fdroiddata` as
   `metadata/dev.sk2andy.materialbrowser.foss.yml`.
5. Validate from the fdroiddata checkout:

   ```bash
   fdroid readmeta
   fdroid rewritemeta dev.sk2andy.materialbrowser.foss
   fdroid checkupdates dev.sk2andy.materialbrowser.foss
   fdroid lint dev.sk2andy.materialbrowser.foss
   fdroid build -v -l dev.sk2andy.materialbrowser.foss
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
