import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import vm from 'node:vm';

const source = readFileSync(new URL(
  '../app/src/gecko/assets/candy_privacy/content_safe_area.js', import.meta.url,
), 'utf8');

const protectedTop = 'max(0px, env(safe-area-inset-top, 0px))';

function fixture(overrides = {}) {
  let clock = 0;
  let nextTimer = 0;
  const timers = new Map();
  const listeners = new Map();
  const observers = [];
  const reads = { style: 0, rect: 0, points: 0 };
  const fallback = [];
  const timing = [];
  const config = {
    ready: true, enabled: true, cssSafeAreaTopInsetPx: 96, navigationGeneration: 7, revision: 11,
    recheckAddedElements: true, recheckChangedElements: true, requireInteractionForUpdates: true,
    recheckOnResize: true, interactionWindowMillis: 1000, mutationDebounceMillis: 150,
    maxElementsPerBatch: 16, maxBatchDurationMillis: 4, maxInitialElements: 512,
    safeAreaLayoutQuietPeriodMillis: 400, safeAreaRequiredFailureCount: 2, ...overrides,
  };
  class Element {
    constructor(tag = 'div', computed = {}) {
      this.tag = tag;
      this.computed = computed;
      this.children = [];
      this.parentElement = null;
      this.isConnected = true;
      this.attributes = new Map();
      this.properties = new Map();
      this.rectReads = 0;
      this.rect = { top: 0, bottom: 64, left: 0, right: 360, width: 360, height: 64 };
      this.style = {
        getPropertyValue: (name) => this.properties.get(name)?.value || '',
        getPropertyPriority: (name) => this.properties.get(name)?.priority || '',
        setProperty: (name, value, priority = '') => this.properties.set(name, { value, priority }),
        removeProperty: (name) => this.properties.delete(name),
      };
    }
    get firstElementChild() { return this.children[0] || null; }
    get nextElementSibling() {
      const siblings = this.parentElement?.children || [];
      return siblings[siblings.indexOf(this) + 1] || null;
    }
    append(child) { this.children.push(child); child.parentElement = this; return child; }
    appendChild(child) { return this.append(child); }
    setAttribute(name, value) { this.attributes.set(name, value); }
    remove() {
      const siblings = this.parentElement?.children;
      if (siblings) siblings.splice(siblings.indexOf(this), 1);
      this.isConnected = false;
    }
    getAttribute(name) {
      return name === 'style' ? [...this.properties].map(([key, entry]) =>
        `${key}: ${entry.value}${entry.priority ? ' !' + entry.priority : ''};`).join(' ') : this.attributes.get(name) || null;
    }
    contains(element) {
      for (let current = element; current; current = current.parentElement) if (current === this) return true;
      return false;
    }
    matches(selector) { return selector.split(',').includes(this.tag); }
    getRootNode() { return document; }
    getBoundingClientRect() { reads.rect++; this.rectReads++; return { ...this.rect }; }
  }
  const root = new Element('html');
  const body = root.append(new Element('body'));
  const document = {
    documentElement: root, body, hits: [],
    createElement: (tag) => new Element(tag),
    addEventListener(type, callback) { listeners.set(type, callback); },
    querySelector(selector) {
      const stack = [body];
      while (stack.length) { const element = stack.shift(); if (element.tag === selector) return element; stack.push(...element.children); }
      return null;
    },
    elementsFromPoint() { reads.points++; return this.hits; },
  };
  const context = vm.createContext({
    Element, document, devicePixelRatio: 3, innerWidth: 360, innerHeight: 640,
    CandyContentTopInset: {
      cssSafeAreaConfiguration: () => ({ ...config }),
      fallbackToNative: (...args) => fallback.push(args),
      performanceDiagnosticsEnabled: () => config.diagnostics === true,
    },
    getComputedStyle(element) {
      reads.style++; clock += config.styleCost || 0;
      return { position: 'static', top: 'auto', paddingTop: '0px', display: 'block',
        visibility: 'visible', opacity: '1', overflowY: 'visible', transform: 'none', ...element.computed,
        ...(element.attributes.has('data-candy-css-safe-area-probe') ? { paddingTop: `${config.environmentTop ?? 32}px` } : {}),
      };
    },
    performance: {
      now: () => clock,
      mark: (name) => timing.push(['mark', name]), measure: (name) => timing.push(['measure', name]),
      clearMarks: (name) => timing.push(['clearMark', name]), clearMeasures: (name) => timing.push(['clearMeasure', name]),
    },
    setTimeout(callback, delay = 0) { const id = ++nextTimer; timers.set(id, { callback, at: clock + delay }); return id; },
    clearTimeout(id) { timers.delete(id); },
    addEventListener(type, callback) { listeners.set('window:' + type, callback); },
    MutationObserver: class {
      constructor(callback) { this.callback = callback; this.roots = []; observers.push(this); }
      observe(node) { this.roots.push(node); }
      disconnect() { this.roots = []; }
    },
  });
  const start = () => vm.runInContext(source, context);
  const step = () => {
    if (!timers.size) return false;
    const [id, timer] = [...timers].sort((a, b) => a[1].at - b[1].at || a[0] - b[0])[0];
    timers.delete(id); clock = Math.max(clock, timer.at); timer.callback(); return true;
  };
  const flush = (maximum = 1000) => { let count = 0; while (step()) assert.ok(++count <= maximum, 'Work must terminate'); return count; };
  return {
    config, reads, body, root, document, context, observers, fallback, timing, timers, start, step, flush,
    element: (tag, computed) => new Element(tag, computed),
    event(type, extra = {}) { listeners.get(type)?.({ type, isTrusted: true, ...extra }); },
    mutations(records) { observers.at(-1)?.callback(records); },
    advance(milliseconds) { clock += milliseconds; },
  };
}

