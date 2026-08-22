# Toppings (local userscripts)

## Ownership

| Layer | Responsibility | Main code |
| --- | --- | --- |
| Model and policy | Parse bounded metadata, match HTTP(S) URLs, derive origin rules and build guarded injection sources | `browser/userscript/` |
| Persistence | Atomically store validated local source, bounded per-script GM values and the last valid catalog outside browser-session state | `data/UserScriptStore.kt`, `data/UserScriptValueStore.kt`, `data/ToppingCatalogStore.kt` |
| Runtime | Register isolated scripts and their world-scoped native bridge on normal tab WebViews | `browser/userscript/UserScriptRuntime.kt`, `browser/BrowserController.kt` |
| UI and import | Manage local Toppings and explicitly install catalog entries | `ui/UserscriptManagementScreen.kt`, `ui/ToppingCatalogScreen.kt` |

## Discovery catalog

- **Toppings entdecken** fetches `catalog.json` only when that settings page opens. The public
  source is [`sk2andy/candy-browser-toppings`](https://github.com/sk2andy/candy-browser-toppings)
  on `main`, so reviewed additions do not require a Candy Browser release.
- A failed refresh falls back to the last atomically cached, valid manifest. Installed Toppings are
  local copies and keep working offline or after a catalog entry is removed.
- Enabling an uninstalled catalog entry downloads only its declared `toppings/<id>.user.js` file.
  Candy requires the fixed GitHub Raw HTTPS host, refuses redirects, wildcard hosts and broad
  all-sites scopes, then checks byte bounds, SHA-256, strict UTF-8, metadata, URL scopes and the
  normal userscript parser. Locally authored Toppings may still use bounded wildcard hosts.
- Catalog changes never execute or replace local source automatically. A different remote hash is
  shown as an explicit update; locally edited source is overwritten only by that update action.
- SHA-256 detects inconsistent transport or a manifest/source race. Repository maintainers and
  GitHub TLS remain the trust root, so `main` requires a reviewed PR and passing catalog CI.

## Supported contract

| Metadata | Behavior |
| --- | --- |
| `@name` | Required display name |
| `@match`, `@include` | At least one bounded HTTP(S) URL pattern is required |
| `@exclude` | Matching exclusions win over positive patterns |
| `@run-at document-start` | Runs before page JavaScript; the DOM may not exist yet |
| `@run-at document-end` | Runs once at `DOMContentLoaded`, or immediately if that event already passed |
| `@grant none` | Accepted without privileged native APIs; Tampermonkey-compatible `GM_info`/`GM.info` metadata remains available |
| `@grant GM_info` or `GM.info` | Explicit metadata grant; metadata is also available with `none` |
| `@grant GM_addStyle` or `GM.addStyle` | Adds a style element to the matching document |
| Value grants | `GM_getValue`/`GM.getValue`, `GM_setValue`/`GM.setValue`, `GM_deleteValue`/`GM.deleteValue` and `GM_listValues`/`GM.listValues` enable bounded persistent JSON values isolated by script ID; legacy synchronous and `GM.*` Promise forms are available |
| Menu grants | `GM_registerMenuCommand`/`GM.registerMenuCommand` and unregister variants add document-bound actions to the browser menu; callbacks remain inside the script's isolated world |
| `GM_openInTab` / `GM.openInTab` | Opens a validated HTTP(S) URL in a regular foreground or background tab; private sources and non-web schemes are rejected |
| `@require` | HTTPS JavaScript is fetched only during explicit import, install or update, bounded, optionally checked with a `#sha256=<hex>` fragment, stored locally and executed in metadata order before the main source |
| `@resource` | Bounded HTTPS binary data is bundled locally; `GM_getResourceText`/`GM.getResourceText` and `GM_getResourceURL`/`GM.getResourceUrl` expose it without page-load network access |
| `@updateURL`, `@downloadURL` | Accepted as inert metadata; Candy never updates scripts implicitly |

- Candy's v1 `@match` subset accepts HTTP(S) hosts without an explicit port and is registered for
  that scheme's default port. Use a bounded `@include https://host:8443/path/*` pattern when a
  non-default port is required.
- `@require` and `@resource` accept only allowlisted public HTTPS dependency hosts on the standard port, without
  credentials. At most three redirects are followed manually; every hop must independently remain
  HTTPS, allowlisted and publicly resolved. Localhost, IP literals, malformed integrity fragments, invalid UTF-8
  JavaScript and dependency payloads over the per-item or aggregate limits fail closed. Resolution
  occurs only during an explicit user action; normal page loads remain offline with respect to
  dependency hosts. `@connect` and unsupported grants remain rejected. Scripts receive only
  explicitly granted local APIs and no general cross-origin permission. Each runs in its own
  Candy-isolated JavaScript world with ordinary DOM access. The world-scoped native bridge accepts
  only bounded, grant-checked value mutations from the matching top-level origin, merges them
  atomically across tabs and acknowledges the canonical persisted state. Page-world
  JavaScript and other Toppings cannot access it. Native event injection executes source directly,
  so page Content Security Policy cannot block a Topping as string eval.
- Only the top-level HTTP(S) document is eligible. Iframes, `file:`, `content:`, `data:`, Link Peek
  previews and private tabs never run userscripts.
- A userscript can read and change matching page content and act through the signed-in page session.
  Import only trusted source. Source and collection bounds limit storage and startup cost, but cannot
  prevent trusted code from blocking or crashing a renderer.

## Lifecycle and boundaries

- AndroidX WebKit frame/world event-injection support is required. Guard handlers are installed
  before all source handlers and before navigation, then removed on script mutation, renderer loss,
  WebView recreation and controller destruction.
- Native allowed-origin rules provide the first origin boundary; the isolated-world guard then
  checks the complete URL, exclusions and top-frame identity before executing source.
- Script changes apply to future documents. Reload an already open page to apply an edit or toggle.
- Userscripts are global user configuration across regular profiles and survive clearing browsing
  data. GM values are removed when their script is deleted. Scripts and their GM values are never
  copied into private runtime or private persistence.
- Site Capsules use normal tab WebViews, so regular Capsule pages follow the same matching rules.

## Verification

| Layer | Check |
| --- | --- |
| Parser, dependency URL/integrity rules, guarded sources and GM bridge protocol | `UserScriptParserTest`, `UserScriptDependencyResolverTest`, `UserScriptRulesTest`, `UserScriptInjectionTest`, `UserScriptApiTest`, `UserScriptBridgeContractTest` |
| Atomic persistence | `UserScriptStoreInstrumentedTest`, `UserScriptValueStoreInstrumentedTest` |
| Settings semantics and actions | `UserscriptManagementScreenInstrumentedTest` |
| Catalog schema, integrity and cache | `ToppingCatalogParserTest`, `ToppingVerifierTest`, `ToppingCatalogStoreInstrumentedTest` |
| Discovery semantics and actions | `ToppingCatalogScreenInstrumentedTest` |
| WebView timing, CSP, origin and top-frame boundary | `UserScriptInjectionInstrumentedTest` on API 34+ |
| GM world isolation and private-registration boundary | `UserScriptRuntimeInstrumentedTest` |
