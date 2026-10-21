# Custom workflows

| Contract | Behavior / owner |
| --- | --- |
| Entry | Android Settings → Gestures & controls → Custom workflows; browser menu management row |
| Editor | Name, icon, ordered steps, templates, duplication, explicit save/discard and placement assignments |
| Steps | Save article offline, add favorite, pin tab, copy URL, open Reader, close input; one to five unique steps |
| Ordering | Reader and close are terminal. Pin and close cannot coexist. Failure stops remaining steps; completed effects remain. |
| Input | Menu/address actions capture the selected page. Link long press and Link Peek capture the link URL; Peek uses its current committed URL. |
| Link effects | Pin creates a background pinned tab. Close dismisses the preview; it never closes the containing tab. Reader extraction uses the preview or an ephemeral engine session with the source profile/private context. |
| Placements | Five menu shortcuts, five bottom-sheet footer slots, three address slots, link long press, right swipe in Link Peek, three configurable Link Peek toolbar slots. The fixed + slot remains available. |
| Deleted definitions | Bindings survive deletion. Bound buttons retain a blank slot; gestures do nothing. Only clearing a binding explicitly restores the original action. |
| Private boundary | Preflight rejects offline save and favorites before executing any step. Page contents and run progress are never stored in workflow configuration. |
| Runtime | One active run; checks captured tab, profile, engine session and navigation generation between steps. Pause requests cancellation; timeout/dispose reject late callbacks. |
| Saving | Serialized Reader writes return a persisted snapshot before continuing. Workflow saves refuse eviction; failure leaves the input open. |
| Persistence | `CustomWorkflowRepository` serializes validated, versioned `custom_workflows_v1.json` writes through `AtomicFile`. Stable workflow IDs bind surfaces independently of names. |
| Backup | Configuration and bindings are in full ZIP export/import and Android device transfer. Import replaces local app data; dangling bindings survive round trips. Old backups without the file restore no workflows. |
| Ownership | `browser/workflow/` owns models and execution; `data/CustomWorkflow*` owns persistence; `ui/CustomWorkflow*` owns editor, projections and feedback. Controller only bridges browser effects. |
| Validation | Rule/codec/runner unit tests; settings, Link Peek, store, Reader capacity and backup instrumentation. |
