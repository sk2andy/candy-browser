package dev.sk2andy.materialbrowser.browser.userscript

import org.json.JSONObject

internal object UserScriptApi {
    fun bootstrap(
        script: UserScript,
        encodedValues: Map<String, String>,
    ): String {
        val info = JSONObject()
            .put("scriptHandler", "Candy")
            .put("version", "1")
            .put(
                "script",
                JSONObject()
                    .put("id", script.id)
                    .put("name", script.name)
                    .put(
                        "grants",
                        script.grants.map(UserScriptGrant::metadataValue),
                    ),
            )
        val values = JSONObject().also { output ->
            encodedValues.toSortedMap().forEach { (key, value) -> output.put(key, value) }
        }
        val definitions = mutableListOf<String>()
        definitions += defineValue("GM_info", info.toString())
        if (UserScriptGrant.AddStyle in script.grants) {
            definitions += defineFunction(
                "GM_addStyle",
                """
                    (css) => {
                        const style = document.createElement("style");
                        style.textContent = String(css);
                        (document.head || document.documentElement).appendChild(style);
                        return style;
                    }
                """.trimIndent(),
            )
        }
        if (script.grants.any(RESOURCE_GRANTS::contains)) {
            definitions += resourceApi(script)
        }
        if (script.grants.any(VALUE_GRANTS::contains)) {
            definitions += valueApi(values.toString(), script.grants)
        }
        if (script.grants.any(INTERACTION_GRANTS::contains)) {
            definitions += interactionApi(
                grants = script.grants,
                bridgeAlreadyDefined = script.grants.any(MUTATING_VALUE_GRANTS::contains),
            )
        }
        val promiseEntries = buildList {
            add("info: globalThis.GM_info")
            if (UserScriptGrant.AddStyle in script.grants) {
                add("addStyle: (css) => Promise.resolve().then(() => globalThis.GM_addStyle(css))")
            }
            if (UserScriptGrant.GetValue in script.grants) {
                add("getValue: (key, fallback) => Promise.resolve().then(() => globalThis.GM_getValue(key, fallback))")
            }
            if (UserScriptGrant.SetValue in script.grants) {
                add("setValue: (key, value) => Promise.resolve().then(() => __candySetValue(key, value))")
            }
            if (UserScriptGrant.DeleteValue in script.grants) {
                add("deleteValue: (key) => Promise.resolve().then(() => __candyDeleteValue(key))")
            }
            if (UserScriptGrant.ListValues in script.grants) {
                add("listValues: () => Promise.resolve().then(() => globalThis.GM_listValues())")
            }
            if (UserScriptGrant.GetResourceText in script.grants) {
                add("getResourceText: (name) => Promise.resolve().then(() => globalThis.GM_getResourceText(name))")
            }
            if (UserScriptGrant.GetResourceUrl in script.grants) {
                add("getResourceUrl: (name) => Promise.resolve().then(() => globalThis.GM_getResourceURL(name))")
            }
            if (UserScriptGrant.RegisterMenuCommand in script.grants) {
                add(
                    "registerMenuCommand: (caption, callback, options) => " +
                        "Promise.resolve(globalThis.GM_registerMenuCommand(" +
                        "caption, callback, options))",
                )
            }
            if (UserScriptGrant.UnregisterMenuCommand in script.grants) {
                add(
                    "unregisterMenuCommand: (id) => " +
                        "Promise.resolve(globalThis.GM_unregisterMenuCommand(id))",
                )
            }
            if (UserScriptGrant.OpenInTab in script.grants) {
                add(
                    "openInTab: (url, options) => " +
                        "Promise.resolve(globalThis.GM_openInTab(url, options))",
                )
            }
        }
        definitions += defineValue("GM", "Object.freeze({${promiseEntries.joinToString(",")}})")
        return """
            (() => {
                "use strict";
                ${definitions.joinToString(separator = "\n")}
            })();
        """.trimIndent()
    }

