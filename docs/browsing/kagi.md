# Kagi search

## Contract

| Concern | Rule | Owner |
| --- | --- | --- |
| Search | The built-in `Kagi` option opens `https://kagi.com/search?q={encodedQuery}`. Kagi may ask the user to sign in inside the WebView. | `SearchEngine`, `AddressResolver` |
| Suggestions | The optional `Kagi` suggestion provider calls `https://kagisuggest.com/api/autosuggest?q={encodedQuery}` and parses its OpenSearch JSON response. | `SearchSuggestionProvider`, `SearchSuggestionClient` |
| Locale | Candy sends the device language only to `kagisuggest.com` so Kagi can localize suggestions. | `HttpSearchSuggestionTransport` |
| Private tabs | Remote Kagi suggestions never run. Kagi searches still navigate normally, but a private tab may require a separate sign-in. | `SearchSuggestionRules`, `BrowserController` |
| Secrets | Candy does not store Kagi Session Links or Search API tokens. Session Links grant account access; Kagi's programmable Search API is a separate authenticated and billed product. | Settings boundary |

Kagi documents both browser URLs in its [default-search setup](https://help.kagi.com/kagi/getting-started/setting-default.html). Kagi recommends the separate suggestion origin alongside [Privacy Pass](https://help.kagi.com/kagi/privacy/privacy-pass.html) because it prevents browsers from automatically sending normal Kagi login cookies with address-bar suggestion requests. Candy does not issue or redeem Privacy Pass tokens and does not persist a Kagi Session Link or Search API token.
