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
  let savedRanges = [];
  let annotationClickListener = null;
  let pointerStart = null;
  document.addEventListener('pointerdown', event => {
    pointerStart = event.isTrusted && event.isPrimary ? { x: event.clientX, y: event.clientY, time: event.timeStamp } : null;
  }, true);
  document.addEventListener('pointermove', event => {
    if (pointerStart && Math.hypot(event.clientX - pointerStart.x, event.clientY - pointerStart.y) > 10) pointerStart = null;
  }, true);
  document.addEventListener('pointercancel', () => { pointerStart = null; }, true);
  document.addEventListener('click', event => {
    const start = pointerStart;
    pointerStart = null;
    if (!event.isTrusted || !annotationClickListener || !start || event.timeStamp - start.time > 700 ||
        Math.hypot(event.clientX - start.x, event.clientY - start.y) > 10 || !getSelection()?.isCollapsed) return;
    const match = savedRanges.find(({ range }) => Array.from(range.getClientRects()).some(rect =>
      event.clientX >= rect.left && event.clientX <= rect.right && event.clientY >= rect.top && event.clientY <= rect.bottom));
    if (!match || !match.id || !match.range.startContainer.isConnected || match.range.toString() !== match.quote ||
        !visible(match.range.startContainer.parentElement, new WeakMap())) return;
    const hit = document.elementFromPoint(event.clientX, event.clientY);
    if (!hit || !match.range.intersectsNode(hit) || hit.closest(excluded)) return;
    event.preventDefault();
    event.stopImmediatePropagation();
    annotationClickListener(match.id);
  }, true);
  function scrollToAnnotation(payload) {
    if (typeof payload.id !== 'string' || payload.id.length < 1 || payload.id.length > 64) return false;
    const range = findRange(payload.quote);
    if (!range || !Array.from(range.getClientRects()).some(value => value.width > 0 && value.height > 0)) return false;
    for (let parent = range.startContainer.parentElement; parent && parent !== document.body; parent = parent.parentElement) {
      if (parent.scrollHeight <= parent.clientHeight || !/auto|scroll/.test(getComputedStyle(parent).overflowY)) continue;
      const rect = range.getBoundingClientRect(), container = parent.getBoundingClientRect();
      parent.scrollBy({ top: rect.top - container.top - parent.clientHeight / 2 + rect.height / 2, behavior: 'instant' });
    }
    const rect = range.getBoundingClientRect();
    window.scrollBy({ top: rect.top - window.innerHeight / 2 + rect.height / 2, behavior: 'instant' });
    return true;
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
    savedRanges = payload.annotations.slice(0, 200).map(annotation => ({
      quote: annotation.quote,
      id: typeof annotation.id === 'string' && annotation.id.length <= 64 ? annotation.id : null,
      range: findRange(annotation.quote, index),
    })).filter(value => value.range);
    CSS.highlights.set('candy-saved', new Highlight(...savedRanges.map(value => value.range)));
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
    setAnnotationClickListener(listener) {
      annotationClickListener = typeof listener === 'function' ? listener : null;
    },
    apply(payload) {
      if (!payload || canonical(payload.url) !== canonical(location.href) || !canonical(payload.url)) return false;
      if (payload.operation === 'highlights') return highlights(payload);
      if (payload.operation === 'scroll-to-annotation') return scrollToAnnotation(payload);
      if (payload.operation === 'translate') return translate(payload);
      return false;
    }
  };
})();
