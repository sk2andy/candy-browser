import CandyShared
import Foundation
import WebKit

struct ToppingInstalledScript {
    let installation: ToppingInstallation
    let handlerName: String
    let invocationFunction: String
    let world: WKContentWorld
}

enum ToppingInstaller {
    @MainActor
    static func install(
        _ installation: ToppingInstallation,
        values: [String: String],
        handlerName: String,
        into controller: WKUserContentController
    ) -> ToppingInstalledScript {
        let world = WKContentWorld.world(name: installation.plan.contentWorldName)
        let invocationFunction = "__candyInvoke_\(safeIdentifier(installation.script.id))"
        controller.addUserScript(
            WKUserScript(
                source: source(
                    installation: installation,
                    values: values,
                    handlerName: handlerName,
                    invocationFunction: invocationFunction
                ),
                injectionTime: installation.script.runAt == .documentstart
                    ? .atDocumentStart
                    : .atDocumentEnd,
                forMainFrameOnly: true,
                in: world
            )
        )
        return ToppingInstalledScript(
            installation: installation,
            handlerName: handlerName,
            invocationFunction: invocationFunction,
            world: world
        )
    }

    static func handlerName(scriptId: String) -> String {
        "candyTopping_\(safeIdentifier(scriptId))"
    }

    private static func source(
        installation: ToppingInstallation,
        values: [String: String],
        handlerName: String,
        invocationFunction: String
    ) -> String {
        let script = installation.script
        let grants = Set(script.grants)
        let info: [String: Any] = [
            "scriptHandler": "Candy",
            "version": "1",
            "script": [
                "id": script.id,
                "name": script.name,
                "grants": script.grants.map(\.metadataValue),
            ],
        ]
        let resources = Dictionary(uniqueKeysWithValues: installation.record.resources.map {
            ($0.name, ["content": $0.encodedContent, "mimeType": $0.mimeType])
        })
        let requireSources = installation.record.requires.map(\.source).joined(separator: "\n;\n")
        var definitions: [String] = [
            "const __candyPost = message => webkit.messageHandlers[\(json(handlerName))].postMessage(message);",
            define("GM_info", expression: json(info)),
            "const __candyGM = { info: globalThis.GM_info };",
        ]
        if grants.contains(.addstyle) {
            definitions.append(define("GM_addStyle", expression: """
                css => {
                  const style = document.createElement('style');
                  style.textContent = String(css);
                  (document.head || document.documentElement).appendChild(style);
                  return style;
                }
                """))
            definitions.append("__candyGM.addStyle = css => Promise.resolve(globalThis.GM_addStyle(css));")
        }
        if grants.contains(.getresourcetext) || grants.contains(.getresourceurl) {
            definitions.append("const __candyResources = \(json(resources));")
            definitions.append("""
                const __candyResource = name => {
                  const key = String(name);
                  if (!Object.prototype.hasOwnProperty.call(__candyResources, key)) {
                    throw new Error('Unknown userscript resource');
                  }
                  return __candyResources[key];
                };
                """)
        }
        if grants.contains(.getresourcetext) {
            definitions.append(define("GM_getResourceText", expression: """
                name => {
                  const binary = atob(__candyResource(name).content);
                  return new TextDecoder('utf-8').decode(Uint8Array.from(binary, c => c.charCodeAt(0)));
                }
                """))
            definitions.append("__candyGM.getResourceText = name => Promise.resolve(globalThis.GM_getResourceText(name));")
        }
        if grants.contains(.getresourceurl) {
            definitions.append(define("GM_getResourceURL", expression: """
                name => { const r = __candyResource(name); return `data:${r.mimeType};base64,${r.content}`; }
                """))
            definitions.append("__candyGM.getResourceUrl = name => Promise.resolve(globalThis.GM_getResourceURL(name));")
            definitions.append("__candyGM.getResourceURL = __candyGM.getResourceUrl;")
        }
        if grants.contains(.getvalue) || grants.contains(.setvalue) ||
            grants.contains(.deletevalue) || grants.contains(.listvalues) {
            definitions.append(valueAPI(values: values, grants: grants))
        }
        if grants.contains(.registermenucommand) || grants.contains(.unregistermenucommand) ||
            grants.contains(.openintab) {
            definitions.append(
                interactionAPI(
                    grants: grants,
                    invocationFunction: invocationFunction
                )
            )
        }
        definitions.append(define("GM", expression: "Object.freeze(__candyGM)"))
        return """
            (async () => {
              'use strict';
              if (window.top !== window || !/^https?:$/.test(location.protocol)) return;
              const __candyAuthorization = await webkit.messageHandlers[\(json(handlerName))]
                .postMessage({type:'authorize'});
              if (!__candyAuthorization || __candyAuthorization.ok !== true) return;
              \(definitions.joined(separator: "\n"))
              \(requireSources)
              \(script.source)
            })();
            """
    }

