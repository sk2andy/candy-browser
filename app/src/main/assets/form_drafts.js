/* Local, explicit recovery of user-edited multiline fields. No network or page storage. */
(function (scope) {
  "use strict";
  function createFormDrafts(win, emit) {
    const doc = win.document;
    const selector = 'textarea,[contenteditable="true"],[contenteditable="plaintext-only"]';
    const sensitive = /pass(word|code)?|(?:^|[-_ ])pin(?:$|[-_ ])|otp|one.?time|credit|debit|card|cvv|cvc|iban|bank|payment|billing|security.?code|token|secret|ssn/i;
    const documentId = Array.from(win.crypto.getRandomValues(new Uint8Array(16)),
      value => value.toString(16).padStart(2, "0")).join("");
    const edited = new Map();
    let enabled = false;
    let url = win.location.href;
    let submitted = false;

    function metadata(node) {
      return ["id", "name", "autocomplete", "aria-label", "placeholder"].map(
        name => node.getAttribute(name) || "").join(" ");
    }
    function ownerForm(node) { return node.form || node.closest("form"); }
    function hasSensitiveInputs(form) {
      const inputs = Array.from(form.elements || []).filter(input => input.tagName === "INPUT");
      return inputs.length > 128 || inputs.some(input =>
        input.type !== "hidden" && (input.type === "password" || sensitive.test(metadata(input)) ||
          /(?:^|\s)cc-/.test((input.getAttribute("autocomplete") || "").toLowerCase())));
    }
    function eligible(node) {
      if (!node || !node.matches(selector) || node.disabled || node.readOnly ||
          node.closest('[hidden],[aria-hidden="true"],[data-private],[data-sensitive],[autocomplete="off"]') ||
          node.getClientRects().length === 0 || sensitive.test(metadata(node)) ||
          (node.getAttribute("autocomplete") || "").toLowerCase() === "off") return false;
      const style = win.getComputedStyle(node);
      if (style.display === "none" || style.visibility === "hidden") return false;
      if (node.tagName !== "TEXTAREA" && node.parentElement?.closest('[contenteditable]')) return false;
      const form = ownerForm(node);
      return !form || (!sensitive.test(metadata(form)) &&
        (form.getAttribute("autocomplete") || "").toLowerCase() !== "off" && !hasSensitiveInputs(form));
    }
    function key(node) {
      const form = ownerForm(node);
      return JSON.stringify([node.tagName, node.id || "", node.getAttribute("name") || "",
        node.getAttribute("aria-label") || "", form?.id || "", form?.getAttribute("name") || "",
        form?.getAttribute("action") || ""]).slice(0, 1025);
    }
    function candidates() {
      const nodes = doc.querySelectorAll(selector);
      if (nodes.length > 128) return [];
      return Array.from(nodes).filter(eligible);
    }
    function unique(node, nodes) {
      const value = key(node);
      const hasIdentity = [node.id, node.getAttribute("name"), node.getAttribute("aria-label")].some(value => value?.trim());
      return hasIdentity && value.length <= 1024 && nodes.filter(other => key(other) === value).length === 1;
    }
    function value(node) {
      return node.tagName === "TEXTAREA" ? node.value : node.innerText;
    }
    function empty(node) {
      if (node.tagName === "TEXTAREA") return node.value === "";
      return !(node.textContent || "").trim() && !node.querySelector("img,video,audio,iframe,object,canvas,svg,input");
    }
    function currentUrl() {
      if (url !== win.location.href) {
        url = win.location.href;
        edited.clear();
        submitted = false;
      }
      return /^https?:\/\//.test(url) && win.top === win && !win.location.username && !win.location.password;
    }
    function publish(ready = false) {
      if (!enabled || !currentUrl()) return;
      const nodes = candidates();
      const fields = [];
      let total = 0;
      for (const [fieldKey, node] of edited) {
        if (!node.isConnected || !eligible(node) || !unique(node, nodes) || key(node) !== fieldKey) continue;
        const text = value(node);
        if (typeof text !== "string" || text.length > 16000 || text.includes("\0")) continue;
        total += text.length;
        if (total > 64000 || fields.length >= 16) break;
        fields.push({ key: fieldKey, value: text });
      }
      emit({ url, documentId, fields, ready, submitted });
    }
    function input(event) {
      if (!enabled || !event.isTrusted || !currentUrl()) return;
      submitted = false;
      const node = event.target?.closest(selector);
      const nodes = candidates();
      if (!eligible(node) || !unique(node, nodes)) return;
      if (edited.size >= 16 && !edited.has(key(node))) return;
      edited.set(key(node), node);
      // Send immediately: a reload directly after typing must not lose a debounce window.
      publish();
    }
    function clear(event) {
      if (!enabled || !event.isTrusted || !currentUrl()) return;
      const form = event.target;
      if (!Array.from(edited.values()).some(node => ownerForm(node) === form)) return;
      edited.clear();
      submitted = true;
      publish();
    }
    function pagehide() { if (edited.size && !submitted) publish(); }
    doc.addEventListener("input", input, true);
    doc.addEventListener("submit", clear, true);
    doc.addEventListener("reset", clear, true);
    win.addEventListener("pagehide", pagehide, true);
    doc.addEventListener("DOMContentLoaded", () => publish(true), { once: true });

    return {
      setEnabled(value) {
        const wasEnabled = enabled;
        enabled = value === true;
        if (!enabled) edited.clear();
        if (enabled && !wasEnabled) submitted = false;
        if (enabled && doc.readyState !== "loading") publish(true);
      },
      restore(payload) {
        if (!enabled || !currentUrl() || submitted || payload?.url !== url ||
            payload.documentId !== documentId || !Array.isArray(payload.fields) ||
            payload.fields.length > 16) return false;
        const nodes = candidates();
        let restored = false;
        let total = 0;
        for (const field of payload.fields) {
          if (typeof field?.key !== "string" || field.key.length > 1024 ||
              typeof field.value !== "string" || field.value.length > 16000 || field.value.includes("\0")) return false;
          total += field.value.length;
          if (total > 64000) return false;
        }
        for (const field of payload.fields) {
          const matches = nodes.filter(node => key(node) === field.key);
          if (matches.length !== 1 || !empty(matches[0]) || !field.value.trim()) continue;
          const node = matches[0];
          if (!eligible(node) || !unique(node, candidates()) || payload.url !== win.location.href) continue;
          if (node.tagName === "TEXTAREA") {
            Object.getOwnPropertyDescriptor(win.HTMLTextAreaElement.prototype, "value").set.call(node, field.value);
          } else {
            node.textContent = field.value;
          }
          node.dispatchEvent(new win.Event("input", { bubbles: true }));
          node.dispatchEvent(new win.Event("change", { bubbles: true }));
          edited.set(field.key, node);
          restored = true;
        }
        if (restored) publish();
        return restored;
      },
      close() {
        enabled = false;
        edited.clear();
        doc.removeEventListener("input", input, true);
        doc.removeEventListener("submit", clear, true);
        doc.removeEventListener("reset", clear, true);
        win.removeEventListener("pagehide", pagehide, true);
      },
    };
  }
  scope.CandyFormDraftFactory = { create: createFormDrafts };
})(globalThis);