test('disabled or unready configuration is inert, including diagnostics and geometry', () => {
  for (const overrides of [{ enabled: false }, { ready: false }, { cssSafeAreaTopInsetPx: 0 }]) {
    const f = fixture(overrides); f.start(); f.flush(); f.event('scroll'); f.event('click');
    assert.deepEqual(f.reads, { style: 0, rect: 0, points: 0 });
    assert.equal(f.timing.length, 0); assert.equal(f.observers.length, 0);
  }
});

test('body flow and initially offscreen declared sticky use CSS max with authored styling', () => {
  const f = fixture();
  f.body.computed.paddingTop = '8px';
  f.body.append(f.element('main'));
  const sticky = f.body.append(f.element('header', { position: 'sticky', top: '0px' }));
  sticky.rect.top = 400; sticky.rect.bottom = 464;
  f.start(); f.flush();
  assert.equal(f.body.style.getPropertyValue('padding-top'), 'max(8px, env(safe-area-inset-top, 0px))');
  assert.equal(sticky.style.getPropertyValue('top'), 'max(0px, env(safe-area-inset-top, 0px))');
  assert.equal(sticky.rectReads, 0, 'Sticky registration must not depend on initial y');
  assert.equal(f.fallback.length, 0);
});

test('disable restores author priority but does not clobber a newer inline override', () => {
  const f = fixture();
  const header = f.body.append(f.element('header', { position: 'fixed', top: '12px' }));
  header.style.setProperty('top', '12px', 'important');
  f.start(); f.flush();
  f.config.enabled = false; f.context.__candyConfigureCssSafeArea(); f.flush();
  assert.equal(header.style.getPropertyValue('top'), '12px');
  assert.equal(header.style.getPropertyPriority('top'), 'important');
  f.config.enabled = true; f.context.__candyConfigureCssSafeArea(); f.flush();
  header.style.setProperty('top', '90px');
  f.config.enabled = false; f.context.__candyConfigureCssSafeArea(); f.flush();
  assert.equal(header.style.getPropertyValue('top'), '90px');
});

test('trusted click permits menu class updates; untrusted input and scroll keys do not', () => {
  const f = fixture(); const menu = f.body.append(f.element('div', { display: 'none', position: 'fixed', top: '0px' }));
  f.start(); f.flush(); menu.computed.display = 'block';
  const record = { type: 'attributes', attributeName: 'class', target: menu };
  f.event('input', { isTrusted: false }); f.mutations([record]); f.flush();
  f.event('keydown', { key: 'PageDown' }); f.mutations([record]); f.flush();
  assert.equal(menu.style.getPropertyValue('top'), '');
  f.event('click'); f.mutations([record]); f.flush();
  assert.equal(menu.style.getPropertyValue('top'), 'max(0px, env(safe-area-inset-top, 0px))');
});

