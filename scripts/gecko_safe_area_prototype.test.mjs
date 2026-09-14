import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import vm from 'node:vm';

const source = readFileSync(new URL('../app/src/main/assets/candy_privacy/content_safe_area_prototype.js', import.meta.url), 'utf8');

function fixture({ density = 3, nativeTop = 96, normalizePixels = false } = {}) {
  let clock = 0; let timerId = 0; let observer;
  const timers = new Map(); const listeners = new Map(); const mutations = []; const registrations = [];
  const reads = { style: 0, rect: 0, selector: 0 }; let writes = 0;
  class Element {
    constructor(position = 'static', top = 'auto', tag = 'div') {
      this.localName = tag; this.parentElement = null; this.children = [];
      this.isConnected = true; this.computed = { position, top }; this.properties = new Map();
      this.style = {
        getPropertyValue: (name) => this.properties.get(name)?.value || '',
        getPropertyPriority: (name) => this.properties.get(name)?.priority || '',
        setProperty: (name, value, priority = '') => {
          const oldValue = this.getAttribute('style');
          if (normalizePixels && ['top', 'padding-top'].includes(name) && /^[+-]?[\d.]+px$/.test(value)) {
            value = `${Number(Number(value.slice(0, -2)).toFixed(4))}px`;
          }
          this.properties.set(name, { value, priority }); writes++;
          if (observer?.connected) mutations.push({ type: 'attributes', target: this, attributeName: 'style', oldValue });
        },
        removeProperty: (name) => {
          const oldValue = this.getAttribute('style');
          this.properties.delete(name); writes++;
          if (observer?.connected) mutations.push({ type: 'attributes', target: this, attributeName: 'style', oldValue });
        },
      };
    }
    append(element) { this.children.push(element); element.parentElement = this; return element; }
    contains(element) {
      for (let current = element; current; current = current.parentElement) if (current === this) return true;
      return false;
    }
    get firstElementChild() { return this.children[0] || null; }
    get nextElementSibling() {
      const siblings = this.parentElement?.children || [];
      return siblings[siblings.indexOf(this) + 1] || null;
    }
    getAttribute(name) { return name === 'style' && this.properties.size ? JSON.stringify([...this.properties]) : null; }
    getBoundingClientRect() { reads.rect++; return { top: 0, left: 0, width: 360, height: 40, right: 360, bottom: 40 }; }
  }
  const root = new Element('static', 'auto', 'html');
  const body = root.append(new Element('static', 'auto', 'body'));
  const config = { ready: true, enabled: true, cssSafeAreaTopInsetPx: nativeTop, navigationGeneration: 1, revision: 1,
    recheckAddedElements: true, recheckChangedElements: true, recheckOnResize: true,
    interactionWindowMillis: 1000, mutationDebounceMillis: 150, maxElementsPerBatch: 16, maxInitialElements: 512 };
  const document = { documentElement: root, body, readyState: 'complete',
    querySelector: (selector) => {
      reads.selector++;
      assert.ok(['header', 'nav', '[role="banner"]'].includes(selector), 'Only bounded semantic fallback queries are expected');
      if (!document.documentElement) return null;
      const pending = [document.documentElement];
      while (pending.length) {
        const element = pending.shift();
        if (element.localName === selector || (selector === '[role="banner"]' && element.role === 'banner')) return element;
        pending.unshift(...element.children);
      }
      return null;
    },
    addEventListener: (type, callback, options) => {
      listeners.set(`document:${type}`, callback); registrations.push({ target: 'document', type, options });
    },
  };
  const windowProxy = {};
  const context = vm.createContext({ Element, document, self: windowProxy, top: windowProxy,
    devicePixelRatio: density,
    CandyContentTopInset: { cssSafeAreaConfiguration: () => ({ ...config }) },
    getComputedStyle: (element) => {
      reads.style++;
      return { display: 'block', visibility: 'visible', paddingTop: '0px', ...element.computed,
        ...(element.style.getPropertyValue('top') ? { top: element.style.getPropertyValue('top') } : {}),
        ...(element.style.getPropertyValue('padding-top') ? { paddingTop: element.style.getPropertyValue('padding-top') } : {}) };
    },
    performance: { now: () => clock },
    setTimeout: (callback, delay = 0) => {
      const id = ++timerId; timers.set(id, { callback, at: clock + delay });
      assert.ok(timers.size <= 1, 'Only one prototype worker may be pending'); return id;
    },
    clearTimeout: (id) => timers.delete(id),
    addEventListener: (type, callback, options) => {
      listeners.set(`window:${type}`, callback); registrations.push({ target: 'window', type, options });
    },
    MutationObserver: class {
      constructor(callback) { this.callback = callback; observer = this; }
      observe() { this.connected = true; }
      disconnect() { this.connected = false; mutations.length = 0; }
    },
  });
  const flush = () => {
    for (let steps = 0; steps < 200; steps++) {
      if (mutations.length && observer?.connected) { observer.callback(mutations.splice(0)); continue; }
      if (!timers.size) return;
      const [id, timer] = [...timers].sort((a, b) => a[1].at - b[1].at || a[0] - b[0])[0];
      timers.delete(id); clock = Math.max(clock, timer.at); timer.callback();
    }
    assert.fail('Prototype work or own-style mutation loop did not terminate');
  };
  return { body, context, config, reads, timers, registrations, flush, writes: () => writes,
    element: (position, top, tag) => body.append(new Element(position, top, tag)),
    start(drain = true) { vm.runInContext(source, context); if (drain) flush(); },
    configure(next) { Object.assign(config, next); context.__candyConfigureCssSafeArea(); flush(); },
    event(type, target = ['scroll', 'resize'].includes(type) ? 'window' : 'document') {
      const listener = listeners.get(`${target}:${type}`);
      assert.equal(typeof listener, 'function', `Actual ${target} ${type} listener must exist`);
      listener({ type, isTrusted: true });
    },
    mutate(element, attributeName) { observer.callback([{ type: 'attributes', target: element, attributeName }]); },
    added(element) { observer.callback([{ type: 'childList', target: body, addedNodes: [element], removedNodes: [] }]); },
  };
}

