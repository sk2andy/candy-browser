# Notices and attributions

This repository mixes original work with material derived from Google's Material Design
documentation and from the Android Open Source Project. The pieces are under different licenses.
This file records which is which.

## Original work (MIT)

Covered by [LICENSE](LICENSE), © 2026 abhixv:

- `skills/m3-expressive/scripts/*.js`, the refresh scripts
- `skills/m3-expressive/SKILL.md`, the source contract, routing, and authored guidance
- The selection, organization, and prose of the files in `skills/m3-expressive/references/`
- `README.md` and this file

The MIT license does **not** cover third-party material included here. Material Design guideline
content, AndroidX token values, and the reference images all remain under their own licenses and
copyright, recorded in the sections below.

## Material Design guidelines (© Google LLC)

The design guidance, placement rules, component behavior, and do/don'ts in
`skills/m3-expressive/references/` are derived from Material Design 3 documentation published at
<https://m3.material.io>.

Material Design documentation is published by Google under the
[Creative Commons Attribution 4.0 International License](https://creativecommons.org/licenses/by/4.0/)
(code samples under Apache 2.0), per the site's terms. That material is reproduced and summarized
here **with attribution to Google LLC**, and remains under its original license.

If you redistribute this repository or build on it, keep this attribution intact.

## AndroidX / Compose Material 3 tokens (Apache License 2.0)

`skills/m3-expressive/references/component-tokens.md` contains dp, sp, shape, and elevation values
extracted from the generated `androidx.compose.material3.tokens` sources.

> Copyright (C) The Android Open Source Project
>
> Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
> in compliance with the License. You may obtain a copy of the License at
> <http://www.apache.org/licenses/LICENSE-2.0>
>
> Unless required by applicable law or agreed to in writing, software distributed under the License
> is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
> or implied. See the License for the specific language governing permissions and limitations under
> the License.

**Modifications made:** token values were extracted from the original Kotlin sources and reformatted
into grouped markdown tables. No values were altered. Each group records the generator `VERSION`
stamp it came from.

## Reference images (© Google LLC)

`skills/m3-expressive/visuals/` contains 2,587 images published on <https://m3.material.io>
(anatomy diagrams, measurement specs, state grids, do/don't pairs, canonical screens). They are
Google's assets, included here unmodified for reference alongside the guidance they illustrate, and
they remain Google's copyright.

They are **not required**, since every number and rule lives in the markdown. If you would rather not
redistribute them, exclude the directory; it regenerates from `visuals/INDEX.tsv` plus
`scripts/refresh-m3.js`:

```gitignore
skills/m3-expressive/visuals/*
!skills/m3-expressive/visuals/INDEX.tsv
```

If Google or a rights holder objects to their inclusion, open an issue and they will be removed from
the repository.

## Trademarks

Material Design, Material You, Android, and Jetpack Compose are trademarks of Google LLC. Use of
these names here is descriptive. It identifies the design system this skill documents.

**This project is not affiliated with, sponsored by, or endorsed by Google LLC.** It is an
independent, community-maintained reference packaged as an agent skill.

## Accuracy

Values are reproduced as published and captured **July 2026**. Known gaps are recorded as `## Gaps`
sections in the relevant reference rather than filled with invented values. This repository makes no
warranty that it matches the current state of Material Design. Verify against the upstream sources
before relying on it for production work, and use the refresh scripts to bring it up to date.