    private fun resourceApi(script: UserScript): String {
        val resources = JSONObject().also { output ->
            script.resources.sortedBy(UserScriptResource::name).forEach { resource ->
                output.put(
                    resource.name,
                    JSONObject()
                        .put("content", resource.encodedContent)
                        .put("mimeType", resource.mimeType),
                )
            }
        }
        val definitions = mutableListOf<String>()
        definitions += """
            const __candyResources = $resources;
            const __candyResource = (name) => {
                const key = String(name);
                if (!Object.prototype.hasOwnProperty.call(__candyResources, key)) {
                    throw new Error("Unknown userscript resource");
                }
                return __candyResources[key];
            };
        """.trimIndent()
        if (UserScriptGrant.GetResourceText in script.grants) {
            definitions += defineFunction(
                "GM_getResourceText",
                """
                    (name) => {
                        const binary = atob(__candyResource(name).content);
                        const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0));
                        return new TextDecoder("utf-8").decode(bytes);
                    }
                """.trimIndent(),
            )
        }
        if (UserScriptGrant.GetResourceUrl in script.grants) {
            definitions += defineFunction(
                "GM_getResourceURL",
                "(name) => { const resource = __candyResource(name); " +
                    "return `data:${'$'}{resource.mimeType};base64,${'$'}{resource.content}`; }",
            )
        }
        return definitions.joinToString(separator = "\n")
    }

