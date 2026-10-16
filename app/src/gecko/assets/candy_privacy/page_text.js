/* Bounded visible-text extraction; mirrors RecallExtractionScript's document policy. */
(() => {
  if (self !== top) return;
  const MAX_TEXT_CHARS = 64000;
  const MAX_PAYLOAD_CHARS = 150000;
  const clean = value => (value || "").replace(/[\u0000-\u001f\u007f]+/g, " ").replace(/\s+/g, " ").trim();
  const excluded = 'script,style,noscript,template,iframe,object,embed,canvas,svg,form,input,textarea,select,button,[contenteditable],nav,aside,footer,video,audio,[hidden],[aria-hidden="true"]';

  function extract() {
    const root = document.body;
    if (!root || !/^https?:$/.test(location.protocol) || location.href.length > 8192) return null;
    const readable = new WeakMap();
    const hasReadableAncestors = element => {
      const checked = [];
      let current = element;
      let allowed = true;
      let depthLimitReached = false;
      while (current) {
        if (readable.has(current)) {
          allowed = readable.get(current);
          break;
        }
        if (checked.length >= 128) {
          allowed = false;
          depthLimitReached = true;
          break;
        }
        checked.push(current);
        const style = getComputedStyle(current);
        if (current.matches(excluded) || style.display === 'none' ||
            style.visibility === 'hidden' || style.visibility === 'collapse' ||
            style.opacity === '0' || style.contentVisibility === 'hidden') {
          allowed = false;
          break;
        }
        current = current.parentElement;
      }
      // A depth rejection applies only to this path, not unrelated sibling paths.
      if (depthLimitReached) readable.set(element, false);
      else checked.forEach(ancestor => readable.set(ancestor, allowed));
      return allowed;
    };
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    const parts = [];
    let length = 0;
    let visited = 0;
    let node;
    while (visited < 20000 && length < MAX_TEXT_CHARS && (node = walker.nextNode())) {
      visited += 1;
      const parent = node.parentElement;
      if (!parent || parent.getClientRects().length === 0 || !hasReadableAncestors(parent)) continue;
      const text = clean(node.nodeValue);
      if (!text) continue;
      const remaining = MAX_TEXT_CHARS - length;
      parts.push(text.slice(0, remaining));
      length += Math.min(text.length, remaining) + 1;
    }
    const text = clean(parts.join(" ")).slice(0, MAX_TEXT_CHARS);
    if (!text) return null;
    const payload = {
      sourceUrl: location.href,
      title: (clean(document.querySelector('meta[property="og:title"]')?.content) || clean(document.title)).slice(0, 1024),
      text,
    };
    return JSON.stringify(payload).length <= MAX_PAYLOAD_CHARS ? payload : null;
  }

  browser.runtime.onMessage.addListener(message => {
    if (message?.type !== "text-extract") return undefined;
    if (globalThis.CandyContentTopInset?.policyRevision?.() !== message.revision ||
        globalThis.CandyContentTopInset?.navigationGeneration?.() !== message.navigationGeneration) {
      return Promise.resolve(null);
    }
    try {
      return Promise.resolve(extract());
    } catch (_) {
      return Promise.resolve(null);
    }
  });
})();
