import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import vm from 'node:vm';

const source = readFileSync(new URL('../app/src/gecko/assets/candy_privacy/content_safe_area_prototype.js', import.meta.url), 'utf8');

function fixture({ density = 3, nativeTop = 96, normalizePixels = false, reparseStyles = false, prototypeSource = source, hostname = '' } = {}) {
  let clock = 0; let timerId = 0; let observer;
  const timers = new Map(); const listeners = new Map(); const mutations = []; const registrations = [];
  const reads = { style: 0, rect: 0, selector: 0 }; let writes = 0;
  let ruleWrites = 0;
  const normalize = (value) => normalizePixels && /^[+-]?[\d.]+px$/.test(value) ? `${Number(Number(value.slice(0, -2)).toFixed(4))}px` : value;
  const sheets = [];
  function ruleStyle() {
    const values = new Map();
    return { getPropertyValue: (name) => values.get(name)?.value || '',
      getPropertyPriority: (name) => values.get(name)?.priority || '',
      setProperty: (name, value, priority) => { values.set(name, { value: normalize(value), priority }); ruleWrites++; },
      removeProperty: (name) => { values.delete(name); ruleWrites++; } };
  }
  class Element {
    constructor(position = 'static', top = 'auto', tag = 'div') {
      this.localName = tag; this.parentElement = null; this.children = [];
      this.content = '';
      this.isConnected = true; this.computed = { position, top }; this.properties = new Map();
      this.attributes = new Map();
      if (tag === 'style') {
        this.sheet = { cssRules: [], ownerNode: this, media: { get mediaText() { return this.owner.getAttribute('media') || ''; }, owner: this },
          insertRule: (selector, index) => {
            const style = ruleStyle();
            const top = /top:\s*([^;]+?)\s*!important/.exec(selector)?.[1];
            if (top) style.setProperty('top', top, 'important');
            this.sheet.cssRules.splice(index, 0, { type: 1, selectorText: selector.split('{')[0].trim(), style }); return index;
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
    append(element) {
      const siblings = element.parentElement?.children;
      if (siblings?.includes(element)) siblings.splice(siblings.indexOf(element), 1);
      this.children.push(element); element.parentElement = this; return element;
    }
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
    setAttribute(name, value) { this.attributes.set(name, value); if (name === 'media') this.reparse(); }
    removeAttribute(name) { this.attributes.delete(name); if (name === 'media') this.reparse(); }
    set textContent(value) { this.content = value; this.reparse(); }
    get textContent() { return this.content; }
    reparse() {
      if (!this.sheet || !reparseStyles) return;
      this.sheet.cssRules = [];
      for (const match of this.content.matchAll(/([^{}]+)\{([^{}]+)\}/g)) this.sheet.insertRule(`${match[1]} {${match[2]}}`, this.sheet.cssRules.length);
    }
    matches(selector) {
      return selector.split(',').some((part) => {
        const marker = /\[([^=]+)="([^"]+)"\]/.exec(part);
        if (marker) return this.getAttribute(marker[1]) === marker[2];
        const value = part.trim();
        const id = /#([\w-]+)/.exec(value)?.[1];
        const classes = [...value.matchAll(/\.([\w-]+)/g)].map((match) => match[1]);
        return (!id || id === this.id) && classes.every((name) => (this.classes || []).includes(name)) &&
          (id || classes.length || value === this.localName);
      });
    }
    getBoundingClientRect() { reads.rect++; return { top: 0, left: 0, width: 360, height: 40, right: 360, bottom: 40 }; }
  }
  const root = new Element('static', 'auto', 'html');
  const body = root.append(new Element('static', 'auto', 'body'));
  const config = { ready: true, enabled: true, cssSafeAreaTopInsetPx: nativeTop, navigationGeneration: 1, revision: 1,
    recheckAddedElements: true, recheckChangedElements: true, recheckOnResize: true,
    interactionWindowMillis: 1000, mutationDebounceMillis: 150, maxElementsPerBatch: 16, maxInitialElements: 512 };
  const document = { documentElement: root, body, readyState: 'complete',
    get styleSheets() {
      const pending = [root]; const result = [];
      while (pending.length) {
        const element = pending.shift();
        if (element.isConnected && element.sheet) result.push(element.sheet);
        pending.unshift(...element.children);
      }
      return result;
    },
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
    const accessible = (sheet) => { try { return sheet.cssRules; } catch { return []; } };
    for (const sheet of document.styleSheets) {
      if (sheet.disabled || (sheet.media?.mediaText && sheet.media.mediaText !== 'all')) continue;
      for (const rule of accessible(sheet)) {
        if (rule.type !== 1 || !element.matches(rule.selectorText)) continue;
        for (const name of ['position', 'display']) {
          const value = rule.style.getPropertyValue(name);
          if (value) result[name] = value;
        }
      }
    }
    for (const [name, camel] of [['top', 'top'], ['padding-top', 'paddingTop']]) {
      const inline = element.properties.get(name);
      if (inline) result[camel] = inline.value;
      if (inline?.priority === 'important' || element.authorImportant?.[name]) continue;
      for (const sheet of document.styleSheets) {
        if (sheet.disabled || (sheet.media?.mediaText && sheet.media.mediaText !== 'all')) continue;
        for (const rule of accessible(sheet)) {
          const value = rule.style.getPropertyValue(name);
          if (rule.type === 1 && element.matches(rule.selectorText) && value) result[camel] = value;
        }
      }
    }
    return result;
  }
  const windowProxy = {};
  const context = vm.createContext({ Element, document, self: windowProxy, top: windowProxy,
    location: { hostname },
    devicePixelRatio: density,
    CandyContentTopInset: { cssSafeAreaConfiguration: () => ({ ...config }), domDiagnosticsEnabled: () => true },
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
    for (let steps = 0; steps < 12000; steps++) {
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
    sheet(definitions, options = {}) {
      const node = body.append(new Element('static', 'auto', 'style'));
      Object.assign(node.sheet, options);
      for (const definition of definitions) {
        const style = ruleStyle();
        for (const [name, value] of Object.entries(definition.declarations || {})) style.setProperty(name, value, definition.important ? 'important' : '');
        node.sheet.cssRules.push({ type: definition.type ?? 1, selectorText: definition.selector || 'div', style,
          ...(definition.nested ? { cssRules: [{}] } : {}) });
      }
      return node;
    },
    start(drain = true) { vm.runInContext(prototypeSource, context); if (drain) flush(); },
    configure(next) { Object.assign(config, next); context.__candyConfigureCssSafeArea(); flush(); },
    event(type, target = ['scroll', 'resize'].includes(type) ? 'window' : 'document', node) {
      const listener = listeners.get(`${target}:${type}`);
      assert.equal(typeof listener, 'function', `Actual ${target} ${type} listener must exist`);
      listener({ type, isTrusted: true, target: node });
    },
    mutate(element, attributeName) { observer.callback([{ type: 'attributes', target: element, attributeName }]); },
    added(element) { observer.callback([{ type: 'childList', target: body, addedNodes: [element], removedNodes: [] }]); },
    textChanged(element) { observer.callback([{ type: 'characterData', target: { parentElement: element } }]); },
    removed(element) { element.remove(); observer.callback([{ type: 'childList', target: body, addedNodes: [], removedNodes: [element] }]); },
    step() {
      if (mutations.length && observer?.connected) { observer.callback(mutations.splice(0)); return; }
      const [id, timer] = [...timers].sort((a, b) => a[1].at - b[1].at)[0] || [];
      if (timer) { timers.delete(id); clock = Math.max(clock, timer.at); timer.callback(); }
    },
    now: () => clock,
    diagnostics: () => context.CandyCssSafeAreaDiagnostics.sample(),
  };
}

test('known Google menu and focus CSS are seeded before activation and removed when disabled', () => {
  for (const hostname of ['www.google.com', 'google.de', 'www.google.de.']) {
    const f = fixture({ hostname, reparseStyles: true });
    const navd = f.element('absolute', '0px'); navd.id = 'navd';
    f.start(false);
    const layer = f.sheets.find((node) => node.textContent.includes(':root #navd'));
    assert.ok(layer?.isConnected, 'Google menu rule must exist before delayed classification');
    assert.match(layer.textContent, /:root #navd/);
    assert.match(layer.textContent, /:root #tsf \.A7Yvie\.emcav/);
    assert.match(layer.textContent, /top: calc\(0px \+ var\(--candy-safe-area-inset-top\)\) !important/);
    assert.equal(
      f.computed(navd).top,
      'calc(0px + var(--candy-safe-area-inset-top))',
      'Absolute Google menu must receive the early host-scoped inset rule',
    );
    const before = { ...f.reads };
    f.event('scroll');
    assert.deepEqual(f.reads, before, 'Scrolling must not add style or geometry reads');
    f.configure({ enabled: false });
    assert.equal(layer.isConnected, false);
  }
  for (const hostname of ['google.com.example.org', 'notgoogle.de', 'example.org']) {
    const f = fixture({ hostname });
    f.start(false);
    assert.equal(f.sheets.some((node) => node.textContent.includes('#navd')), false);
    assert.equal(f.sheets.some((node) => node.textContent.includes('.A7Yvie.emcav')), false);
  }
});

test('Reddit app ownership replaces body inset without losing author padding', () => {
  const f = fixture();
  f.body.computed.paddingTop = '4px';
  let flow = false; let changed;
  f.context.CandyRedditSafeArea = {
    owns: () => false, ownsSource: () => false, added: () => {}, sync: () => {},
    flowProtected: () => flow,
    configure: (active, callback) => { changed = callback; flow = active; callback(); },
  };
  f.start();
  assert.equal(f.computed(f.body).paddingTop, '4px', 'Do not add both body and Reddit container insets');
  flow = false; changed(); f.flush();
  assert.equal(f.computed(f.body).paddingTop, '32px', 'Restore general body inset when Reddit flow disappears');
  flow = true; changed(); f.flush();
  assert.equal(f.computed(f.body).paddingTop, '4px', 'Never measure retained Candy padding as author padding');
  const before = { ...f.reads };
  f.event('scroll');
  assert.deepEqual(f.reads, before);
});

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
  fallback.remove(); fallbackHeader.isConnected = false;
  const wrapper = streamed.element('sticky', '0px');
  const semantic = streamed.element('static', 'auto', 'header');
  streamed.body.children.splice(streamed.body.children.indexOf(semantic), 1); wrapper.append(semantic);
  streamed.context.document.readyState = 'interactive'; streamed.event('DOMContentLoaded'); streamed.flush();
  assert.equal(streamed.reads.selector, 1, 'Semantic discovery starts after parsing, not final subresource load');
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

test('full selector rules protect passive class activation and new matching nodes without double ownership', () => {
  const f = fixture();
  f.sheet([{ selector: '#latent.persistent-header', declarations: { position: 'fixed', top: '8px' }, important: true },
    { selector: '#new-latent.persistent-header', declarations: { position: 'sticky', top: '0px' }, important: true }]);
  const latent = f.element('static', 'auto'); latent.id = 'latent';
  const ordinary = f.element('fixed', '80px');
  f.start();
  const before = { reads: { ...f.reads }, writes: f.writes(), rules: f.ruleWrites() };
  latent.classes = ['persistent-header'];
  const added = f.element('static', 'auto'); added.id = 'new-latent'; added.classes = ['persistent-header'];
  f.event('scroll'); f.mutate(latent, 'class'); f.added(added); f.flush();
  assert.deepEqual({ reads: { ...f.reads }, writes: f.writes(), rules: f.ruleWrites() }, before, 'Passive activation and scroll do no repair');
  assert.equal(f.computed(latent).top, '40px'); assert.equal(f.computed(added).top, '32px');
  assert.equal(latent.style.getPropertyValue('top'), '');
  assert.equal(f.computed(ordinary).top, '112px', 'Unmatched DOM protection remains');
  for (let repeat = 0; repeat < 3; repeat++) { f.event('resize'); f.flush(); }
  assert.equal(f.computed(latent).top, '40px'); assert.equal(f.computed(added).top, '32px');
  f.configure({ cssSafeAreaTopInsetPx: 48 }); assert.equal(f.computed(latent).top, '24px');
  f.configure({ enabled: false }); assert.equal(f.computed(latent).top, '8px'); assert.equal(f.computed(added).top, '0px');

  const early = fixture(); early.context.document.readyState = 'loading';
  early.sheet([{ selector: '#existing.sticky', declarations: { position: 'sticky', top: '8px' } }]);
  const existing = early.element('static', 'auto'); existing.id = 'existing'; existing.classes = ['sticky'];
  early.start(); assert.equal(early.computed(existing).top, '8px', 'Parser-time bootstrap protects body without traversing partial DOM');
  early.context.document.readyState = 'interactive'; early.event('DOMContentLoaded'); early.flush();
  assert.equal(early.computed(existing).top, '40px');
  early.context.document.readyState = 'complete'; early.event('load', 'window'); early.flush();
  assert.equal(early.computed(existing).top, '40px', 'Load scan replaces earlier DOM ownership, not 72px');
  assert.equal(existing.attributes.size, 0, 'CSS ownership releases any obsolete initial DOM top marker');
  assert.equal(early.computed(early.body).paddingTop, '32px');

  const priority = fixture();
  priority.sheet([{ selector: '#priority.fixed', declarations: { position: 'fixed', top: '100px' }, important: true },
    { selector: '#priority.fixed', declarations: { position: 'fixed', top: '0px' } },
    { selector: '#later.fixed', declarations: { position: 'fixed', top: '8px' } },
    { selector: '#later.fixed', declarations: { position: 'fixed', top: '20px' }, important: true },
    { selector: '#same.fixed', declarations: { position: 'fixed', top: '8px' }, important: true },
    { selector: '#same.fixed', declarations: { position: 'fixed', top: '20px' }, important: true }]);
  const prior = priority.element('static', 'auto'); prior.id = 'priority'; prior.classes = ['fixed'];
  const later = priority.element('static', 'auto'); later.id = 'later'; later.classes = ['fixed'];
  const same = priority.element('static', 'auto'); same.id = 'same'; same.classes = ['fixed'];
  priority.start();
  assert.equal(priority.computed(prior).top, '132px', 'Later normal duplicate cannot replace earlier source important');
  assert.equal(priority.computed(later).top, '52px', 'Later source important wins');
  assert.equal(priority.computed(same).top, '52px', 'Same-priority duplicates preserve source order');
});

test('selector scan skips unsupported and inaccessible contexts, consumes caps and never restarts on scroll', () => {
  const f = fixture();
  const declarations = { position: 'fixed', top: '0px' };
  f.sheet([{ selector: '#valid.future', declarations, important: true }]);
  f.sheet([{ selector: '#disabled.future', declarations }], { disabled: true });
  f.sheet([{ selector: '#media.future', declarations }], { media: { mediaText: '(min-width: 600px)' } });
  f.sheet([{ type: 4, selector: '#group.future', declarations },
    { type: 3, selector: '#import.future', declarations },
    { selector: '#nested.future', declarations, nested: true },
    { selector: '#auto.future', declarations: { position: 'fixed', top: 'auto' } },
    { selector: '#percent.future', declarations: { position: 'fixed', top: '10%' } }]);
  const inaccessible = f.sheet([]);
  Object.defineProperty(inaccessible.sheet, 'cssRules', { get() { throw Object.assign(new Error('Blocked'), { name: 'SecurityError' }); } });
  const nodes = ['valid', 'disabled', 'media', 'group', 'import', 'nested', 'auto', 'percent'].map((id) => {
    const element = f.element('static', 'auto'); element.id = id; return element;
  });
  f.start();
  for (const node of nodes) node.classes = ['future'];
  const own = f.sheets.find((node) => node !== inaccessible && node.sheet.cssRules.some((rule) => rule.selectorText === '#valid.future' && rule.style.getPropertyPriority('top') === 'important') && node.parentElement?.localName === 'html');
  assert.equal(f.computed(nodes[0]).top, '32px');
  assert.ok(!own.sheet.cssRules.some((rule) => /#(?:disabled|media|group|import|nested|auto|percent)/.test(rule.selectorText)));
  const count = own.sheet.cssRules.length;
  f.event('load', 'window'); f.event('scroll'); f.flush(); assert.equal(own.sheet.cssRules.length, count);

  const capped = fixture(); capped.config.maxInitialElements = 64;
  capped.sheet([...Array.from({ length: 4100 }, () => ({ type: 7 })), { selector: '#late.future', declarations }]);
  const late = capped.element('static', 'auto'); late.id = 'late'; capped.start(); late.classes = ['future'];
  assert.equal(capped.computed(late).top, '0px', 'Unsupported rules consume the independent per-source cap');
  capped.event('scroll'); capped.flush(); assert.equal(capped.computed(late).top, '0px', 'No scroll continuation after partial scan');

  const reserved = fixture();
  reserved.config.maxInitialElements = 64;
  reserved.sheet(Array.from({ length: 512 }, () => ({ selector: '#reserved.future', declarations })));
  reserved.start();
  assert.equal(reserved.computed(reserved.body).paddingTop, '32px', 'Selector duplicates cannot exhaust the body protection slot');
  const reservedLayers = reserved.sheets.filter((node) => node.isConnected && node.parentElement?.localName === 'html');
  assert.equal(reservedLayers.flatMap((node) => node.sheet.cssRules).filter((rule) => rule.selectorText === '#reserved.future').length, 1);
  assert.equal(reservedLayers.flatMap((node) => node.sheet.cssRules).length, 2, 'Duplicate source rules use one clone and retain body protection');
});

test('late CSS sources and revisions are protected outside click gate with one cooldown worker', () => {
  const f = fixture(); f.start();
  const before = { ...f.reads }; const began = f.now();
  const style = f.sheet([{ selector: '#late.fixed', declarations: { position: 'fixed', top: '8px' }, important: true }]);
  const late = f.element('static', 'auto'); late.id = 'late'; late.classes = ['fixed'];
  f.added(style); f.flush();
  assert.equal(f.now() - began, 500); assert.equal(f.computed(late).top, '40px');
  assert.deepEqual(f.reads, before, 'CSS source events do not read computed style or geometry');
  style.sheet.cssRules[0].style.setProperty('top', '24px', 'important');
  const updateAt = f.now();
  for (let repeat = 0; repeat < 100; repeat++) f.textChanged(style);
  f.flush(); assert.equal(f.now() - updateAt, 500);
  assert.equal(f.computed(late).top, '56px', 'Revision replaces original top, not an inset-adjusted value');
  assert.equal(f.diagnostics().cssRulesApplied, 1);

  const link = f.sheet([{ selector: '#link.sticky', declarations: { position: 'sticky', top: '20px' } }]);
  link.localName = 'link';
  link.setAttribute('rel', 'stylesheet');
  const linked = f.element('static', 'auto'); linked.id = 'link'; linked.classes = ['sticky'];
  f.added(link); f.event('load', 'document', link); f.flush(); assert.equal(f.computed(linked).top, '52px');
  link.sheet.disabled = true; f.mutate(link, 'disabled'); f.flush();
  assert.equal(f.computed(linked).top, 'auto', 'Disabled sheet drops its stale protection');
  link.sheet.disabled = false; f.mutate(link, 'disabled'); f.flush(); assert.equal(f.computed(linked).top, '52px');
  link.setAttribute('rel', 'preload'); f.mutate(link, 'rel'); f.flush(); assert.equal(f.computed(linked).top, '20px');
  link.setAttribute('rel', 'stylesheet'); f.mutate(link, 'rel'); f.flush(); assert.equal(f.computed(linked).top, '52px');
  link.setAttribute('media', '(min-width: 900px)'); f.mutate(link, 'media'); f.flush(); assert.equal(f.computed(linked).top, 'auto');
  link.removeAttribute('media'); f.mutate(link, 'media'); f.flush(); assert.equal(f.computed(linked).top, '52px');
  const linkedSheet = link.sheet;
  link.setAttribute('rel', 'preload'); link.sheet = null; f.mutate(link, 'rel'); f.flush();
  assert.equal(f.diagnostics().cssRulesApplied, 1, 'Connected link with no active stylesheet drops stale clone');
  link.setAttribute('rel', 'stylesheet'); link.sheet = linkedSheet; f.event('load', 'document', link); f.flush();
  assert.equal(f.computed(linked).top, '52px');
  const own = f.sheets.filter((node) => node.isConnected && node.parentElement?.localName === 'html');
  const visited = f.diagnostics().cssRulesVisited;
  for (const node of own) { f.added(node); f.textChanged(node); f.mutate(node, 'media'); }
  f.flush(); assert.equal(f.diagnostics().cssRulesVisited, visited, 'Own CSS sources never loop');
  f.removed(style); f.flush(); assert.equal(f.computed(late).top, 'auto');
  f.configure({ cssSafeAreaTopInsetPx: 48 }); assert.equal(f.computed(linked).top, '36px');
  f.configure({ enabled: false }); assert.equal(f.computed(linked).top, '20px');
  assert.equal(f.diagnostics().active, false); assert.equal(f.diagnostics().cssRulesApplied, 0);
});

test('source order, late own-sheet placement and cancelled staging preserve complete protection', () => {
  const f = fixture();
  f.config.maxElementsPerBatch = 4;
  const first = f.sheet([{ selector: '#same.fixed', declarations: { position: 'fixed', top: '8px' }, important: true }]);
  f.sheet([{ selector: '#same.fixed', declarations: { position: 'fixed', top: '20px' }, important: true }]);
  const node = f.element('static', 'auto'); node.id = 'same'; node.classes = ['fixed']; f.start();
  first.sheet.cssRules[0].style.setProperty('top', '24px', 'important'); f.textChanged(first); f.flush();
  assert.equal(f.computed(node).top, '52px', 'Updating earlier sheet does not change its order');
  const late = f.sheet([{ selector: '#same.fixed', declarations: { position: 'fixed', top: '30px' }, important: true }]);
  f.body.children.splice(f.body.children.indexOf(late), 1); f.context.document.documentElement.append(late);
  f.added(late); f.flush(); assert.equal(f.computed(node).top, '62px', 'New protection sheet commits after late author stylesheet');
  late.sheet.cssRules[0].style.setProperty('top', '100px', 'important'); f.textChanged(late);
  f.step(); f.step(); // Advance cooldown and candidate collection, but not commit.
  f.event('scroll'); f.flush();
  assert.equal(f.computed(node).top, '62px', 'Scroll cancellation leaves the last complete protection intact');
  assert.equal(f.diagnostics().cssScrollCancellations, 1);
  const visited = f.diagnostics().cssRulesVisited;
  for (let repeat = 0; repeat < 100; repeat++) { f.event('scroll'); f.flush(); }
  assert.equal(f.diagnostics().cssRulesVisited, visited, 'No scroll-stop or scroll-driven source resumption');
});

test('independent progressive source budgets reach past sixteen sheets and report inaccessible sources', () => {
  const f = fixture(); f.config.maxInitialElements = 64;
  for (let index = 0; index < 20; index++) f.sheet([{ type: 7 }]);
  f.sheet([...Array.from({ length: 600 }, () => ({ type: 7 })),
    { selector: '#beyond.fixed', declarations: { position: 'fixed', top: '8px' } }]);
  const node = f.element('static', 'auto'); node.id = 'beyond'; f.start(); node.classes = ['fixed'];
  assert.equal(f.computed(node).top, '40px', 'CSS discovery is independent of initial DOM cap');
  const inaccessible = f.sheet([]);
  Object.defineProperty(inaccessible.sheet, 'cssRules', { get() { throw Object.assign(new Error('Blocked'), { name: 'SecurityError' }); } });
  f.added(inaccessible); f.flush(); assert.equal(f.diagnostics().cssSecurityErrors, 1);
  assert.ok(f.diagnostics().cssUnsupportedRules >= 620);
  assert.equal(f.reads.rect, 0);
});

test('replaced sheet identities retain last protection through cooldown and committed reconciliation cancellation', () => {
  const f = fixture(); f.config.maxElementsPerBatch = 4;
  const style = f.sheet([{ selector: '#replacement.fixed', declarations: { position: 'fixed', top: '8px' }, important: true }]);
  const node = f.element('static', 'auto'); node.id = 'replacement'; node.classes = ['fixed'];
  for (let index = 0; index < 50; index++) f.element('fixed', '80px');
  f.start();
  const temporary = f.sheet([{ selector: '#replacement.fixed', declarations: { position: 'fixed', top: '24px' }, important: true }]);
  temporary.remove(); style.sheet = temporary.sheet; style.sheet.ownerNode = style;
  f.textChanged(style);
  assert.equal(f.computed(node).top, '40px', 'Identity invalidation does not immediately delete old protection');
  while (f.diagnostics().cssRulesVisited < 2) f.step();
  while (f.computed(node).top !== '56px') f.step();
  // Commit occurred, but fifty retained DOM protections still require bounded reconciliation.
  f.event('scroll'); f.flush(); assert.equal(f.computed(node).top, '56px', 'Cancellation after commit retains active selector sheet');
  f.textChanged(style); f.flush(); assert.equal(f.computed(node).top, '56px');
});

test('selector and navigation-lifetime source caps remain bounded and observable', () => {
  const f = fixture(); f.config.maxInitialElements = 64;
  f.sheet(Array.from({ length: 300 }, (_, index) => ({ selector: `#cap${index}.fixed`, declarations: { position: 'fixed', top: '0px' } })));
  f.start(); assert.equal(f.diagnostics().cssRulesApplied, 63); assert.ok(f.diagnostics().cssBudgetHits > 0);
  assert.equal(f.computed(f.body).paddingTop, '32px');
  const capped = fixture();
  const style = capped.sheet([{ selector: '#event.fixed', declarations: { position: 'fixed', top: '0px' } }]);
  capped.start();
  for (let index = 0; index < 4200; index++) capped.textChanged(style);
  capped.flush(); assert.ok(capped.diagnostics().cssBudgetHits > 0);
  const visited = capped.diagnostics().cssRulesVisited;
  for (let index = 0; index < 100; index++) capped.textChanged(style);
  capped.flush(); assert.equal(capped.diagnostics().cssRulesVisited, visited, 'Lifetime event cap does not grant a fresh source budget');
});

test('Gecko media attribute reparsing retains staged CSS rules instead of empty STYLE text', () => {
  const broken = fixture({ reparseStyles: true, prototypeSource: source.replace(/^\s*build\.staging\.textContent = .*$/m, '') });
  broken.sheet([{ selector: '#gecko.fixed', declarations: { position: 'fixed', top: '8px' }, important: true }]);
  const uncovered = broken.element('static', 'auto'); uncovered.id = 'gecko'; uncovered.classes = ['fixed']; broken.start();
  assert.equal(broken.computed(uncovered).top, '8px', 'Former CSSOM-only activation loses cloned rules on media reparse');
  const f = fixture({ reparseStyles: true });
  f.sheet([{ selector: '#gecko.fixed', declarations: { position: 'fixed', top: '8px' }, important: true }]);
  const node = f.element('static', 'auto'); node.id = 'gecko'; node.classes = ['fixed'];
  f.start();
  assert.equal(f.computed(node).top, '40px', 'Media activation reparses canonical staged CSS, not empty text');
  const own = f.sheets.find((sheet) => sheet.isConnected && sheet.parentElement?.localName === 'html' && sheet.textContent);
  assert.match(own.textContent, /#gecko\.fixed \{ top: 40px !important; \}/);
  assert.equal(own.sheet.cssRules.length, 1);
  f.event('scroll'); f.flush(); assert.equal(f.computed(node).top, '40px');
});

test('parser and interactive CSS sources are immediate while genuinely late sources retain cooldown', () => {
  const f = fixture(); f.context.document.readyState = 'loading'; f.start();
  const parser = f.sheet([{ selector: '#parser.fixed', declarations: { position: 'fixed', top: '8px' } }]);
  const node = f.element('static', 'auto'); node.id = 'parser'; node.classes = ['fixed'];
  f.added(parser); f.flush(); assert.equal(f.now(), 0); assert.equal(f.computed(node).top, '40px');
  f.context.document.readyState = 'interactive'; f.event('DOMContentLoaded'); f.flush();
  const initialLink = f.sheet([{ selector: '#initial-link.fixed', declarations: { position: 'fixed', top: '20px' } }]);
  initialLink.localName = 'link'; initialLink.setAttribute('rel', 'stylesheet');
  f.added(initialLink); f.event('load', 'document', initialLink); f.flush(); assert.equal(f.now(), 0);
  f.context.document.readyState = 'complete'; f.event('load', 'window'); f.flush();
  const late = f.sheet([{ selector: '#late-load.fixed', declarations: { position: 'fixed', top: '24px' } }]);
  f.added(late); f.flush(); assert.equal(f.now(), 500);

  const snapshot = fixture(); snapshot.start(false);
  const queued = snapshot.sheet([{ selector: '#snapshot.fixed', declarations: { position: 'fixed', top: '8px' } }]);
  snapshot.added(queued); snapshot.flush();
  assert.equal(snapshot.now(), 0, 'Initial snapshot shortens an already pending source deadline');
});

test('DOM header discovery interleaves with large progressive CSS sources before final load', () => {
  const f = fixture(); f.config.maxElementsPerBatch = 4; f.context.document.readyState = 'interactive';
  f.sheet(Array.from({ length: 4096 }, () => ({ type: 7 })));
  const header = f.element('sticky', '8px', 'header');
  f.start(false); f.step();
  assert.equal(f.computed(header).top, '40px', 'Bounded header job does not wait behind thousands of source rules');
  assert.ok(f.diagnostics().cssRulesVisited < 4096);
  f.flush(); assert.equal(f.context.document.readyState, 'interactive'); assert.equal(f.computed(header).top, '40px');
});

test('body bootstrap runs synchronously on arrival and preserves parser author padding at DOM readiness', () => {
  const f = fixture(); f.context.document.readyState = 'loading'; f.context.document.body = null;
  f.start(false);
  assert.equal(f.context.document.documentElement.style.getPropertyValue('--candy-safe-area-inset-top'), '32px');
  f.context.document.body = f.body; f.added(f.body);
  assert.equal(f.computed(f.body).paddingTop, '32px', 'Arrival protection does not wait for a timer or final load');
  f.body.computed.paddingTop = '100px';
  f.context.document.readyState = 'interactive'; f.event('DOMContentLoaded');
  assert.equal(f.computed(f.body).paddingTop, '100px', 'Own early padding is removed/read/replaced within readiness task');
  f.flush(); const before = { ...f.reads };
  f.event('load', 'window'); f.flush(); assert.deepEqual(f.reads, before, 'Author padding recapture happens only once');
  f.body.style.setProperty('padding-top', '120px'); f.flush(); f.configure({ enabled: false });
  assert.equal(f.computed(f.body).paddingTop, '120px', 'Disable exposes latest author inline padding');
  const existing = fixture(); existing.body.computed.paddingTop = '100px'; existing.start(false);
  assert.equal(existing.computed(existing.body).paddingTop, '100px', 'First configure protects an existing body synchronously');
});