test('body is protected once; fixed/sticky add inset once to full numeric px at or below inset without padding', () => {
  const f = fixture(); const nodes = [];
  for (const position of ['fixed', 'sticky']) {
    for (const top of ['0px', '8px', 'auto', '-8px', '-1px', '32px', '48px', '10%', 'calc(8px + 2px)', '8px-junk']) {
      const element = f.element(position, top); element.style.setProperty('padding-top', '7px');
      nodes.push({ element, top });
    }
  }
  f.start();
  assert.equal(f.body.style.getPropertyValue('padding-top'), '32px');
  for (const { element, top } of nodes) {
    const expected = ['0px', '8px', '-8px', '-1px', '32px'].includes(top) ? `${Number.parseFloat(top) + 32}px` : '';
    assert.equal(element.style.getPropertyValue('top'), expected, top);
    assert.equal(element.style.getPropertyValue('padding-top'), '7px');
  }
  f.configure({ revision: 2 });
  assert.equal(f.body.style.getPropertyValue('padding-top'), '32px');
  for (const { element, top } of nodes) {
    const expected = ['0px', '8px', '-8px', '-1px', '32px'].includes(top) ? `${Number.parseFloat(top) + 32}px` : '';
    f.event('click'); f.mutate(element, 'class'); f.mutate(element, 'style'); f.flush();
    f.event('resize'); f.flush();
    assert.equal(element.style.getPropertyValue('top'), expected, `No accumulation after mutation/resize: ${top}`);
  }
  const stickyZero = nodes.find(({ element, top }) => element.computed.position === 'sticky' && top === '0px').element;
  const ownedStyleReads = f.reads.style;
  for (let repeat = 0; repeat < 100; repeat++) {
    f.event('click'); f.mutate(stickyZero, 'class'); f.mutate(stickyZero, 'style'); f.flush();
    assert.equal(stickyZero.style.getPropertyValue('top'), '32px');
  }
  assert.equal(f.reads.style, ownedStyleReads, 'Retained owned leaf skips computed-style reads');
  for (let repeat = 0; repeat < 100; repeat++) {
    f.event('resize'); f.flush(); assert.equal(stickyZero.style.getPropertyValue('top'), '32px');
  }
  const safe = fixture(); safe.body.style.setProperty('padding-top', '40px'); safe.start();
  assert.equal(safe.body.style.getPropertyValue('padding-top'), '40px');
  const ready = fixture(); const root = ready.context.document.documentElement;
  ready.context.document.documentElement = null; ready.context.document.body = null; ready.start();
  ready.context.document.documentElement = root; ready.context.document.body = ready.body;
  const header = ready.element('fixed', '0px'); ready.event('DOMContentLoaded'); ready.flush();
  assert.equal(ready.body.style.getPropertyValue('padding-top'), '32px');
  assert.equal(header.style.getPropertyValue('top'), '32px');
  const fullQueue = fixture(); const roots = Array.from({ length: 16 }, () => fullQueue.element('fixed', '0px'));
  fullQueue.start(false); fullQueue.event('click');
  for (const node of roots) fullQueue.mutate(node, 'class');
  fullQueue.flush();
  assert.ok(roots.every((node) => node.style.getPropertyValue('top') === '32px'), 'Full queue must not corrupt another root job');
  const late = fixture();
  for (let index = 0; index < 600; index++) late.element('static', 'auto');
  const wrapper = late.element('sticky', '0px');
  const semantic = late.element('static', 'auto', 'header');
  late.body.children.splice(late.body.children.indexOf(semantic), 1); wrapper.append(semantic);
  late.start();
  assert.equal(wrapper.style.getPropertyValue('top'), '32px', 'First semantic header seeds its late sticky ancestor beyond the body cap');
  assert.equal(late.reads.selector, 1, 'One initial semantic query, not a page-wide selector loop');
  assert.equal(late.reads.rect, 0);
  late.event('scroll'); late.flush();
  assert.equal(wrapper.style.getPropertyValue('top'), '32px');
  const streamed = fixture(); streamed.context.document.readyState = 'loading';
  for (let index = 0; index < 600; index++) streamed.element('static', 'auto');
  const earlierNav = streamed.element('sticky', '0px', 'nav'); earlierNav.hidden = true;
  const fallbackWrapper = streamed.element('sticky', '0px');
  const fallbackHeader = streamed.element('static', 'auto', 'header');
  streamed.body.children.splice(streamed.body.children.indexOf(fallbackHeader), 1); fallbackWrapper.append(fallbackHeader);
  streamed.start();
  assert.equal(streamed.body.style.getPropertyValue('padding-top'), '32px');
  assert.equal(streamed.reads.selector, 0, 'Parser-loading owned Body must not consume the semantic seed');
  streamed.context.document.readyState = 'interactive'; streamed.event('DOMContentLoaded'); streamed.flush();
  assert.equal(streamed.reads.selector, 0, 'Parser-ready fallback must not consume the semantic seed');
  streamed.body.children.splice(streamed.body.children.indexOf(fallbackWrapper), 1);
  fallbackWrapper.isConnected = false; fallbackHeader.isConnected = false;
  const finalWrapper = streamed.element('sticky', '0px');
  const finalHeader = streamed.element('static', 'auto', 'header');
  streamed.body.children.splice(streamed.body.children.indexOf(finalHeader), 1); finalWrapper.append(finalHeader);
  streamed.context.document.readyState = 'complete'; streamed.event('load', 'window'); streamed.flush();
  assert.equal(finalWrapper.style.getPropertyValue('top'), '32px', 'Load-complete replacement is seeded despite already-owned Body');
  assert.equal(earlierNav.style.getPropertyValue('top'), '', 'Header is preferred even when a hidden NAV appears earlier');
  assert.equal(fallbackWrapper.style.getPropertyValue('top'), '');
  assert.equal(streamed.reads.selector, 1, 'Preferred header needs only one initial query');
  assert.equal(streamed.reads.rect, 0);
  streamed.event('load', 'window'); streamed.flush();
  assert.equal(streamed.reads.selector, 1, 'Retained semantic seed is not polled on later load events');
  const fractional = fixture({ density: 3, nativeTop: 137, normalizePixels: true });
  const roundedEqual = fractional.element('fixed', '45.6667px');
  const beyondTolerance = fractional.element('sticky', '45.6678px');
  fractional.start();
  assert.equal(roundedEqual.style.getPropertyValue('top'), '91.3334px', 'Rounded-up computed equal is eligible');
  assert.equal(beyondTolerance.style.getPropertyValue('top'), '', 'More than 0.001px above inset is not eligible');
  fractional.configure({ enabled: false });
  assert.equal(roundedEqual.style.getPropertyValue('top'), '');
});