test('scroll alone reads no geometry and cancels an unstarted initial traversal', () => {
  const f = fixture(); f.start(); const before = { ...f.reads };
  f.event('scroll'); f.flush();
  assert.deepEqual(f.reads, before);
  assert.equal(f.body.style.getPropertyValue('padding-top'), '');
});

test('authorized dirty menu restarts fresh after scroll while scroll clears further authorization', () => {
  const f = fixture(); const menu = f.body.append(f.element('div', { display: 'none', position: 'fixed', top: '0px' }));
  f.start(); f.flush(); menu.computed.display = 'block'; f.event('click');
  f.mutations([{ type: 'attributes', attributeName: 'hidden', target: menu }]);
  const before = { ...f.reads }; f.event('wheel'); assert.deepEqual(f.reads, before); f.flush();
  assert.equal(menu.style.getPropertyValue('top'), 'max(0px, env(safe-area-inset-top, 0px))');
  const added = f.body.append(f.element('div', { position: 'fixed', top: '0px' }));
  f.mutations([{ type: 'childList', target: f.body, addedNodes: [added] }]); f.flush();
  assert.equal(added.style.getPropertyValue('top'), '');
});

test('class change exposes new authored position and own write batches never requeue', () => {
  const f = fixture(); const header = f.body.append(f.element('header', { position: 'sticky', top: '0px' }));
  f.start(); f.flush(); const ownStyle = header.getAttribute('style'); f.event('click');
  f.mutations([{ type: 'attributes', attributeName: 'style', target: header, oldValue: '' }]);
  assert.equal(f.timers.size, 0);
  header.computed.position = 'static'; f.mutations([{ type: 'attributes', attributeName: 'class', target: header }]); f.flush();
  assert.equal(header.style.getPropertyValue('top'), '');
  assert.ok(ownStyle.includes('env(safe-area-inset-top'));
});

test('initial traversal and per-task work remain bounded and cooperative', () => {
  const f = fixture({ maxInitialElements: 16, maxElementsPerBatch: 2, styleCost: 4 });
  for (let index = 0; index < 100; index++) f.body.append(f.element('div'));
  f.start(); const before = f.reads.style; f.step();
  assert.ok(f.reads.style - before <= 16, 'One atomic body node may include a bounded flow/ancestor check');
  assert.ok(f.timers.size > 0); f.flush();
  assert.ok(f.reads.style <= 69, 'Shared initial cap must not scan all 100 children');
});

test('nested/moving/tall unknown layouts fallback only after actual confirmed overlap', () => {
  const f = fixture(); const scroller = f.body.append(f.element('div', { overflowY: 'auto' }));
  const sticky = scroller.append(f.element('header', { position: 'sticky', top: '0px' }));
  f.document.hits = [sticky]; f.start();
  while (!f.timers.size || f.fallback.length === 0) { if (!f.step()) break; }
  assert.deepEqual(f.fallback, [[7, 11]]);
  assert.equal(sticky.style.getPropertyValue('top'), '');
  const safe = fixture(); const transformed = safe.body.append(safe.element('div', { transform: 'translateY(10px)' }));
  transformed.append(safe.element('header', { position: 'fixed', top: '0px' }));
  safe.start(); safe.flush(); assert.equal(safe.fallback.length, 0, 'No visible hit means no verified overlap');
});

test('policy generation replaces pending work and reconnects observer to the new policy', () => {
  const f = fixture(); const header = f.body.append(f.element('header', { position: 'sticky', top: '0px' }));
  f.start(); f.config.navigationGeneration = 8; f.config.revision = 12;
  f.context.__candyConfigureCssSafeArea(); f.flush();
  assert.equal(header.style.getPropertyValue('top'), 'max(0px, env(safe-area-inset-top, 0px))');
  assert.equal(f.observers.at(-1).roots.length, 1);
  assert.equal(f.fallback.length, 0);
});

test('opt-in aggregate phases clear their own entries and remain absent by default', () => {
  const f = fixture({ diagnostics: true }); f.start(); f.flush();
  const measures = f.timing.filter(([type]) => type === 'measure');
  assert.ok(measures.length > 0);
  for (const [, name] of measures) {
    assert.match(name, /^Candy\.SafeArea\.Css\./);
    assert.ok(f.timing.some(([type, value]) => type === 'clearMeasure' && value === name));
  }
  const normal = fixture(); normal.start(); normal.flush(); assert.equal(normal.timing.length, 0);
});

