import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import vm from 'node:vm';

const source = readFileSync(new URL('../app/src/main/assets/candy_privacy/content_safe_area_prototype.js', import.meta.url), 'utf8');

function fixture({ density = 3, nativeTop = 96, normalizePixels = false } = {}) {
  let clock = 0; let timerId = 0; let observer;
  const timers = new Map(); const listeners = new Map(); const mutations = []; const registrations = [];
  const reads = { style: 0, rect: 0, selector: 0 }; let writes = 0;
  let ruleWrites = 0;
  const normalize = (value) => normalizePixels && /^[+-]?[\d.]+px$/.test(value) ? `${Number(Number(value.slice(0, -2)).toFixed(4))}px` : value;
  const sheets = [];
  function ruleStyle() {
    const values = new Map();
    return { getPropertyValue: (name) => values.get(name)?.value || '',
      setProperty: (name, value, priority) => { values.set(name, { value: normalize(value), priority }); ruleWrites++; } };
  }
  class Element {
    constructor(position = 'static', top = 'auto', tag = 'div') {
      this.localName = tag; this.parentElement = null; this.children = [];
      this.isConnected = true; this.computed = { position, top }; this.properties = new Map();
      this.attributes = new Map();
      if (tag === 'style') {
        this.sheet = { cssRules: [],
          insertRule: (selector, index) => {
            this.sheet.cssRules.splice(index, 0, { selectorText: selector, style: ruleStyle() }); return index;
          }, deleteRule: (index) => this.sheet.cssRules.splice(index, 1) };
        sheets.push(this);
      }
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
    appendChild(element) { return this.append(element); }
    remove() {
      const siblings = this.parentElement?.children;
      if (siblings) siblings.splice(siblings.indexOf(this), 1);
      this.parentElement = null; this.isConnected = false;
    }
    contains(element) {
      for (let current = element; current; current = current.parentElement) if (current === this) return true;
      return false;
    }
    get firstElementChild() { return this.children[0] || null; }
    get nextElementSibling() {
      const siblings = this.parentElement?.children || [];
      return siblings[siblings.indexOf(this) + 1] || null;
    }
    getAttribute(name) { return name === 'style' ? (this.properties.size ? JSON.stringify([...this.properties]) : null) : this.attributes.get(name) ?? null; }
    setAttribute(name, value) { this.attributes.set(name, value); }
    removeAttribute(name) { this.attributes.delete(name); }
    getBoundingClientRect() { reads.rect++; return { top: 0, left: 0, width: 360, height: 40, right: 360, bottom: 40 }; }
  }
  const root = new Element('static', 'auto', 'html');
  const body = root.append(new Element('static', 'auto', 'body'));
  const config = { ready: true, enabled: true, cssSafeAreaTopInsetPx: nativeTop, navigationGeneration: 1, revision: 1,
    recheckAddedElements: true, recheckChangedElements: true, recheckOnResize: true,
    interactionWindowMillis: 1000, mutationDebounceMillis: 150, maxElementsPerBatch: 16, maxInitialElements: 512 };
  const document = { documentElement: root, body, readyState: 'complete',
    createElement: (tag) => new Element('static', 'auto', tag),
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
  function computed(element) {
    const result = { display: 'block', visibility: 'visible', paddingTop: '0px', ...element.computed };
    for (const [name, camel] of [['top', 'top'], ['padding-top', 'paddingTop']]) {
      const inline = element.properties.get(name);
      if (inline) result[camel] = inline.value;
      if (inline?.priority === 'important' || element.authorImportant?.[name]) continue;
      for (const sheet of sheets.filter((node) => node.isConnected)) {
        for (const rule of sheet.sheet.cssRules) {
          const match = /\[([^=]+)="([^"]+)"\]/.exec(rule.selectorText);
          const value = rule.style.getPropertyValue(name);
          if (match && element.getAttribute(match[1]) === match[2] && value) result[camel] = value;
        }
      }
    }
    return result;
  }
  const windowProxy = {};
  const context = vm.createContext({ Element, document, self: windowProxy, top: windowProxy,
    devicePixelRatio: density,
    CandyContentTopInset: { cssSafeAreaConfiguration: () => ({ ...config }) },
    getComputedStyle: (element) => {
      reads.style++;
      return computed(element);
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
  return { body, context, config, reads, timers, registrations, flush, computed, sheets,
    writes: () => writes, ruleWrites: () => ruleWrites,
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

test('persistent body and finite fixed/sticky rules do not accumulate on authorized rechecks', () => {
  const f = fixture(); f.body.style.setProperty('padding-top', '4px');
  const nodes = [];
  for (const position of ['fixed', 'sticky']) {
    for (const top of ['0px', '8px', '-8px', '-1px', '32px', '80px', 'auto', '10%', 'calc(8px + 2px)', '8px-junk']) {
      const element = f.element(position, top); element.style.setProperty('padding-top', '7px');
      nodes.push({ element, top });
    }
  }
  const relative = f.element('relative', '80px');
  f.start();
  assert.equal(f.computed(f.body).paddingTop, '32px');
  assert.equal(f.body.style.getPropertyValue('padding-top'), '4px', 'Body author inline is untouched');
  for (const { element, top } of nodes) {
    const expected = /^[+-]?[\d.]+px$/.test(top) ? `${Number.parseFloat(top) + 32}px` : top;
    assert.equal(f.computed(element).top, expected, top);
    assert.equal(element.style.getPropertyValue('top'), '', 'No inline top writes');
    assert.equal(f.computed(element).paddingTop, '7px');
  }
  assert.equal(f.computed(relative).top, '80px');
  const sticky = nodes[10].element;
  const before = { reads: f.reads.style, writes: f.writes(), rules: f.ruleWrites() };
  for (let repeat = 0; repeat < 100; repeat++) {
    f.event('click'); f.mutate(sticky, 'class'); f.mutate(sticky, 'style'); f.flush();
  }
  assert.deepEqual({ reads: f.reads.style, writes: f.writes(), rules: f.ruleWrites() }, before);
  for (let repeat = 0; repeat < 100; repeat++) { f.event('resize'); f.flush(); }
  assert.equal(f.computed(sticky).top, '32px');
  const larger = fixture(); larger.body.style.setProperty('padding-top', '40px'); larger.start();
  larger.body.style.setProperty('padding-top', '0px'); larger.flush();
  assert.equal(larger.computed(larger.body).paddingTop, '40px', 'Larger captured body padding persists');
  const fractional = fixture({ density: 3, nativeTop: 137, normalizePixels: true });
  const equal = fractional.element('fixed', '45.6667px');
  const above = fractional.element('sticky', '45.6678px'); fractional.start();
  assert.equal(fractional.computed(equal).top, '91.3334px');
  assert.equal(fractional.computed(above).top, '91.3345px');
  assert.equal(f.reads.rect, 0);
});

test('passive normal resets stay protected without repairs; important authors and cleanup keep latest styles', () => {
  const f = fixture({ density: 2.608695652173913, nativeTop: 136, normalizePixels: true });
  f.body.style.setProperty('padding-top', '4px');
  const fixed = f.element('fixed', '8px'); fixed.style.setProperty('top', '8px');
  const sticky = f.element('sticky', '80px');
  const important = f.element('fixed', '2px'); important.style.setProperty('top', '2px', 'important');
  const stronger = f.element('fixed', '3px'); stronger.authorImportant = { top: true };
  f.start();
  assert.equal(f.computed(fixed).top, '60.1333px');
  assert.equal(f.computed(sticky).top, '132.1333px');
  assert.equal(f.computed(important).top, '2px', 'Inline important is an explicit boundary');
  assert.equal(f.computed(stronger).top, '3px', 'Stronger author important can win');
  fixed.style.setProperty('top', '0px'); sticky.style.setProperty('top', '0px');
  f.body.style.setProperty('padding-top', '0px');
  const before = { reads: { ...f.reads }, writes: f.writes(), rules: f.ruleWrites() };
  f.flush();
  assert.deepEqual({ reads: { ...f.reads }, writes: f.writes(), rules: f.ruleWrites() }, before, 'Passive callback does no style reads or writes');
  assert.equal(f.computed(fixed).top, '60.1333px');
  assert.equal(f.computed(sticky).top, '132.1333px');
  assert.equal(f.computed(f.body).paddingTop, '52.1333px');
  assert.equal(fixed.style.getPropertyValue('top'), '0px');
  sticky.style.setProperty('top', '19px', 'important'); f.flush();
  assert.equal(f.computed(sticky).top, '19px');
  f.configure({ cssSafeAreaTopInsetPx: 48 });
  assert.equal(f.computed(fixed).top, '18.4px', 'Old sheet removed before latest author top is captured');
  assert.equal(f.computed(sticky).top, '19px');
  const pending = f.element('fixed', '0px'); f.event('click'); f.mutate(pending, 'class');
  f.configure({ enabled: false });
  assert.equal(f.computed(fixed).top, '0px');
  assert.equal(f.computed(sticky).top, '19px');
  assert.equal(f.computed(f.body).paddingTop, '0px');
  assert.equal(f.computed(pending).top, '0px');
  assert.equal(f.sheets.filter((sheet) => sheet.isConnected).length, 0);
  assert.equal(f.context.document.documentElement.style.getPropertyValue('--candy-safe-area-inset-top'), '');
  assert.ok([f.body, fixed, sticky, important].every((element) => element.attributes.size === 0));
});

test('readiness, reserved late semantic seed, trusted discovery and nested scroll cancellation stay bounded', () => {
  const ready = fixture(); const root = ready.context.document.documentElement;
  ready.context.document.documentElement = null; ready.context.document.body = null; ready.start();
  ready.context.document.documentElement = root; ready.context.document.body = ready.body;
  const header = ready.element('fixed', '0px'); ready.event('DOMContentLoaded'); ready.flush();
  assert.equal(ready.computed(header).top, '32px');
  const full = fixture(); const roots = Array.from({ length: 16 }, () => full.element('fixed', '0px'));
  full.start(false); full.event('click'); for (const node of roots) full.mutate(node, 'class'); full.flush();
  assert.ok(roots.every((node) => full.computed(node).top === '32px'));
  const streamed = fixture(); streamed.context.document.readyState = 'loading';
  for (let index = 0; index < 600; index++) streamed.element('static', 'auto');
  const nav = streamed.element('sticky', '0px', 'nav'); nav.hidden = true;
  const fallback = streamed.element('sticky', '0px');
  const fallbackHeader = streamed.element('static', 'auto', 'header');
  streamed.body.children.splice(streamed.body.children.indexOf(fallbackHeader), 1); fallback.append(fallbackHeader);
  streamed.start(); assert.equal(streamed.reads.selector, 0);
  streamed.context.document.readyState = 'interactive'; streamed.event('DOMContentLoaded'); streamed.flush();
  assert.equal(streamed.reads.selector, 0);
  fallback.remove(); fallbackHeader.isConnected = false;
  const wrapper = streamed.element('sticky', '0px');
  const semantic = streamed.element('static', 'auto', 'header');
  streamed.body.children.splice(streamed.body.children.indexOf(semantic), 1); wrapper.append(semantic);
  streamed.context.document.readyState = 'complete'; streamed.event('load', 'window'); streamed.flush();
  assert.equal(streamed.computed(wrapper).top, '32px');
  assert.equal(streamed.computed(nav).top, '0px');
  assert.equal(streamed.reads.selector, 1); assert.equal(streamed.reads.rect, 0);
  streamed.event('load', 'window'); streamed.flush(); assert.equal(streamed.reads.selector, 1);
  const f = fixture(); const late = f.element('static', 'auto'); f.start();
  Object.assign(late.computed, { position: 'fixed', top: '0px' });
  f.mutate(late, 'class'); f.flush(); assert.equal(f.computed(late).top, '0px');
  f.event('click'); f.mutate(late, 'class'); f.flush(); assert.equal(f.computed(late).top, '32px');
  const added = f.element('sticky', '8px'); f.added(added); f.flush(); assert.equal(f.computed(added).top, '40px');
  const resized = f.element('fixed', '80px'); f.event('resize'); f.flush(); assert.equal(f.computed(resized).top, '112px');
  f.configure({ recheckOnResize: false }); const noResize = f.element('fixed', '0px');
  f.event('resize'); f.flush(); assert.equal(f.computed(noResize).top, '0px');
  const before = { reads: { ...f.reads }, writes: f.writes(), rules: f.ruleWrites() };
  const capture = f.registrations.find((entry) => entry.target === 'document' && entry.type === 'scroll');
  assert.equal(capture.options.capture, true); assert.equal(capture.options.passive, true);
  for (const target of ['document', 'window']) { f.event('scroll', target); f.flush(); }
  assert.deepEqual({ reads: { ...f.reads }, writes: f.writes(), rules: f.ruleWrites() }, before);
});
