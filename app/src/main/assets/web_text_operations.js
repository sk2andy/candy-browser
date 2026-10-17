(() => {
  "use strict";
  if (globalThis.CandyWebTextOperations) return;
  const excluded = 'script,style,noscript,input,textarea,select,button,[contenteditable]:not([contenteditable="false"]),[data-candy-translation]';
  function canonical(value) {
    try { const u = new URL(value); u.hash = ''; return /^https?:$/.test(u.protocol) ? u.href : null; }
    catch (_) { return null; }
  }
  function visible(element, cache) {
    const visited = [];
    let shown = true;
    for (let parent = element; parent; parent = parent.parentElement) {
      if (cache.has(parent)) { shown = cache.get(parent); break; }
      if (visited.length >= 64) { shown = false; break; }
      visited.push(parent);
      const style = getComputedStyle(parent);
      if (style.display === 'none' || style.visibility === 'hidden' || style.visibility === 'collapse' ||
          style.contentVisibility === 'hidden' || style.opacity === '0') { shown = false; break; }
    }
    visited.forEach(node => cache.set(node, shown));
    return shown;
  }
  function textIndex() {
    if (!document.body) return { text: '', segments: [] };
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    const segments = [], visibility = new WeakMap();
    let text = '', node, count = 0;
    while ((node = walker.nextNode()) && count++ < 20000 && text.length < 500000) {
      if (!node.parentElement || node.parentElement.closest(excluded) || !visible(node.parentElement, visibility)) continue;
      const content = node.textContent.slice(0, 500000 - text.length);
      segments.push({ node, start: text.length, end: text.length + content.length });
      text += content;
    }
    return { text, segments };
  }
  function findRange(quote, index = textIndex()) {
    if (typeof quote !== 'string' || !quote.trim() || quote.length > 8000) return null;
    const { text, segments } = index;
    const start = text.indexOf(quote);
    // Ambiguous or changed quotes stay in the library; never mark a guessed match.
    if (start < 0 || text.indexOf(quote, start + 1) >= 0) return null;
    const end = start + quote.length;
    const first = segments.find(s => s.start <= start && s.end > start);
    const last = segments.find(s => s.start < end && s.end >= end);
    if (!first || !last) return null;
    const range = document.createRange();
    range.setStart(first.node, start - first.start);
    range.setEnd(last.node, end - last.start);
    if (range.toString() !== quote) return null;
    const fragment = range.cloneContents();
    if (fragment.querySelector?.(excluded)) return null;
    return range;
  }
  function highlights(payload) {
    if (!globalThis.CSS?.highlights || typeof Highlight !== 'function' || !Array.isArray(payload.annotations)) return false;
    let style = document.getElementById('__candy_highlight_style');
    if (!style) {
      style = document.createElement('style');
      style.id = '__candy_highlight_style';
      style.textContent = '::highlight(candy-saved) { background-color: #ffe082; color: #211a00; }';
      (document.head || document.documentElement).appendChild(style);
    }
    const index = textIndex();
    const ranges = payload.annotations.slice(0, 200).map(a => findRange(a.quote, index)).filter(Boolean);
    CSS.highlights.set('candy-saved', new Highlight(...ranges));
    return true;
  }
  function translate(payload) {
    if (typeof payload.replacement !== 'string' || !payload.replacement.trim() || payload.replacement.length > 24000) return false;
    const range = findRange(payload.original);
    if (!range) return false;
    const wrapper = document.createElement('span');
    wrapper.dataset.candyTranslation = 'true';
    wrapper.setAttribute('role', 'button');
    wrapper.tabIndex = 0;
    wrapper.title = String(payload.toggleLabel || 'Original / translation').slice(0, 160);
    wrapper.setAttribute('aria-label', wrapper.title);
    wrapper.style.textDecoration = 'underline dotted';
    const original = range.extractContents();
    const originalNodes = Array.from(original.childNodes);
    wrapper.textContent = payload.replacement;
    range.insertNode(wrapper);
    let translated = true;
    const toggle = () => {
      if (translated) { wrapper.replaceChildren(...originalNodes); }
      else { wrapper.textContent = payload.replacement; }
      translated = !translated;
    };
    wrapper.addEventListener('click', event => { if (!event.isTrusted) return; event.preventDefault(); event.stopPropagation(); toggle(); });
    wrapper.addEventListener('keydown', event => {
      if (event.isTrusted && (event.key === 'Enter' || event.key === ' ')) { event.preventDefault(); toggle(); }
    });
    return true;
  }
  const documentToken = Array.from(crypto.getRandomValues(new Uint32Array(4)), n => n.toString(16)).join('-');
  globalThis.CandyWebTextOperations = {
    documentToken,
    apply(payload) {
      if (!payload || canonical(payload.url) !== canonical(location.href) || !canonical(payload.url)) return false;
      if (payload.operation === 'highlights') return highlights(payload);
      if (payload.operation === 'translate') return translate(payload);
      return false;
    }
  };
})();