test('unrelated revisions/diagnostic policy fields do not restore or classify existing elements', () => {
  const f = fixture(); const header = f.body.append(f.element('header', { position: 'sticky', top: '0px' }));
  f.start(); f.flush(); const before = { ...f.reads };
  f.config.revision++; f.config.diagnostics = true; f.config.scrollMetricsEnabled = true;
  f.context.__candyConfigureCssSafeArea(); f.flush();
  assert.deepEqual(f.reads, before);
  assert.equal(header.style.getPropertyValue('top'), 'max(0px, env(safe-area-inset-top, 0px))');
});

test('native env zero defers verification without declaring an overlap failure', () => {
  const f = fixture({ environmentTop: 0 });
  const header = f.body.append(f.element('header', { position: 'sticky', top: '-1px' }));
  f.document.hits = [header]; f.start(); f.flush();
  assert.equal(f.fallback.length, 0);
  assert.equal(f.reads.rect, 0, 'Unknown geometry must wait for actual native env delivery');
});

test('body and first flow wrapper authored env padding never receive a doubled Candy inset', () => {
  const bodyEnv = fixture(); bodyEnv.body.computed.paddingTop = '32px';
  bodyEnv.start(); bodyEnv.flush(); assert.equal(bodyEnv.body.style.getPropertyValue('padding-top'), '');
  const wrapperEnv = fixture(); wrapperEnv.body.append(wrapperEnv.element('main', { paddingTop: '32px' }));
  wrapperEnv.start(); wrapperEnv.flush(); assert.equal(wrapperEnv.body.style.getPropertyValue('padding-top'), '');
});

test('env zero followed by native delivery and revision-only acknowledgement finishes deferred body once', () => {
  const f = fixture({ environmentTop: 0 }); const header = f.body.append(f.element('header', { position: 'sticky', top: '0px' }));
  f.start(); f.flush(); assert.equal(f.body.style.getPropertyValue('padding-top'), '');
  const headerStyle = header.getAttribute('style');
  f.config.environmentTop = 32; f.config.revision++;
  f.context.__candyConfigureCssSafeArea(); f.flush();
  assert.equal(f.body.style.getPropertyValue('padding-top'), 'max(0px, env(safe-area-inset-top, 0px))');
  assert.equal(header.getAttribute('style'), headerStyle, 'Already-classified sticky must not be reset by acknowledgement');
});

test('unauthorized scroll author top overrides win; disabling interaction gate permits bounded repair', () => {
  const f = fixture(); const header = f.body.append(f.element('header', { position: 'sticky', top: '0px' }));
  f.start(); f.flush(); f.event('scroll');
  header.style.setProperty('top', '-4px'); header.computed.top = '-4px';
  f.mutations([{ type: 'attributes', attributeName: 'style', target: header, oldValue: '' }]); f.flush();
  assert.equal(header.style.getPropertyValue('top'), '-4px', 'Intentional author hiding must never be forced visible');
  header.style.setProperty('top', '0px'); header.computed.top = '0px';
  f.mutations([{ type: 'attributes', attributeName: 'style', target: header, oldValue: 'top: -4px;' }]); f.flush();
  assert.equal(header.style.getPropertyValue('top'), '0px', 'Default gate does not promise repair of scroll-owned inline updates');
  f.config.requireInteractionForUpdates = false; f.context.__candyConfigureCssSafeArea(); f.flush();
  header.style.setProperty('top', '0px');
  f.mutations([{ type: 'attributes', attributeName: 'style', target: header, oldValue: '' }]); f.flush();
  assert.equal(header.style.getPropertyValue('top'), 'max(0px, env(safe-area-inset-top, 0px))');
});

test('canceled queued worker cannot execute or overwrite the replacement worker state', () => {
  const f = fixture(); f.start(); const abandoned = [...f.timers.values()][0].callback;
  const before = { ...f.reads }; f.event('scroll'); abandoned();
  assert.deepEqual(f.reads, before); assert.equal(f.timers.size, 0);
  const menu = f.body.append(f.element('div', { position: 'fixed', top: '0px' }));
  f.event('click'); f.mutations([{ type: 'attributes', attributeName: 'class', target: menu }]);
  const queued = f.timers.size; abandoned(); assert.equal(f.timers.size, queued);
  f.flush(); assert.ok(menu.style.getPropertyValue('top').includes('env('));
});