    private fun valueApi(
        encodedValues: String,
        grants: Set<UserScriptGrant>,
    ): String {
        val functions = mutableListOf<String>()
        functions += """
            const __candyValues = Object.assign(Object.create(null), $encodedValues);
            const __candyKey = (key) => {
                const value = String(key);
                if (!value || value.length > ${UserScriptBridgeContract.MAX_KEY_CHARS}) {
                    throw new TypeError("Invalid userscript value key");
                }
                return value;
            };
            const __candyDecode = (encoded, fallback) => {
                if (encoded === undefined) return fallback;
                try { return JSON.parse(encoded); } catch (_) { return fallback; }
            };
        """.trimIndent()
        if (grants.any(MUTATING_VALUE_GRANTS::contains)) {
            functions += """
                const __candyBridge = globalThis.${UserScriptBridgeContract.BRIDGE_NAME};
                const __candyPending = new Map();
                const __candyMutationQueue = [];
                let __candyRequestId = 0;
                let __candyRateStartedAt = 0;
                let __candyRateCount = 0;
                let __candyMutationActive = false;
                let __candyConfirmedSnapshot = JSON.stringify(__candyValues);
                const __candyRestore = (snapshot) => {
                    const values = JSON.parse(snapshot);
                    if (!values || Array.isArray(values) || typeof values !== "object") {
                        throw new TypeError("Invalid userscript value snapshot");
                    }
                    Object.keys(__candyValues).forEach((key) => delete __candyValues[key]);
                    Object.assign(__candyValues, values);
                };
                const __candyRebuildVisibleValues = () => {
                    __candyRestore(__candyConfirmedSnapshot);
                    __candyMutationQueue.forEach((item) => item.mutation());
                };
                __candyBridge.onmessage = (event) => {
                    let response;
                    try { response = JSON.parse(String(event.data)); } catch (_) { return; }
                    const pending = __candyPending.get(response.id);
                    if (!pending) return;
                    __candyPending.delete(response.id);
                    clearTimeout(pending.timeout);
                    if (response.ok === true && typeof response.snapshot === "string") {
                        try {
                            __candyRestore(response.snapshot);
                            __candyConfirmedSnapshot = response.snapshot;
                            __candyRebuildVisibleValues();
                        }
                        catch (_) {
                            __candyRebuildVisibleValues();
                            pending.reject(new Error("Invalid userscript value response"));
                            return;
                        }
                        pending.resolve();
                    }
                    else {
                        __candyRebuildVisibleValues();
                        pending.reject(new Error("Userscript value was not persisted"));
                    }
                };
                const __candyPerformMutation = (type, key, value) => {
                    __candyRequestId = (__candyRequestId % 2147483647) + 1;
                    const id = __candyRequestId;
                    return new Promise((resolve, reject) => {
                        const timeout = setTimeout(() => {
                            const pending = __candyPending.get(id);
                            if (!pending) return;
                            __candyPending.delete(id);
                            __candyRebuildVisibleValues();
                            reject(new Error("Userscript value persistence timed out"));
                        }, 10000);
                        __candyPending.set(id, { resolve, reject, timeout });
                        try {
                            const message = { type, id, key };
                            if (value !== undefined) message.value = value;
                            __candyBridge.postMessage(JSON.stringify(message));
                        } catch (error) {
                            clearTimeout(timeout);
                            __candyPending.delete(id);
                            __candyRebuildVisibleValues();
                            reject(error);
                        }
                    });
                };
                const __candyDrainMutations = () => {
                    if (__candyMutationActive || __candyMutationQueue.length === 0) return;
                    const item = __candyMutationQueue.shift();
                    __candyMutationActive = true;
                    let operation;
                    try {
                        operation = __candyPerformMutation(
                            item.type,
                            item.key,
                            item.value,
                        );
                    } catch (error) {
                        __candyMutationActive = false;
                        item.reject(error);
                        __candyDrainMutations();
                        return;
                    }
                    operation.then(item.resolve, item.reject).then(() => {
                        __candyMutationActive = false;
                        __candyDrainMutations();
                    });
                };
                const __candyEnqueueMutation = (type, key, value, mutation) => {
                    const now = Date.now();
                    if (now - __candyRateStartedAt >= 1000) {
                        __candyRateStartedAt = now;
                        __candyRateCount = 0;
                    }
                    if (__candyRateCount >=
                        ${UserScriptBridgeContract.MAX_API_MUTATIONS_PER_WINDOW}) {
                        throw new RangeError("Too many userscript value writes");
                    }
                    __candyRateCount += 1;
                    const previous = JSON.stringify(__candyValues);
                    mutation();
                    const totalBytes = new TextEncoder().encode(
                        JSON.stringify(__candyValues),
                    ).byteLength;
                    if (Object.keys(__candyValues).length >
                        ${UserScriptBridgeContract.MAX_VALUES_PER_SCRIPT} ||
                        totalBytes >
                        ${UserScriptBridgeContract.MAX_SCRIPT_VALUE_PAYLOAD_BYTES}) {
                        __candyRestore(previous);
                        throw new RangeError("Userscript value storage is full");
                    }
                    return new Promise((resolve, reject) => {
                        __candyMutationQueue.push({
                            type,
                            key,
                            value,
                            mutation,
                            resolve,
                            reject,
                        });
                        __candyDrainMutations();
                    });
                };
            """.trimIndent()
        }
        if (UserScriptGrant.GetValue in grants) {
            functions += defineFunction(
                "GM_getValue",
                "(key, fallback) => __candyDecode(__candyValues[__candyKey(key)], fallback)",
            )
        }
        if (UserScriptGrant.SetValue in grants) {
            functions += """
                const __candySetValue = (key, value) => {
                    const normalizedKey = __candyKey(key);
                    const encoded = JSON.stringify(value);
                    if (encoded === undefined) {
                        throw new TypeError("Unsupported userscript value");
                    }
                    if (new TextEncoder().encode(encoded).byteLength >
                        ${UserScriptBridgeContract.MAX_ENCODED_VALUE_BYTES}) {
                        throw new RangeError("Userscript value is too large");
                    }
                    return __candyEnqueueMutation("set-value", normalizedKey, encoded, () => {
                        __candyValues[normalizedKey] = encoded;
                    });
                };
            """.trimIndent()
            functions += defineFunction(
                "GM_setValue",
                """
                    (key, value) => {
                        void __candySetValue(key, value).catch(() => {});
                    }
                """.trimIndent(),
            )
        }
        if (UserScriptGrant.DeleteValue in grants) {
            functions += """
                const __candyDeleteValue = (key) => {
                    const normalizedKey = __candyKey(key);
                    return __candyEnqueueMutation(
                        "delete-value",
                        normalizedKey,
                        undefined,
                        () => {
                        delete __candyValues[normalizedKey];
                        },
                    );
                };
            """.trimIndent()
            functions += defineFunction(
                "GM_deleteValue",
                """
                    (key) => {
                        void __candyDeleteValue(key).catch(() => {});
                    }
                """.trimIndent(),
            )
        }
        if (UserScriptGrant.ListValues in grants) {
            functions += defineFunction("GM_listValues", "() => Object.keys(__candyValues)")
        }
        return functions.joinToString(separator = "\n")
    }

