# Android app languages

## Ownership

| Concern | Source | Contract |
| --- | --- | --- |
| Available languages | `app/src/main/res/xml/locales_config.xml` | One entry per supported language or script; Android system settings and the in-app picker use this list. |
| User preference | `data/AppLanguagePreferences.kt` | Android `LocaleManager` persists the app override. An empty locale list follows the device language. |
| Picker selection | `data/AppLanguageRules.kt`, `ui/AppLanguageSettings.kt` | Prefer an exact locale, then its language/script, then its language. Chinese regional fallbacks retain the expected simplified or traditional script. |
| Runtime changes | `MainActivity.kt`, `AndroidManifest.xml` | Locale/layout-direction updates refresh resources without recreating the browser or losing private tabs. |
| Translated text | `res/values-*/strings.xml`, `userscript_strings.xml`, `https_only_warning.xml` | Translate all three files; preserve formatter arguments, product names, technical tokens and privacy guarantees. |

## Supported languages

| Group | Languages | Android locale tags |
| --- | --- | --- |
| Existing | English, German, French, Portuguese, Spanish, Polish, Czech | `en`, `de`, `fr`, `pt`, `es`, `pl`, `cs` |
| Russian | Russian | `ru` (also used for `ru-RU`) |
| East Asia | Simplified Chinese, Traditional Chinese, Japanese, Korean | `zh-Hans`, `zh-Hant`, `ja`, `ko` |
| Southeast Asia | Thai, Vietnamese | `th`, `vi` |
| Northern/western Europe | Norwegian Bokmål, Swedish, Danish, Dutch, Luxembourgish | `nb`, `sv`, `da`, `nl`, `lb` |
| Balkan region | Albanian, Bosnian, Bulgarian, Greek, Croatian, Macedonian, Romanian, Serbian (Cyrillic), Slovenian, Turkish | `sq`, `bs`, `bg`, `el`, `hr`, `mk`, `ro`, `sr`, `sl`, `tr` |

The added languages start from local machine-translation drafts with reviewed browser labels,
formatter arguments, plural forms and security/privacy warnings. Full native-speaker review remains
open. Keep improvements in the locale resources; no translation model or service runs in the app.

## Verification

| Check | Coverage |
| --- | --- |
| `python3 scripts/test_localization.py` | Every declared locale has all translatable resource names and compatible formatting arguments. |
| `AppLanguageRulesTest` | Chinese scripts/regions, exact region selection, language fallback and device default. |
| `AppLanguageSettingsInstrumentedTest` | Native language labels, Chinese script switching, device default and scrolling through the full menu. |
| `AppLanguagePreferencesInstrumentedTest` | Native locale persistence, private-tab preservation and Android resource resolution/formatting for every added language. |
| Russian regression coverage | Native `русский` menu selection, `ru-RU` resource fallback, Russian one/few/many tab counts and persisted locale changes without losing private tabs. |
| Full/FOSS assembly and lint | Android XML escaping, plural categories, packaged resources and both product flavors. |