test('trusted class/style/hidden changes repair positioning without own-style loops or scroll work', () => {
  const f = fixture(); const element = f.element('static', 'auto'); f.start();
  Object.assign(element.computed, { position: 'fixed', top: '0px' });
  f.mutate(element, 'class'); f.flush();
  assert.equal(element.style.getPropertyValue('top'), '', 'No repair window before trusted interaction');
  f.event('click'); f.mutate(element, 'class'); f.flush();
  assert.equal(element.style.getPropertyValue('top'), '32px');
  const added = f.element('sticky', '8px'); f.added(added); f.flush();
  assert.equal(added.style.getPropertyValue('top'), '40px');
  const resized = f.element('static', 'auto'); resized.computed.position = 'fixed'; resized.computed.top = '0px';
  f.event('resize'); f.flush();
  assert.equal(resized.style.getPropertyValue('top'), '32px', 'Enabled resize recheck works with unchanged policy');
  f.configure({ recheckOnResize: false, revision: 2 });
  const noResize = f.element('fixed', '0px'); f.event('resize'); f.flush();
  assert.equal(noResize.style.getPropertyValue('top'), '', 'Disabled resize recheck remains inert');
  const before = { reads: { ...f.reads }, writes: f.writes() };
  const capturedScroll = f.registrations.find((entry) => entry.target === 'document' && entry.type === 'scroll');
  assert.equal(capturedScroll.options.capture, true); assert.equal(capturedScroll.options.passive, true);
  for (const target of ['document', 'window']) { f.event('scroll', target); f.flush(); }
  assert.deepEqual({ reads: { ...f.reads }, writes: f.writes() }, before);
  for (const attribute of ['style', 'hidden']) { f.event('click'); f.mutate(element, attribute); f.flush(); }
  assert.equal(element.style.getPropertyValue('top'), '32px');
});