test('canceled verification closure cannot read geometry after scrolling', () => {
  const f = fixture(); const header = f.body.append(f.element('header', { position: 'sticky', top: '-1px' }));
  f.document.hits = [header]; f.start(); f.step();
  const abandoned = [...f.timers.values()][0].callback;
  f.event('scroll'); const before = { ...f.reads }; abandoned(); f.flush();
  assert.deepEqual(f.reads, before); assert.equal(f.fallback.length, 0);
});

test('shared initial classification cap includes visible/header seeds without duplicate styling', () => {
  const f = fixture({ maxInitialElements: 64 });
  for (let index = 0; index < 100; index++) f.body.append(f.element('div'));
  const header = f.body.append(f.element('header', { position: 'fixed', top: '0px' }));
  const nav = f.body.append(f.element('nav', { position: 'fixed', top: '0px' }));
  f.document.hits = [header, nav]; f.start(); f.flush();
  assert.ok(header.style.getPropertyValue('top').includes('env('));
  assert.ok(nav.style.getPropertyValue('top').includes('env('));
  assert.ok(f.reads.style < 80, 'Seeds and body must share the cap instead of 3 independent 64-node traversals');
});

test('unknown verification reaches the seventeenth identity in bounded time slices', () => {
  const f = fixture({ styleCost: 4 }); const unknown = [];
  for (let index = 0; index < 20; index++) unknown.push(f.body.append(f.element('header', { position: 'sticky', top: '-1px' })));
  f.start(); f.flush(); assert.equal(f.fallback.length, 0);
  f.document.hits = [unknown[17]]; f.event('window:resize'); f.flush();
  assert.deepEqual(f.fallback, [[7, 11]]);
});

test('unknown classification is removed when a trusted change makes the element safe', () => {
  const f = fixture(); const header = f.body.append(f.element('header', { position: 'sticky', top: '-1px' }));
  f.start(); f.flush(); header.computed.top = '0px'; f.event('click');
  f.mutations([{ type: 'attributes', attributeName: 'class', target: header }]);
  f.document.hits = [header]; f.flush();
  assert.ok(header.style.getPropertyValue('top').includes('env('));
  assert.equal(f.fallback.length, 0, 'A successfully reclassified identity must leave unknown verification');
});

test('delayed author wrapper env delivery is checked before deferred body padding', () => {
  const f = fixture({ environmentTop: 0 }); const wrapper = f.body.append(f.element('main', { paddingTop: '0px' }));
  f.start(); f.flush(); wrapper.computed.paddingTop = '32px'; f.config.environmentTop = 32; f.config.revision++;
  f.context.__candyConfigureCssSafeArea(); f.flush();
  assert.equal(f.body.style.getPropertyValue('padding-top'), '');
});

test('shallow body and visible fixed seeds cannot starve behind a large header subtree', () => {
  const f = fixture({ maxInitialElements: 64 });
  const header = f.body.append(f.element('header', { position: 'fixed', top: '0px' }));
  for (let index = 0; index < 100; index++) header.append(f.element('span'));
  const control = f.body.append(f.element('button', { position: 'fixed', top: '0px' }));
  f.document.hits = [control]; f.start(); f.flush();
  assert.equal(f.body.style.getPropertyValue('padding-top'), protectedTop);
  assert.equal(header.style.getPropertyValue('top'), protectedTop);
  assert.equal(control.style.getPropertyValue('top'), protectedTop);
  assert.ok(f.reads.style < 85, 'Only 64 unique nodes plus bounded body/ancestor reads');
});

test('blank and padded panel backgrounds do not cause fallback, but unsafe absolute controls do', () => {
  for (const paddingTop of ['0px', '32px']) {
    const f = fixture(); const panel = f.body.append(f.element('div', { position: 'fixed', top: '0px', paddingTop }));
    panel.rect.height = 640; panel.rect.bottom = 640;
    const button = panel.append(f.element('button', { position: 'static' }));
    button.rect.top = 40; button.rect.bottom = 80;
    f.document.hits = [panel, button]; f.start(); f.flush();
    assert.equal(f.fallback.length, 0, 'Background bleed is not unsafe semantic content');
  }
  const f = fixture(); const panel = f.body.append(f.element('div', { position: 'fixed', top: '0px', paddingTop: '32px' }));
  panel.rect.height = 640; panel.rect.bottom = 640;
  const button = panel.append(f.element('button', { position: 'absolute' }));
  f.document.hits = [panel, button]; f.start(); f.flush();
  assert.deepEqual(f.fallback, [[7, 11]], 'Parent padding does not prove absolute descendants safe');
});