    private static func valueAPI(values: [String: String], grants: Set<ToppingGrant>) -> String {
        var valuesAPI: [String] = [
            "const __candyValues = Object.assign(Object.create(null), \(json(values)));",
            """
            const __candyKey = key => {
              const value = String(key);
              if (!value || value.length > 256) throw new TypeError('Invalid userscript value key');
              return value;
            };
            const __candyDecode = (encoded, fallback) => {
              if (encoded === undefined) return fallback;
              try { return JSON.parse(encoded); } catch (_) { return fallback; }
            };
            """,
        ]
        if grants.contains(.getvalue) {
            valuesAPI.append(define("GM_getValue", expression: "(key, fallback) => __candyDecode(__candyValues[__candyKey(key)], fallback)"))
            valuesAPI.append("__candyGM.getValue = (key, fallback) => Promise.resolve(globalThis.GM_getValue(key, fallback));")
        }
        if grants.contains(.setvalue) {
            valuesAPI.append("""
                const __candySetValue = async (key, value) => {
                  const normalizedKey = __candyKey(key);
                  const encoded = JSON.stringify(value);
                  if (encoded === undefined) throw new TypeError('Unsupported userscript value');
                  if (new TextEncoder().encode(encoded).byteLength > 16384) throw new RangeError('Value too large');
                  __candyValues[normalizedKey] = encoded;
                  const response = await __candyPost({type:'set-value', id:Date.now(), key:normalizedKey, value:encoded});
                  if (!response || response.ok !== true || typeof response.snapshot !== 'string') {
                    throw new Error('Value was not persisted');
                  }
                  Object.keys(__candyValues).forEach(key => delete __candyValues[key]);
                  Object.assign(__candyValues, JSON.parse(response.snapshot));
                };
                """)
            valuesAPI.append(define("GM_setValue", expression: "(key, value) => { void __candySetValue(key, value).catch(() => {}); }"))
            valuesAPI.append("__candyGM.setValue = __candySetValue;")
        }
        if grants.contains(.deletevalue) {
            valuesAPI.append("""
                const __candyDeleteValue = async key => {
                  const normalizedKey = __candyKey(key);
                  delete __candyValues[normalizedKey];
                  const response = await __candyPost({type:'delete-value', id:Date.now(), key:normalizedKey});
                  if (!response || response.ok !== true || typeof response.snapshot !== 'string') {
                    throw new Error('Value was not persisted');
                  }
                  Object.keys(__candyValues).forEach(key => delete __candyValues[key]);
                  Object.assign(__candyValues, JSON.parse(response.snapshot));
                };
                """)
            valuesAPI.append(define("GM_deleteValue", expression: "key => { void __candyDeleteValue(key).catch(() => {}); }"))
            valuesAPI.append("__candyGM.deleteValue = __candyDeleteValue;")
        }
        if grants.contains(.listvalues) {
            valuesAPI.append(define("GM_listValues", expression: "() => Object.keys(__candyValues)"))
            valuesAPI.append("__candyGM.listValues = () => Promise.resolve(globalThis.GM_listValues());")
        }
        return valuesAPI.joined(separator: "\n")
    }

    private static func interactionAPI(
        grants: Set<ToppingGrant>,
        invocationFunction: String
    ) -> String {
        var api = [
            "const __candyMenuCallbacks = new Map();",
            "let __candyMenuSequence = 0;",
            define(invocationFunction, expression: "commandId => { const callback = __candyMenuCallbacks.get(String(commandId)); if (callback) callback(); }"),
        ]
        if grants.contains(.registermenucommand) {
            api.append(define("GM_registerMenuCommand", expression: """
                (caption, callback) => {
                  const value = String(caption).trim();
                  if (!value || value.length > 120 || typeof callback !== 'function') {
                    throw new TypeError('Invalid userscript menu command');
                  }
                  const id = String(__candyMenuSequence = (__candyMenuSequence % 2147483647) + 1);
                  __candyMenuCallbacks.set(id, callback);
                  void __candyPost({type:'register-menu', commandId:id, caption:value});
                  return id;
                }
                """))
            api.append("__candyGM.registerMenuCommand = (caption, callback) => Promise.resolve(globalThis.GM_registerMenuCommand(caption, callback));")
        }
        if grants.contains(.unregistermenucommand) {
            api.append(define("GM_unregisterMenuCommand", expression: """
                id => {
                  const commandId = String(id);
                  const existed = __candyMenuCallbacks.delete(commandId);
                  void __candyPost({type:'unregister-menu', commandId});
                  return existed;
                }
                """))
            api.append("__candyGM.unregisterMenuCommand = id => Promise.resolve(globalThis.GM_unregisterMenuCommand(id));")
        }
        if grants.contains(.openintab) {
            api.append(define("GM_openInTab", expression: """
                (url, options = {}) => {
                  const resolved = new URL(String(url), location.href);
                  if (!/^https?:$/.test(resolved.protocol)) throw new TypeError('Only HTTP(S) URLs are allowed');
                  const active = typeof options === 'boolean' ? options : options && options.active === true;
                  void __candyPost({type:'open-tab', url:resolved.href, active});
                  return Object.freeze({close: () => {}});
                }
                """))
            api.append("__candyGM.openInTab = (url, options) => Promise.resolve(globalThis.GM_openInTab(url, options));")
        }
        return api.joined(separator: "\n")
    }

    private static func define(_ name: String, expression: String) -> String {
        "Object.defineProperty(globalThis, \(json(name)), {value:(\(expression)), writable:false, configurable:false, enumerable:false});"
    }

    private static func json(_ value: Any) -> String {
        guard let data = try? JSONSerialization.data(
            withJSONObject: value,
            options: [.sortedKeys, .fragmentsAllowed]
        ),
              let result = String(data: data, encoding: .utf8) else {
            preconditionFailure("Topping bootstrap contains non-JSON data")
        }
        return result
    }

    private static func safeIdentifier(_ value: String) -> String {
        value.unicodeScalars.map { scalar in
            CharacterSet.alphanumerics.contains(scalar) ? String(scalar) : "_"
        }.joined()
    }
}
