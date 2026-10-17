# Page translation

## Selected text on Android

| Surface | Contract |
| --- | --- |
| Native selection | **Translate here** is appended to the engine's native text menu; copy, select-all and Android text actions remain available. Editable/password fields are excluded. |
| Local translation | `AndroidInlineTranslator` queries the configured Android `TranslationManager` off the main thread. Only downloaded (`STATE_ON_DEVICE`) language pairs are offered. No Google runtime dependency is added; Full and FOSS use the same platform API. |
| Languages | The user selects source and target languages from available pairs; the device language is preferred as target when available. Candy does not guess the source language. The platform translation settings entry manages models when the service exposes one. Returning from settings requires selecting the text again. |
| Inline result | A unique unchanged read-only passage is replaced with a keyboard-accessible toggle between original and translated text. Ambiguous, hidden, editable or changed text fails without guessed replacements. |
| Provider fallback | An explicit button uses the existing selected page provider (Google, Yandex or Kagi). It clearly says it sends the page address and translates the whole page. No automatic network fallback occurs. |
| Lifecycle and privacy | Selected text and translations remain in memory, including private tabs. Dismissal, backgrounding and destruction release the request; tab/profile/session/URL/navigation checks reject stale results. No selected text is written to preferences, logs or saved-instance state. |
| Availability | Local translation requires an installed system translation service and downloaded language pair. Unsupported devices retain the explicit page-provider fallback. iOS retains its existing page translation flow. |
| Ownership | `browser/translation/InlineTranslationRules`, `AndroidInlineTranslator`, `InlineTranslationCoordinator`; `ui/InlineTranslationDialog`; shared engine `executeWebTextOperation` adapter contract. |
| Tests | `InlineTranslationRulesTest`, `InlineTranslationCoordinatorInstrumentedTest`, native menu and web-text engine tests under `browser/annotation`. |

## Flow

| Step | Owner | Contract |
| --- | --- | --- |
| User action | `ui/BrowserMainMenu.kt` | **Translate page** is enabled in the main `…` menu for supported HTTP(S) pages and disabled otherwise. |
| URL policy | `shared/src/commonMain/.../browser/PageTranslation.kt` | Validate the source with the shared HTTP URL policy, bound it, encode it, and build one provider URL. Android and iOS compile the same provider rules. |
| Navigation | `browser/BrowserController.kt`, `iosApp/CandyIos/BrowserViewModel.swift` | Route the provider URL through existing browser navigation in the selected engine tab. iOS does not extract page text or open a translation sheet. Ordinary Android tabs keep it in the selected tab; Site Capsules may open a full Candy tab at their navigation boundary. |
| Provider choice | Shared Browser settings, Android `BrowserSessionStore`, iOS `BrowserTranslationProviderPreference` | Both platforms persist a global Google Translate, Yandex Translate, or Kagi Translate choice. Android keeps the compatibility-oriented Yandex default; iOS starts with Google as required by its product flow. Invalid shared values fail closed to Yandex. |
| Android recovery | `PageTranslationRecoveryRules`, `BrowserController`, `BrowserOfferSnackbarEffects` | Gecko and System WebView retain a tab- and navigation-bound translation attempt. Provider transport/HTTP failures, plus a confirmed empty result, offer an explicit Yandex retry or return to the original page. |

The target language follows the first configured Android locale. All providers detect the source
language. Their translated page UI can select another target language.

## Privacy and compatibility

- Translation starts only after an explicit user action. The full source URL, including query and
  fragment, is sent to the selected provider.
- No automatic fallback contacts a second provider. After a non-Yandex failure, Candy offers a
  **Try Yandex** action; only that explicit tap sends the source URL to Yandex. A failed Yandex
  attempt instead offers the original page.
- Private tabs use their existing memory-only WebView boundary. Candy stores no separate
  translation history or page state; the global provider preference still persists.
- Translation providers fetch the source page themselves. Login state, paywalls, local addresses,
  and highly dynamic pages may therefore fail or show incomplete content.
- Google website translation may be unavailable in some regions and can lose content when a source
  site's JavaScript replaces Google's server-translated document. Yandex is the default because its
  proxy preserves more of these script-driven pages on Android; iOS defaults to Google. The provider
  setting shows this compatibility warning while Google is selected. Android verifies that a provider
  result contains visible document content after rendering. Candy reports a provider error when two
  delayed probes stay empty, or when a bounded error-only result matches a known provider rejection.
  Provider URL routes are external service contracts and may need maintenance if a provider changes
  them.
- Kagi Translate currently requires an active Kagi subscription. Candy does not manage Kagi
  authentication; the provider page handles sign-in.
- Kagi reserves the `to`, `kt_quality`, and `kt_view` query names for its own website route. Candy
  disables Kagi translation for source URLs using those names instead of changing their meaning.

## Provider endpoints

| Provider | Request shape | Result host |
| --- | --- | --- |
| Google Translate | `translate.google.com/translate?sl=auto&tl=…&u=…` | `*.translate.goog` |
| Yandex Translate | `translate.yandex.com/translate?url=…&lang=…` | `translated.turbopages.org` |
| Kagi Translate | `translate.kagi.com/<source-host-and-path>?to=…` | `translate.kagi.com` |

Provider result pages are not offered for translation again.

Official feature documentation:

- [Google Translate: translate documents and websites](https://support.google.com/translate/answer/2534559?hl=en)
- [Yandex Translate: translating websites](https://yandex.com/support/translate-mobile/en/mode)
- [Kagi Translate: URL parameters](https://help.kagi.com/kagi/translate/url-parameters.html)