test('disable cancels pending work and restores originals unless authors changed them; inset changes do not accumulate', () => {
  const f = fixture(); f.body.style.setProperty('padding-top', '4px');
  const restored = f.element('fixed', '2px'); restored.style.setProperty('top', '2px', 'important');
  const authored = f.element('sticky', '0px'); f.start();
  f.configure({ cssSafeAreaTopInsetPx: 48, revision: 2 });
  assert.equal(f.body.style.getPropertyValue('padding-top'), '16px');
  assert.equal(restored.style.getPropertyValue('top'), '18px');
  authored.style.setProperty('top', '19px');
  const pending = f.element('fixed', '0px'); f.event('click'); f.mutate(pending, 'class');
  f.configure({ enabled: false, revision: 3 });
  assert.equal(f.body.style.getPropertyValue('padding-top'), '4px');
  assert.equal(restored.style.getPropertyValue('top'), '2px');
  assert.equal(restored.style.getPropertyPriority('top'), 'important');
  assert.equal(authored.style.getPropertyValue('top'), '19px');
  assert.equal(pending.style.getPropertyValue('top'), '');
  assert.equal(f.context.document.documentElement.style.getPropertyValue('--candy-safe-area-inset-top'), '');
  const normalized = fixture({ density: 2.608695652173913, nativeTop: 136, normalizePixels: true });
  normalized.body.style.setProperty('padding-top', '4px');
  const normalizedTop = normalized.element('fixed', '2px'); normalizedTop.style.setProperty('top', '2px');
  normalized.start();
  assert.equal(normalized.body.style.getPropertyValue('padding-top'), '52.1333px');
  assert.equal(normalizedTop.style.getPropertyValue('top'), '54.1333px');
  normalized.configure({ enabled: false, revision: 2 });
  assert.equal(normalized.body.style.getPropertyValue('padding-top'), '4px', 'Canonical CSSOM value remains owned for cleanup');
  assert.equal(normalizedTop.style.getPropertyValue('top'), '2px');
});
