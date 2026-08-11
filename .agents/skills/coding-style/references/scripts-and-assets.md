# Scripts and generated assets

## Language lookup

| Surface | Convention |
| --- | --- |
| POSIX shell | `#!/bin/sh`, `set -eu`, quoted paths/variables, `mktemp -d` plus trap cleanup |
| Bash audit runner | `#!/usr/bin/env bash`, `set -euo pipefail`, arrays and explicit emulator/device guards |
| Node | ESM imports, exported pure transforms, small CLI `main`, `node:test` coverage |
| Python | Standard library first, explicit CLI arguments, deterministic text output, focused unit tests |
| Gradle Kotlin DSL | Typed providers, validated inputs, no embedded credentials, stable build-type behavior |

## Generator rules

- Pin upstream source revisions. Record exact source URL, revision, transformation and license in shipped assets/notices.
- Download into a temporary directory; validate before replacing repository files.
- Compile only semantics supported by runtime code. Reject unsupported syntax instead of approximating it.
- Sort and deduplicate output deterministically. Include stable headers/counts where existing format expects them.
- Gate surprising rule counts, byte sizes, ranks, schemas and classifications before writing output.
- Edit generator, compiler or audit source; regenerate derived assets. Do not hand-edit generated files.
- Run the matching script tests, then rerun generation and require a clean diff.

## Safety lookup

| Risk | Required behavior |
| --- | --- |
| Credentials/signing | Read from ignored properties or environment; never print or commit values |
| Network source drift | Use pinned revision and fail closed on download/validation errors |
| Temporary data | Use task-scoped temp directory and cleanup trap |
| License-bearing filter data | Ship required source/license/notice beside generated subset |
