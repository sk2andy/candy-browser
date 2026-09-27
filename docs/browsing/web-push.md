# GeckoView website push

| Concern | Current behavior | Owner |
| --- | --- | --- |
| Web Push API | GeckoView `WebPushDelegate` supplies subscriptions to regular service workers | `GeckoWebPushCoordinator` |
| FOSS delivery | Mozilla Autopush WebSocket collects pending messages on subscribe and on bounded reconnect | `FossWebPushTransport` |
| Background reconnect | WorkManager schedules a network-constrained reconnect every 15 minutes while subscriptions or pending unregistrations exist | `FossWebPushReconnectWorker` |
| Payload | RFC 8291 `aes128gcm` content is decrypted before `WebPushController.onPushEvent` | `WebPushCrypto` |
| Storage | UAID, subscriptions and keys are encrypted with Android Keystore in `noBackupFilesDir` | `FossWebPushStore` |
| Private mode | Private Gecko scopes cannot create persistent push subscriptions | `FossWebPushScopeRules` |
| System WebView | No Web Push integration | `GeckoWebPushCoordinator` checks the selected engine |

The FOSS transport is the only available transport and the default. It does not require Google Play services or another Android app. A bounded WebSocket connection drains pending notifications and closes; WorkManager may run later than the 15-minute interval under Doze or other battery restrictions. Messages can expire at the sender's chosen TTL before the next reconnect.

FCM delivery requires a separate Firebase project and a push service with Candy's server-side Firebase credentials. The public Mozilla Autopush service does not automatically register new Firebase sender IDs. Until that infrastructure and a Full-only client adapter exist, Candy does not offer FCM as an active transport.

If FCM is added, switching transports will require invalidating existing endpoints and signaling `WebPushController.onSubscriptionChanged(scope)` so websites can subscribe again. Subscriptions cannot be transparently migrated between transport stores.

Deleting all browsing data also erases FOSS push identity and keys. Removing an isolated profile or disabling its isolation removes subscriptions for its Gecko session context before the change completes. Non-isolated profiles share Gecko's default context, so their subscriptions are not attributable to one Candy profile.