test('negative-margin fixed controls and transformed buttons with icon children remain verified unknowns', () => {
  for (const computed of [{ marginTop: '-40px' }, { willChange: 'transform' }]) {
    const f = fixture(); const button = f.body.append(f.element('button', { position: 'fixed', top: '0px', ...computed }));
    button.append(f.element('span')); f.document.hits = [button]; f.start(); f.flush();
    assert.equal(button.style.getPropertyValue('top'), '', 'Unsupported geometry is not blindly CSS-owned');
    assert.deepEqual(f.fallback, [[7, 11]], 'Interactive own box counts despite a decorative child');
  }
});

test('negative first-flow margins verify semantic residual overlap rather than body background', () => {
  for (const unsafe of [false, true]) {
    const f = fixture(); f.body.computed.paddingTop = '32px';
    const wrapper = f.body.append(f.element('main', { marginTop: '-40px' }));
    const button = wrapper.append(f.element('button'));
    if (!unsafe) { button.rect.top = 40; button.rect.bottom = 80; }
    f.document.hits = [button]; f.start(); f.flush();
    assert.equal(f.body.style.getPropertyValue('padding-top'), '');
    assert.equal(f.fallback.length, unsafe ? 1 : 0);
  }
});

test('a later unknown becoming safe cannot hide an earlier still-unsafe candidate across chunks', () => {
  const f = fixture({ styleCost: 4 });
  const first = f.body.append(f.element('header', { position: 'sticky', top: '-1px' }));
  const later = f.body.append(f.element('header', { position: 'sticky', top: '-1px' }));
  f.document.hits = [first, later]; f.start();
  while (f.reads.rect < 2) assert.ok(f.step());
  later.rect.top = 40; later.rect.bottom = 80;
  f.flush(); assert.deepEqual(f.fallback, [[7, 11]]);
});

test('diagnostic aggregate phase uses four UserTiming calls and clears every entry', () => {
  const f = fixture({ diagnostics: true }); f.start(); f.flush();
  const counts = Object.fromEntries(['mark', 'measure', 'clearMark', 'clearMeasure'].map((kind) =>
    [kind, f.timing.filter(([type]) => type === kind).length]));
  assert.ok(counts.mark > 0); assert.equal(counts.measure, counts.mark);
  assert.equal(counts.clearMark, counts.mark); assert.equal(counts.clearMeasure, counts.mark);
});

test('an initial sticky header is normal flow and cannot make later content falsely look inset-aware', () => {
  const f = fixture();
  const header = f.body.append(f.element('header', { position: 'sticky', top: '0px' }));
  header.rect.height = 72; header.rect.bottom = 72;
  const main = f.body.append(f.element('main')); main.rect.top = 72; main.rect.bottom = 640;
  f.start(); f.flush();
  assert.equal(f.body.style.getPropertyValue('padding-top'), protectedTop);
  assert.equal(header.style.getPropertyValue('top'), protectedTop);
  const before = { ...f.reads }; f.event('scroll'); f.flush(); assert.deepEqual(f.reads, before);
});

test('absolute controls remain unknown and fallback only for fresh residual unsafe geometry', () => {
  for (const safeRelativeFlow of [false, true]) {
    const f = fixture();
    const parent = safeRelativeFlow ? f.body.append(f.element('main', { position: 'relative' })) : f.body;
    const button = parent.append(f.element('button', { position: 'absolute', top: '0px' }));
    if (safeRelativeFlow) { button.rect.top = 32; button.rect.bottom = 96; }
    f.document.hits = [button]; f.start(); f.flush();
    assert.equal(button.style.getPropertyValue('top'), '', 'Absolute containing blocks are not CSS-rewritten');
    assert.equal(f.fallback.length, safeRelativeFlow ? 0 : 1);
  }
});

