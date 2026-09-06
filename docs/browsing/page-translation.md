# Page translation

## Flow

| Step | Owner | Contract |
| --- | --- | --- |
| User action | `ui/BrowserMainMenu.kt` | **Translate page** is enabled in the main `…` menu for supported HTTP(S) pages and disabled otherwise. |
| URL policy | `shared/src/commonMain/.../browser/PageTranslation.kt` | Validate the source with the shared HTTP URL policy, bound it, encode it, and build one provider URL. Android and iOS compile the same provider rules. |
| Navigation | `browser/BrowserController.kt`, `iosApp/CandyIos/BrowserViewModel.swift` | Route the provider URL through existing browser navigation in the selected engine tab. iOS does not extract page text or open a translation sheet. Ordinary Android tabs keep it in the selected tab; Site Capsules may open a full Candy tab at their navigation boundary. |
| Provider choice | Shared Browser settings, Android `BrowserSessionStore`, iOS `BrowserTranslationProviderPreference` | Both platforms persist a global Google Translate, Yandex Translate, or Kagi Translate choice. Android keeps the compatibility-oriented Yandex default; iOS starts with Google as required by its product flow. Invalid shared values fail closed to Yandex. |

The target language follows the first configured Android locale. All providers detect the source
language. Their translated page UI can select another target language.

## Privacy and compatibility

- Translation starts only after an explicit user action. The full source URL, including query and
  fragment, is sent to the selected provider.
- No automatic fallback contacts a second provider.
- Private tabs use their existing memory-only WebView boundary. Candy stores no separate
  translation history or page state; the global provider preference still persists.
- Translation providers fetch the source page themselves. Login state, paywalls, local addresses,
  and highly dynamic pages may therefore fail or show incomplete content.
- Google website translation may be unavailable in some regions and can lose content when a source
  site's JavaScript replaces Google's server-translated document. Yandex is the default because its
  proxy preserves more of these script-driven pages on Android; iOS defaults to Google. The provider
  setting shows this compatibility warning while Google is selected. Provider URL routes are
  external service contracts and may need maintenance if a provider changes them.
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
