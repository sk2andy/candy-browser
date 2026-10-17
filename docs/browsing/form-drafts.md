# Form draft recovery

## Behavior lookup

| Concern | Contract |
| --- | --- |
| Opt-in | Protection settings enables draft capture. Disabled by default; disabling deletes disk and private-memory drafts. |
| Capture | Only real user edits to visible, uniquely identifiable textareas and contenteditable fields in the top HTTP(S) document. Single-line inputs, subframes, password/payment-related fields, sensitive forms and autocomplete-off fields are excluded. |
| Recovery | An explicit Restore action fills matching empty fields. Existing text is never overwritten. Page URL, query, fragment, profile, document and navigation identities must match. |
| Regular drafts | Encrypted with Android Keystore in `noBackupFilesDir/form_drafts`; unavailable to backup, app-data export, sync or logs. Synced profiles are excluded. |
| Private drafts | A separate memory-only cache belongs to the coordinator and scopes each draft to its private tab. Closing the tab, deleting the profile, clearing browsing data, disabling recovery or destroying the coordinator removes these drafts. No private draft is sent to the disk store. |
| Retention | At most 20 pages per cache for 24 hours; 16 fields per page, 16,000 characters per field and 64,000 total characters per page. |
| Submission | Submitting/resetting a captured form removes the draft; later user edits can create a new draft. |
| Limits | Ambiguous unnamed fields are skipped. Sites can hide or replace editors; recovery requires the matching editor to be visible and empty. Sensitive-field detection uses field/form metadata; users should leave recovery disabled on shared devices or when composing sensitive prose. |

## Ownership lookup

| Component | Responsibility |
| --- | --- |
| `FormDraftRules`, `FormDraftCodec` | Exact-page validation, bounded snapshots, expiry and serialization |
| `FormDraftCoordinator` | Active-document binding, stale callback rejection, recovery offers and serialized regular-draft writes |
| `PrivateFormDrafts` | Bounded per-tab memory-only recovery and lifecycle cleanup |
| `FormDraftStore` | Keystore encryption, atomic writes, profile deletion and private-write rejection |
| `form_drafts.js` | Trusted-input capture, sensitive-field exclusion, document identity and non-overwriting restore |
| `SystemWebViewFormDrafts`, Gecko privacy bridge | Engine-specific transport and top-document validation |

## Verification lookup

| Surface | Tests |
| --- | --- |
| Retention, malformed input, private tab isolation and cleanup | `FormDraftRulesTest`, `PrivateFormDraftsTest` |
| DOM capture, sensitive fields, submission and restore boundaries | `scripts/form_drafts.test.mjs` |
| Encrypted round trip, private disk rejection, expiry, corruption and deletion | `FormDraftStoreInstrumentedTest` |
| Typed input → reload → explicit restore, private memory-only recovery on both engines | `FormDraftEngineInstrumentedTest` |