test('a canceled body retry cannot clear a newer generation retry handle', () => {
  const f = fixture({ environmentTop: 0 }); f.start(); f.step();
  assert.equal(f.timers.size, 1); const abandoned = [...f.timers.values()][0].callback;
  f.config.navigationGeneration++; f.context.__candyConfigureCssSafeArea(); f.step();
  assert.equal(f.timers.size, 1); const before = { ...f.reads }; abandoned();
  assert.deepEqual(f.reads, before);
  f.config.revision++; f.context.__candyConfigureCssSafeArea(); f.step();
  assert.equal(f.timers.size, 1, 'Existing replacement retry must prevent duplicate retry scheduling');
  f.flush();
});

test('unauthorized author mutations invalidate only verification evidence and queued closures', () => {
  const f = fixture(); const header = f.body.append(f.element('header', { position: 'sticky', top: '-1px' }));
  f.document.hits = [header]; f.start(); f.step(); f.step(); f.step();
  assert.equal(f.fallback.length, 0, 'One confirmed cycle must remain below threshold');
  assert.equal(f.timers.size, 1); const abandoned = [...f.timers.values()][0].callback;
  const record = { type: 'attributes', attributeName: 'class', target: header };
  const before = { ...f.reads }; f.mutations([record]); abandoned(); f.flush();
  assert.deepEqual(f.reads, before, 'Unauthorized changes do not schedule or execute geometry');
  assert.equal(f.fallback.length, 0);
  f.event('click'); f.mutations([record]); f.step(); f.step(); f.step();
  assert.equal(f.fallback.length, 0, 'A fresh layout cannot reuse the preceding failure count');
  f.flush(); assert.deepEqual(f.fallback, [[7, 11]]);
});

test('unauthorized verification invalidation does not abandon an accepted dirty worker', () => {
  const f = fixture(); const menu = f.body.append(f.element('div', { display: 'none', position: 'fixed', top: '0px' }));
  f.start(); f.flush(); menu.computed.display = 'block'; f.event('click');
  f.mutations([{ type: 'attributes', attributeName: 'class', target: menu }]);
  f.advance(1001);
  f.mutations([{ type: 'childList', target: f.body, addedNodes: [], removedNodes: [f.element('span')] }]);
  f.flush(); assert.equal(menu.style.getPropertyValue('top'), protectedTop);
});

test('bounded semantic verification visits open-shadow controls and ignores hidden hosts', () => {
  for (const hidden of [false, true]) {
    const f = fixture(); const host = f.body.append(f.element('div', {
      position: 'fixed', top: '0px', transform: 'translateY(0px)', display: hidden ? 'none' : 'block',
    }));
    const button = f.element('button');
    const shadow = { mode: 'open', host, firstElementChild: button, elementsFromPoint: () => [button] };
    host.shadowRoot = shadow; button.getRootNode = () => shadow;
    f.document.hits = [host]; f.start(); f.flush();
    assert.equal(f.fallback.length, hidden ? 0 : 1);
    assert.equal(host.style.getPropertyValue('top'), '');
    assert.ok(f.observers[0].roots.includes(shadow), 'Existing bounded discovery observes open roots');
  }
});

test('deferred native env cannot mistake sticky visual offset or its descendants for protected flow', () => {
  for (const withChild of [false, true]) {
    const f = fixture({ environmentTop: 0 });
    const header = f.body.append(f.element('header', { position: 'sticky', top: '0px' }));
    header.rect.height = 40; header.rect.bottom = 40;
    const child = withChild ? header.append(f.element('span')) : null;
    const main = f.body.append(f.element('main')); main.rect.top = 40; main.rect.bottom = 640;
    f.start(); f.flush();
    assert.equal(header.style.getPropertyValue('top'), protectedTop);
    assert.equal(f.body.style.getPropertyValue('padding-top'), '');
    // Actual CSS env now moves only sticky's visual box; main retains its old flow allocation.
    header.rect.top = 32; header.rect.bottom = 72;
    if (child) { child.rect.top = 32; child.rect.bottom = 72; }
    f.config.environmentTop = 32; f.config.revision++;
    f.context.__candyConfigureCssSafeArea(); f.flush();
    assert.equal(f.body.style.getPropertyValue('padding-top'), protectedTop);
    assert.equal(header.style.getPropertyValue('top'), protectedTop);
    assert.equal(header.rectReads, 0, 'Sticky paint geometry must never prove surrounding flow protected');
    if (child) assert.equal(child.rectReads, 0, 'Descendant paint geometry inherits sticky offset ambiguity');
  }
});
