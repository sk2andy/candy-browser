# Site Capsules

## Topic lookup

| Need | Detail | Main code |
| --- | --- | --- |
| Model validation, navigation boundary, profile projection | [`model-and-navigation.md`](model-and-navigation.md) | `SiteCapsule`, `CapsuleNavigationRules`, `CapsuleProfileRules` |
| Editor/launch lifecycle, persistence, icons, shortcuts, deletion | [`lifecycle-and-shortcuts.md`](lifecycle-and-shortcuts.md) | `SiteCapsuleEditorContract`, `CapsuleIntentRules`, `SiteCapsuleStore`, `CapsuleShortcutPublisher` |

## Test lookup

| Surface | Tests |
| --- | --- |
| Model, navigation, profile rules | `capsule/*RulesTest` |
| Editor and launch contracts | `SiteCapsuleEditorContractInstrumentedTest`, `SiteCapsuleLaunchInstrumentedTest` |
| Storage | `SiteCapsuleStoreInstrumentedTest` |
| Compose UI | `SiteCapsuleScreenInstrumentedTest` |

