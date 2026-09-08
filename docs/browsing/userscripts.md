# Toppings (local userscripts)

Toppings are the cross-platform lightweight customization model. Android's Gecko runtime uses a
private bundled MV3 host extension. Android has no WebView fallback.
The iOS runtime consumes the shared metadata and URL policy, resolves dependencies during explicit
imports and installs each script as a main-frame `WKUserScript` in a named `WKContentWorld`. Android Gecko separately
supports Mozilla-signed Firefox WebExtensions. See [`platform-engines.md`](platform-engines.md) for
the engine boundary; WebExtensions and Toppings intentionally remain different capability models.
Firefox WebExtension action, popup, options, tab and download conformance is tracked in the
[GeckoView 155 capability matrix](platform-engines.md#firefox-webextension-capability-matrix-geckoview-155);
none of those privileged delegates are exposed to Toppings.

## Ownership

| Layer | Responsibility | Main code |
| --- | --- | --- |
| Model and policy | Parse bounded metadata, match HTTP(S) URLs, derive origin rules and build guarded injection sources | `browser/userscript/` |
| Persistence | Atomically store validated local source, bounded per-script GM values and the last valid catalog outside browser-session state | `data/UserScriptStore.kt`, `data/UserScriptValueStore.kt`, `data/ToppingCatalogStore.kt` |
| Runtime | Register isolated scripts in regular Gecko sessions through the bundled Candy host; iOS uses its platform WKContentWorld bridge | `browser/gecko/GeckoToppingHostRuntime.kt`, `browser/BrowserController.kt`, `iosApp/CandyIos/ToppingRuntime.swift` |
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

- GeckoView 155 installs or updates the fixed `candy-topping-host@sk2andy.dev` built-in extension,
  grants its optional `userScripts` permission and reconciles enabled persisted Toppings through a
  revisioned native-messaging port. The first regular navigation waits for a successful registration
  acknowledgement or a bounded initialization failure, preserving `document-start` on cold start.
- Gecko registrations use a dedicated `USER_SCRIPT` world, `allFrames=false`, Candy's exact
  `@match`/`@include`/`@exclude` scopes and `document-start`/`document-end` timing. GeckoView 155 has
  no CSS member on `RegisteredUserScript`; CSS in this slice is supported through the local
  `GM_addStyle`/`GM.addStyle` bootstrap. `GM_info`/`GM.info` is also local.
- GeckoView 155 user-script worlds expose `runtime.sendMessage` and `runtime.connect`, but not
  `runtime.onMessage`. Value mutations use the former; menu callbacks use a world-validated port
  accepted through the host's `runtime.onUserScriptConnect`. The bridge binds Gecko's active tab to
  Candy's tab ID only when the host-provided active Candy ID and `sender.tab.active` agree.
  Value-only reconciliation changes the content-addressed registration ID but deliberately retains
  the world ID, so the current document can continue mutating values and invoking menu callbacks.
- GeckoView 155 can keep a built-in extension marked private-capable even after requesting the opposite.
  Therefore every registered script performs a fail-closed `private-check` over the dedicated
  user-script messaging channel before user source runs. The host handles only that bounded message,
  never appears in the Firefox extension manager and cannot be mutated through that manager.
- AndroidX WebKit frame/world event-injection support is required. Guard handlers are installed
  before all source handlers and before navigation, then removed on script mutation, renderer loss,
  WebView recreation and controller destruction.
- Native allowed-origin rules provide the first origin boundary; the isolated-world guard then
  checks the complete URL, exclusions and top-frame identity before executing source.
- Android Gecko catalog changes are guaranteed after the next app/runtime start. GeckoView 155 does
  not reliably expose a changed dynamic registration to an already-created session, even after the
  extension API acknowledges it; rebuilding live sessions without losing history remains a parity
  gap. On the Android WebView compatibility path, reloading applies the changed script immediately.
- Userscripts are global user configuration across regular profiles and survive clearing browsing
  data. GM values are removed when their script is deleted. Scripts and their GM values are never
  copied into private runtime or private persistence.
- Site Capsules use normal tab WebViews, so regular Capsule pages follow the same matching rules.
- iOS registers a distinct named `WKContentWorld` and `WKScriptMessageHandlerWithReply` for every
  Topping revision. The first statement asks native code to authorize the current top-frame URL;
  disabled, replaced, private, non-HTTP(S), mismatched and stale-revision scripts fail before
  `@require` or user source runs. Save, toggle and delete reconcile live controllers without removing
  Candy Blocking scripts. Old immutable `WKUserScript` registrations remain inert because their
  revision handler is removed; a matching current revision is never registered twice.
- iOS `@require` and `@resource` downloads use an ephemeral cache-free session only from the same
  allowlist as Android. Credentials, non-standard ports, IP literals, private/loopback DNS answers,
  more than three redirects, oversized payloads, invalid UTF-8 and SHA-256 mismatches fail closed.
  The resolved bytes are stored with the Topping and normal navigation performs no dependency
  network request. Persisted dependency bytes and integrity are revalidated before registration.
- iOS GM values use a bounded script-ID-partitioned `UserDefaults` store. The world bridge checks
  current grant, top frame, current URL scope, revision and message rate before an atomic mutation,
  then replies with the canonical merged snapshot. Menu callbacks live only in the script world;
  native stores only IDs/captions and removes them on navigation. `GM_openInTab` accepts at most four
  credential-free HTTP(S) requests per ten seconds and never runs in private tabs.
- WebKit's reply bridge is asynchronous. iOS registers `document-start` at WebKit's earliest
  injection point, but the Topping payload resumes only after native authorization; unlike Gecko,
  WebKit cannot guarantee that this continuation beats every page-world start script.

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
| Shared iOS metadata/grants/dependency and URL policy | `shared:ToppingRulesTest` |
| iOS compiler and WebKit contract | `swiftc -typecheck` over `iosApp/CandyIos/*.swift`; `CandyIos` simulator build/smoke |
| GeckoView 155 host readiness, world validation, resolved `@require`/`@resource`, values, active-tab binding, menu callback, `GM_openInTab`, exclude and private rejection | `CandyToppingHostInstrumentedTest` on the dedicated API 34+ emulator |