    private fun interactionApi(
        grants: Set<UserScriptGrant>,
        bridgeAlreadyDefined: Boolean,
    ): String {
        val functions = mutableListOf<String>()
        if (!bridgeAlreadyDefined) {
            functions += "const __candyBridge = globalThis.${UserScriptBridgeContract.BRIDGE_NAME};"
        }
        functions += "const __candyMenuCallbacks = new Map();"
        functions += "let __candyMenuSequence = 0;"
        functions += """
            const __candyPreviousOnMessage = __candyBridge.onmessage;
            __candyBridge.onmessage = (event) => {
                let message;
                try { message = JSON.parse(String(event.data)); } catch (_) { return; }
                if (message && message.type === "menu-invoke") {
                    const callback = __candyMenuCallbacks.get(String(message.commandId));
                    if (callback) {
                        try { callback(); } catch (error) { queueMicrotask(() => { throw error; }); }
                    }
                    return;
                }
                if (typeof __candyPreviousOnMessage === "function") {
                    __candyPreviousOnMessage.call(__candyBridge, event);
                }
            };
        """.trimIndent()
        if (UserScriptGrant.RegisterMenuCommand in grants) {
            functions += defineFunction(
                "GM_registerMenuCommand",
                """
                    (caption, callback) => {
                        const normalizedCaption = String(caption).trim();
                        if (!normalizedCaption ||
                            normalizedCaption.length >
                                ${UserScriptBridgeContract.MAX_COMMAND_CAPTION_CHARS} ||
                            typeof callback !== "function") {
                            throw new TypeError("Invalid userscript menu command");
                        }
                        __candyMenuSequence = (__candyMenuSequence % 2147483647) + 1;
                        const commandId = String(__candyMenuSequence);
                        __candyMenuCallbacks.set(commandId, callback);
                        __candyBridge.postMessage(JSON.stringify({
                            type: "register-menu",
                            commandId,
                            caption: normalizedCaption,
                        }));
                        return commandId;
                    }
                """.trimIndent(),
            )
        }
        if (UserScriptGrant.UnregisterMenuCommand in grants) {
            functions += defineFunction(
                "GM_unregisterMenuCommand",
                """
                    (id) => {
                        const commandId = String(id);
                        const existed = __candyMenuCallbacks.delete(commandId);
                        __candyBridge.postMessage(JSON.stringify({
                            type: "unregister-menu",
                            commandId,
                        }));
                        return existed;
                    }
                """.trimIndent(),
            )
        }
        if (UserScriptGrant.OpenInTab in grants) {
            functions += defineFunction(
                "GM_openInTab",
                """
                    (url, options = {}) => {
                        const resolvedUrl = new URL(String(url), location.href).href;
                        const active = typeof options === "boolean"
                            ? options
                            : options && options.active === true;
                        __candyBridge.postMessage(JSON.stringify({
                            type: "open-tab",
                            url: resolvedUrl,
                            active,
                        }));
                        return Object.freeze({ close: () => {} });
                    }
                """.trimIndent(),
            )
        }
        return functions.joinToString(separator = "\n")
    }

    private fun defineFunction(name: String, body: String): String = defineValue(name, body)

    private fun defineValue(name: String, expression: String): String = """
        Object.defineProperty(globalThis, ${JSONObject.quote(name)}, {
            value: $expression,
            writable: false,
            configurable: false,
            enumerable: false,
        });
    """.trimIndent()

    private val VALUE_GRANTS = setOf(
        UserScriptGrant.DeleteValue,
        UserScriptGrant.GetValue,
        UserScriptGrant.ListValues,
        UserScriptGrant.SetValue,
    )
    private val MUTATING_VALUE_GRANTS = setOf(
        UserScriptGrant.DeleteValue,
        UserScriptGrant.SetValue,
    )
    private val INTERACTION_GRANTS = setOf(
        UserScriptGrant.OpenInTab,
        UserScriptGrant.RegisterMenuCommand,
        UserScriptGrant.UnregisterMenuCommand,
    )
    private val RESOURCE_GRANTS = setOf(
        UserScriptGrant.GetResourceText,
        UserScriptGrant.GetResourceUrl,
    )
}
