import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import vm from 'node:vm';

const source = readFileSync(new URL('../app/src/main/assets/candy_privacy/content_safe_area_prototype.js', import.meta.url), 'utf8');
const redditSource = readFileSync(new URL('../app/src/main/assets/candy_privacy/content_safe_area_reddit.js', import.meta.url), 'utf8');

function fixture({ density = 3, nativeTop = 96, envTop = nativeTop, simulateAnimationFrames = false,
  normalizePixels = false, reparseStyles = false, prototypeSource = source, hostname = '',
  viewportContent = null, themeColor = null } = {}) {
  let clock = 0; let timerId = 0; let observer;
  const observers = [];
  let deliveredEnvTop = envTop;
  const frameCallbacks = [];
  const timers = new Map(); const listeners = new Map(); const mutations = []; const registrations = [];
  const reads = { style: 0, rect: 0, selector: 0 }; const fallbacks = []; const backdrops = []; let writes = 0;
  let selectorMatches = 0;
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
      this.localName = tag; this.nodeType = 1; this.parentElement = null; this.children = [];
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
    get offsetParent() {
      if (this.computed.position === 'fixed') return null;
      for (let parent = this.parentElement; parent; parent = parent.parentElement) {
        if (parent.computed.position !== 'static' || parent.localName === 'body') return parent;
      }
      return null;
    }
    get nextElementSibling() {
      const siblings = this.parentElement?.children || [];
      return siblings[siblings.indexOf(this) + 1] || null;
    }
    getAttribute(name) {
      if (name === 'style') return this.properties.size ? JSON.stringify([...this.properties]) : null;
      if (name === 'class' && this.classes) return this.classes.join(' ');
      if (name === 'role' && this.role) return this.role;
      return this.attributes.get(name) ?? null;
    }
    setAttribute(name, value) { this.attributes.set(name, value); if (name === 'media') this.reparse(); }
    removeAttribute(name) { this.attributes.delete(name); if (name === 'media') this.reparse(); }
    set textContent(value) { this.content = value; this.reparse(); }
    get textContent() { return this.content; }
    get firstChild() {
      if (this.content) return { nodeType: 3, textContent: this.content, nextSibling: this.children[0] || null };
      return this.children[0] || null;
    }
    reparse() {
      if (!this.sheet || !reparseStyles) return;
      this.sheet.cssRules = [];
      for (const match of this.content.matchAll(/([^{}]+)\{([^{}]+)\}/g)) this.sheet.insertRule(`${match[1]} {${match[2]}}`, this.sheet.cssRules.length);
    }
    matches(selector) {
      selectorMatches++;
      return selector.split(',').some((part) => {
        const exclusions = [...part.matchAll(/:not\(:where\(\[([^\]]+)\]\)\)/g)];
        if (exclusions.some((exclusion) => this.getAttribute(exclusion[1]) !== null)) return false;
        for (const exclusion of exclusions) part = part.replace(exclusion[0], '');
        const marker = /\[([^=]+)="([^"]+)"\]/.exec(part);
        if (marker) return this.getAttribute(marker[1]) === marker[2];
        const value = part.trim();
        if (value === 'meta[name="theme-color" i]') {
          return this.localName === 'meta' && this.getAttribute('name') === 'theme-color';
        }
        const id = /#([\w-]+)/.exec(value)?.[1];
        const classes = [...value.matchAll(/\.([\w-]+)/g)].map((match) => match[1]);
        return (!id || id === this.id) && classes.every((name) => (this.classes || []).includes(name)) &&
          (id || classes.length || value === this.localName);
      });
    }
    querySelectorAll(selector) {
      const result = [];
      const pending = [...this.children];
      while (pending.length) {
        const element = pending.shift();
        if (element.matches(selector)) result.push(element);
        pending.unshift(...element.children);
      }
      return result;
    }
    querySelector(selector) { return this.querySelectorAll(selector)[0] || null; }
    getBoundingClientRect() { reads.rect++; return this.rect || { top: 0, left: 0, width: 360, height: 40, right: 360, bottom: 40 }; }
  }
  const root = new Element('static', 'auto', 'html');
  const viewport = viewportContent === null ? null : root.append(new Element('static', 'auto', 'meta'));
  viewport?.setAttribute('name', 'viewport');
  viewport?.setAttribute('content', viewportContent);
  const theme = themeColor === null ? null : root.append(new Element('static', 'auto', 'meta'));
  theme?.setAttribute('name', 'theme-color');
  theme?.setAttribute('content', themeColor);
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
      if (selector === 'meta[name="viewport" i]') return viewport;
      reads.selector++;
      assert.ok(['header', 'nav', 'main', '[role="banner"]'].includes(selector), 'Only bounded semantic fallback queries are expected');
      if (!document.documentElement) return null;
      const pending = [document.documentElement];
      while (pending.length) {
        const element = pending.shift();
        if (element.localName === selector || (selector === '[role="banner"]' && element.role === 'banner')) return element;
        pending.unshift(...element.children);
      }
      return null;
    },
    querySelectorAll: (selector) => {
      if (selector === 'meta[name="theme-color" i]') return theme ? [theme] : [];
      if (selector === 'meta[name="viewport" i]') return viewport ? [viewport] : [];
      if (!["header, nav, [role=\"banner\"], [role=\"navigation\"]",
        'reddit-header-small, reddit-header-large, shreddit-header', 'shreddit-app', 'main'].includes(selector)) return [];
      reads.selector++;
      return root.querySelectorAll(selector);
    },
    addEventListener: (type, callback, options) => {
      listeners.set(`document:${type}`, callback); registrations.push({ target: 'document', type, options });
    },
    removeEventListener: (type, callback) => {
      if (listeners.get(`document:${type}`) === callback) listeners.delete(`document:${type}`);
    },
  };
  function computed(element) {
    const result = { display: 'block', visibility: 'visible', paddingTop: '0px', backgroundColor: 'rgba(0, 0, 0, 0)', ...element.computed };
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
          if (inline && rule.style.getPropertyPriority(name) !== 'important') continue;
          if (rule.type === 1 && element.matches(rule.selectorText) && value) result[camel] = value;
        }
      }
    }
    for (const name of ['top', 'paddingTop']) {
      if (result[name] === 'env(safe-area-inset-top)') result[name] = `${deliveredEnvTop / density}px`;
      if (result[name] === 'env(safe-area-inset-top, 0px)') result[name] = `${deliveredEnvTop / density}px`;
    }
    return result;
  }
  const windowProxy = {};
  const context = vm.createContext({ Element, document, self: windowProxy, top: windowProxy,
    location: { hostname },
    innerWidth: 800,
    innerHeight: 800,
    scrollY: 0,
    devicePixelRatio: density,
    CandyContentTopInset: {
      cssSafeAreaConfiguration: () => ({ ...config }),
      domDiagnosticsEnabled: () => true,
      fallbackToNative: (...args) => fallbacks.push(args),
      statusBarBackdrop: (...args) => backdrops.push(args),
    },
    getComputedStyle: (element) => {
      reads.style++;
      return computed(element);
    },
    performance: { now: () => clock },
    requestAnimationFrame: simulateAnimationFrames ? (callback) => frameCallbacks.push(callback) : undefined,
    setTimeout: (callback, delay = 0) => {
      const id = ++timerId; timers.set(id, { callback, at: clock + delay });
      assert.ok(timers.size <= 1, 'Only one prototype worker may be pending'); return id;
    },
    clearTimeout: (id) => timers.delete(id),
    addEventListener: (type, callback, options) => {
      listeners.set(`window:${type}`, callback); registrations.push({ target: 'window', type, options });
    },
    removeEventListener: (type, callback) => {
      if (listeners.get(`window:${type}`) === callback) listeners.delete(`window:${type}`);
    },
    MutationObserver: class {
      constructor(callback) { this.callback = callback; observer = this; observers.push(this); }
      observe(target) { this.connected = true; (this.targets ||= new Set()).add(target); }
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
  return { body, viewport, theme, context, config, reads, timers, registrations, flush, computed, sheets, fallbacks, backdrops,
    matches: () => selectorMatches,
    setEnvTop: (value) => { deliveredEnvTop = value; },
    frame(count = 1) {
      for (let index = 0; index < count; index++) frameCallbacks.shift()?.();
    },
    hasListener: (type, target = 'window') => listeners.has(`${target}:${type}`),
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
    scrollTo(y) { context.scrollY = y; },
    event(type, target = ['scroll', 'resize'].includes(type) ? 'window' : 'document', node) {
      const listener = listeners.get(`${target}:${type}`);
      if (type === 'scroll' && !listener) return;
      assert.equal(typeof listener, 'function', `Actual ${target} ${type} listener must exist`);
      listener({ type, isTrusted: true, target: node });
    },
    mutate(element, attributeName) {
      assert.ok(observer?.connected, 'DOM mutations need an active observer');
      observer.callback([{ type: 'attributes', target: element, attributeName }]);
    },
    added(element) { observer.callback([{ type: 'childList', target: body, addedNodes: [element], removedNodes: [] }]); },
    dispatchMutations(records) { observer.callback(records); },
    childAdded(parent, element) {
      parent.append(element);
      observer.callback([{ type: 'childList', target: parent, addedNodes: [element], removedNodes: [] }]);
    },
    shadowChildAdded(parent, element) {
      parent.append(element);
      for (const watched of observers) {
        if (watched.connected && watched.targets?.has(parent)) {
          watched.callback([{ type: 'childList', target: parent, addedNodes: [element], removedNodes: [] }]);
        }
      }
    },
    textChanged(element) { observer.callback([{ type: 'characterData', target: { parentElement: element } }]); },
    removed(element) { element.remove(); observer.callback([{ type: 'childList', target: body, addedNodes: [], removedNodes: [element] }]); },
    step() {
      if (mutations.length && observer?.connected) { observer.callback(mutations.splice(0)); return; }
      const [id, timer] = [...timers].sort((a, b) => a[1].at - b[1].at)[0] || [];
      if (timer) { timers.delete(id); clock = Math.max(clock, timer.at); timer.callback(); }
    },
    now: () => clock,
    advance: (millis) => { clock += millis; },
    diagnostics: () => context.CandyCssSafeAreaDiagnostics.sample(),
  };
}

test('viewport-fit cover without effective safe-area use receives CSS protection', () => {
  const f = fixture({ viewportContent: 'width=device-width, VIEWPORT-FIT = cover' });
  f.body.style.setProperty('padding-top', '4px');
  const header = f.element('fixed', '8px', 'header');
  header.rect = { top: 0, left: 0, width: 800, height: 56, right: 800, bottom: 56 };
  const main = f.element('static', 'auto', 'main');
  main.textContent = 'Unprotected page content';
  main.rect = { top: 4, left: 0, width: 800, height: 400, right: 800, bottom: 404 };
  const bottomNavigation = f.element('fixed', '600px', 'nav');
  bottomNavigation.computed.bottom = '0px';
  bottomNavigation.rect = { top: 600, left: 0, width: 800, height: 48, right: 800, bottom: 648 };
  const hiddenProbe = f.element('static', 'auto');
  hiddenProbe.computed.display = 'none';
  hiddenProbe.style.setProperty('padding-top', 'env(safe-area-inset-top)');
  f.start();
  assert.equal(f.diagnostics().active, true);
  assert.equal(f.hasListener('scroll'), true, 'Active cover repair still cancels broad work on scroll');
  const beforeScroll = { ...f.reads };
  const matchesBeforeScroll = f.matches();
  for (let index = 0; index < 100; index++) f.event('scroll');
  f.flush();
  assert.deepEqual(f.reads, beforeScroll);
  assert.equal(f.matches(), matchesBeforeScroll);
  assert.equal(f.computed(f.body).paddingTop, '32px');
  assert.equal(f.computed(header).top, '40px');
  assert.equal(f.computed(bottomNavigation).top, '600px');
  assert.equal(f.computed(bottomNavigation).bottom, '0px');
});

test('root absolute header stack clears the top safe area without moving nested absolute content', () => {
  const f = fixture();
  const wrapper = f.element('static', 'auto');
  const topHeader = wrapper.append(f.element('absolute', '0px'));
  topHeader.id = 'top-header';
  topHeader.rect = { top: 0, left: 0, width: 800, height: 31, right: 800, bottom: 31 };
  const mainHeader = wrapper.append(f.element('absolute', '31px', 'header'));
  mainHeader.id = 'main-header';
  mainHeader.rect = { top: 31, left: 0, width: 800, height: 80, right: 800, bottom: 111 };
  const logo = mainHeader.append(f.element('absolute', '0px'));
  logo.rect = { top: 31, left: 0, width: 100, height: 80, right: 100, bottom: 111 };
  const hero = wrapper.append(f.element('absolute', '0px'));
  hero.rect = { top: 0, left: 0, width: 800, height: 700, right: 800, bottom: 700 };

  f.start();
  assert.equal(f.computed(f.body).paddingTop, '32px');
  assert.equal(f.computed(topHeader).top, '32px');
  assert.equal(f.computed(mainHeader).top, '63px');
  assert.equal(f.computed(logo).top, '0px');
  assert.equal(f.computed(hero).top, '0px');
  f.scrollTo(200); f.event('scroll'); f.flush();
  assert.equal(f.computed(topHeader).top, '32px');
  assert.equal(f.computed(mainHeader).top, '63px');
});

test('cover page protects only root absolute headers lacking an author safe-area offset', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover' });
  const unaware = f.element('absolute', '0px', 'header');
  unaware.rect = { top: 0, left: 0, width: 800, height: 48, right: 800, bottom: 48 };
  const aware = f.element('absolute', 'auto', 'header');
  aware.style.setProperty('top', 'env(safe-area-inset-top)');
  aware.rect = { top: 32, left: 0, width: 800, height: 48, right: 800, bottom: 80 };
  const main = f.element('static', 'auto', 'main');
  main.textContent = 'Page content';
  main.rect = { top: 0, left: 0, width: 800, height: 400, right: 800, bottom: 400 };

  f.start();
  assert.equal(f.computed(unaware).top, '32px');
  assert.equal(f.computed(aware).top, '32px');
});

test('viewport-fit cover with effective safe-area padding and top keeps author geometry', () => {
  const f = fixture({ viewportContent: 'width=device-width,viewport-fit=cover' });
  f.body.style.setProperty('padding-top', 'env(safe-area-inset-top)');
  const header = f.element('fixed', 'auto', 'header');
  header.style.setProperty('top', 'env(safe-area-inset-top)');
  header.rect = { top: 32, left: 0, width: 800, height: 56, right: 800, bottom: 88 };
  const main = f.element('static', 'auto', 'main');
  main.textContent = 'Safe-area-aware page content';
  main.rect = { top: 32, left: 0, width: 800, height: 400, right: 800, bottom: 432 };
  f.start();
  assert.equal(f.computed(f.body).paddingTop, '32px');
  assert.equal(f.computed(header).top, '32px');
});

test('same-document route generation rechecks cover protection with unchanged viewport meta', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover' });
  f.body.style.setProperty('padding-top', '4px');
  const header = f.element('fixed', '0px', 'header');
  header.rect = { top: 0, left: 0, width: 800, height: 56, right: 800, bottom: 56 };
  const main = f.element('static', 'auto', 'main');
  main.content = 'Detail';
  main.rect = { top: 4, left: 0, width: 800, height: 400, right: 800, bottom: 404 };
  f.start();
  assert.equal(f.diagnostics().active, true);

  f.body.style.setProperty('padding-top', '32px');
  header.computed.top = '32px';
  header.rect.top = 32; header.rect.bottom = 88;
  main.rect.top = 32; main.rect.bottom = 432;
  f.configure({ navigationGeneration: 2 });
  assert.equal(f.diagnostics().active, false);

  f.body.style.setProperty('padding-top', '4px');
  header.computed.top = '0px';
  header.rect.top = 0; header.rect.bottom = 56;
  main.rect.top = 4; main.rect.bottom = 404;
  f.configure({ navigationGeneration: 3 });
  assert.equal(f.diagnostics().active, true);
});

test('unchanged cover protection retains its stylesheet and anchors across menu mutations', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover' });
  const header = f.element('fixed', '0px', 'header');
  header.rect = { top: 0, left: 0, width: 800, height: 56, right: 800, bottom: 56 };
  const main = f.element('static', 'auto', 'main');
  main.content = 'Visible page content';
  f.start();
  assert.equal(f.diagnostics().active, true);
  assert.equal(f.computed(header).top, '32px');
  assert.equal(f.computed(f.body).paddingTop, '32px');

  const layer = f.sheets.find((element) => element.isConnected &&
    element.parentElement === f.context.document.documentElement);
  assert.ok(layer);
  const rules = [...layer.sheet.cssRules];
  const headerMarkers = [...header.attributes];
  const bodyMarkers = [...f.body.attributes];
  const assertRetained = () => {
    assert.equal(layer.isConnected, true, 'An unchanged cover decision must not detach protection');
    assert.equal(layer.sheet.cssRules.length, rules.length);
    for (let index = 0; index < rules.length; index++) {
      assert.equal(layer.sheet.cssRules[index], rules[index], 'Existing anchors keep their rule identity');
    }
    assert.deepEqual([...header.attributes], headerMarkers);
    assert.deepEqual([...f.body.attributes], bodyMarkers);
    assert.equal(f.computed(header).top, '32px');
    assert.equal(f.computed(f.body).paddingTop, '32px');
  };

  for (const classes of [['menu-open'], []]) {
    header.classes = classes;
    f.event('click', 'document', header);
    f.mutate(header, 'class');
    assertRetained();
    f.flush();
    assertRetained();
  }

  const menu = f.context.document.createElement('nav');
  menu.content = 'Menu links';
  f.childAdded(header, menu);
  assertRetained();
  f.flush();
  assertRetained();
});

test('unchanged cover protection adds missing body padding when normal flow appears', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover' });
  const header = f.element('fixed', '0px', 'header');
  header.rect = { top: 0, left: 0, width: 800, height: 56, right: 800, bottom: 56 };
  f.start();
  assert.equal(f.diagnostics().active, true);
  assert.equal(f.computed(header).top, '32px');
  assert.equal(f.computed(f.body).paddingTop, '0px');

  const main = f.element('static', 'auto', 'main');
  main.content = 'New visible page content';
  f.added(main);
  f.flush();

  assert.equal(f.computed(f.body).paddingTop, '32px');
  assert.equal(f.computed(header).top, '32px');
});

test('cover body scroll lock gains its inset before the debounced worker', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover' });
  const header = f.element('sticky', '0px', 'header');
  header.rect = { top: 0, left: 0, width: 800, height: 56, right: 800, bottom: 56 };
  const logo = f.context.document.createElement('svg');
  header.append(logo);
  f.start();
  assert.equal(f.computed(f.body).paddingTop, '0px');
  assert.equal(f.computed(header).top, '32px');

  f.event('click', 'document', header);
  f.body.computed.position = 'fixed';
  f.body.computed.top = '0px';
  f.body.classes = ['scroll-locked'];
  f.mutate(f.body, 'class');
  assert.equal(f.computed(f.body).top, '32px', 'The containing block is protected before any worker runs');
  assert.equal(f.computed(f.body).paddingTop, '0px');
  f.flush();
  assert.equal(f.computed(f.body).top, '32px');

  f.body.computed.position = 'static';
  f.body.computed.top = 'auto';
  f.body.classes = [];
  f.mutate(f.body, 'class');
  f.flush();
  f.event('click', 'document', header);
  f.body.computed.position = 'fixed';
  f.body.computed.top = '0px';
  f.body.classes = ['scroll-locked'];
  f.mutate(f.body, 'class');
  assert.equal(f.computed(f.body).top, '32px', 'Reopening does not add a second inset');
});

test('cover body scroll lock keeps existing body padding without another top inset', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover' });
  const header = f.element('sticky', '0px', 'header');
  header.content = 'Visible header text';
  header.rect = { top: 0, left: 0, width: 800, height: 56, right: 800, bottom: 56 };
  f.start();
  assert.equal(f.computed(f.body).paddingTop, '32px');

  for (let opening = 0; opening < 2; opening++) {
    f.event('click', 'document', header);
    f.body.computed.position = 'fixed';
    f.body.computed.top = '0px';
    f.body.classes = ['scroll-locked'];
    f.mutate(f.body, 'class');
    assert.equal(f.computed(f.body).top, '0px', 'Existing body padding already protects the flow');
    assert.equal(f.computed(f.body).paddingTop, '32px');
    f.flush();
    assert.equal(f.computed(f.body).top, '0px', 'The worker does not duplicate the inset either');

    f.body.computed.position = 'static';
    f.body.computed.top = 'auto';
    f.body.classes = [];
    f.mutate(f.body, 'class');
    f.flush();
  }
});

test('loading cover protects an SVG-first sticky header synchronously', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover' });
  f.context.document.readyState = 'loading';
  const header = f.element('sticky', '0px', 'header');
  header.rect = { top: 0, left: 0, width: 800, height: 56, right: 800, bottom: 56 };
  header.append(f.context.document.createElement('svg'));
  f.start(false);
  assert.equal(f.computed(header).top, '32px', 'Protection precedes the first worker or parser completion');
  assert.equal(f.computed(f.body).paddingTop, '0px');
});

test('loading cover protects a header promoted by parser-time CSS without a quiet delay', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover' });
  f.context.document.readyState = 'loading';
  const header = f.element('static', 'auto', 'header');
  header.rect = { top: 0, left: 0, width: 800, height: 56, right: 800, bottom: 56 };
  header.append(f.context.document.createElement('svg'));
  f.start();
  assert.equal(f.diagnostics().active, false);
  header.computed.position = 'sticky';
  header.computed.top = '0px';
  header.classes = ['styled'];
  f.mutate(header, 'class');
  assert.equal(f.computed(header).top, '32px', 'Parser-time promotion needs no trusted click or worker');
  assert.equal(f.computed(f.body).paddingTop, '0px');
});

test('immediate cover body protection keeps the changed-element interaction gates', () => {
  for (const changedElements of [true, false]) {
    const f = fixture({ viewportContent: 'viewport-fit=cover' });
    const header = f.element('sticky', '0px', 'header');
    header.rect = { top: 0, left: 0, width: 800, height: 56, right: 800, bottom: 56 };
    f.start();
    f.configure({ recheckChangedElements: changedElements });
    if (!changedElements) f.event('click', 'document', header);
    f.body.computed.position = 'fixed';
    f.body.computed.top = '0px';
    f.body.classes = ['scroll-locked'];
    f.mutate(f.body, 'class');
    assert.equal(f.computed(f.body).top, '0px');
  }
});

test('cover route watches late generic div replacement only during bounded settling', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover' });
  const header = f.element('fixed', '32px', 'header');
  header.rect = { top: 32, left: 0, width: 800, height: 56, right: 800, bottom: 88 };
  const main = f.element('static', 'auto', 'main');
  main.content = 'Initial safe content';
  main.rect = { top: 32, left: 0, width: 800, height: 400, right: 800, bottom: 432 };
  f.start();
  assert.equal(f.diagnostics().active, false);

  f.configure({ navigationGeneration: 2 });
  assert.equal(f.diagnostics().active, false);
  const movedContent = f.element('static', 'auto');
  movedContent.content = 'Router content';
  movedContent.rect = { top: 0, left: 0, width: 800, height: 300, right: 800, bottom: 300 };
  f.added(movedContent);
  f.flush();
  assert.equal(f.diagnostics().active, true);
  assert.equal(f.hasListener('scroll'), true);
});

test('late native env delivery rechecks an initially protected cover page once', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover', envTop: 0,
    simulateAnimationFrames: true });
  f.body.style.setProperty('padding-top', 'env(safe-area-inset-top)');
  const header = f.element('fixed', 'auto', 'header');
  header.style.setProperty('top', 'env(safe-area-inset-top)');
  header.rect = { top: 0, left: 0, width: 800, height: 56, right: 800, bottom: 56 };
  const main = f.element('static', 'auto', 'main');
  main.content = 'Protected page content';
  main.rect = { top: 0, left: 0, width: 800, height: 400, right: 800, bottom: 400 };
  f.start();
  assert.equal(f.diagnostics().active, true);
  f.setEnvTop(96);
  header.rect.top = 32; header.rect.bottom = 88;
  main.rect.top = 32; main.rect.bottom = 432;
  f.frame(4);
  f.flush();
  assert.equal(f.diagnostics().active, false);
  assert.equal(f.computed(header).top, '32px');
  assert.equal(f.computed(f.body).paddingTop, '32px');
});

test('viewport-fit cover protects only an unaware fixed anchor on a mixed page', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover' });
  f.body.style.setProperty('padding-top', 'env(safe-area-inset-top)');
  const aware = f.element('fixed', 'auto', 'header');
  aware.style.setProperty('top', 'env(safe-area-inset-top)');
  aware.rect = { top: 32, left: 0, width: 800, height: 56, right: 800, bottom: 88 };
  const unaware = f.element('fixed', '8px', 'header');
  unaware.rect = { top: 0, left: 0, width: 800, height: 56, right: 800, bottom: 56 };
  const main = f.element('static', 'auto', 'main');
  main.textContent = 'Page content below safe area';
  main.rect = { top: 32, left: 0, width: 800, height: 400, right: 800, bottom: 432 };
  f.start();
  assert.equal(f.computed(f.body).paddingTop, '32px');
  assert.equal(f.computed(aware).top, '32px');
  assert.equal(f.computed(unaware).top, '40px');
});

test('cover page catches an unprotected custom fixed top control with safe body flow', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover' });
  f.body.style.setProperty('padding-top', 'env(safe-area-inset-top)');
  const control = f.element('fixed', '0px', 'custom-topbar');
  control.rect = { top: 0, left: 0, width: 800, height: 48, right: 800, bottom: 48 };
  const main = f.element('static', 'auto', 'main');
  main.textContent = 'Protected flow';
  main.rect = { top: 32, left: 0, width: 800, height: 400, right: 800, bottom: 432 };
  f.start();
  assert.equal(f.computed(f.body).paddingTop, '32px');
  assert.equal(f.computed(control).top, '32px');
});

test('cover page accepts combined top and padding that keep header content safe', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover' });
  f.body.style.setProperty('padding-top', 'env(safe-area-inset-top)');
  const header = f.element('fixed', '16px', 'header');
  header.style.setProperty('padding-top', '16px');
  header.rect = { top: 16, left: 0, width: 800, height: 48, right: 800, bottom: 64 };
  const main = f.element('static', 'auto', 'main');
  main.textContent = 'Protected flow';
  main.rect = { top: 32, left: 0, width: 800, height: 400, right: 800, bottom: 432 };
  f.start();
  assert.equal(f.computed(header).top, '16px');
  assert.equal(f.computed(f.body).paddingTop, '32px');
});

test('viewport meta change rechecks cover protection without a policy update', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover' });
  f.body.style.setProperty('padding-top', 'env(safe-area-inset-top)');
  const header = f.element('fixed', 'auto', 'header');
  header.style.setProperty('top', 'env(safe-area-inset-top)');
  header.rect = { top: 32, left: 0, width: 800, height: 56, right: 800, bottom: 88 };
  const main = f.element('static', 'auto', 'main');
  main.textContent = 'Page content after viewport change';
  main.rect = { top: 32, left: 0, width: 800, height: 400, right: 800, bottom: 432 };
  f.start();
  assert.equal(f.computed(header).top, '32px');
  assert.equal(f.hasListener('scroll'), false);

  f.body.style.setProperty('padding-top', '4px');
  header.style.setProperty('top', '8px');
  header.rect = { top: 0, left: 0, width: 800, height: 56, right: 800, bottom: 56 };
  main.rect = { top: 4, left: 0, width: 800, height: 400, right: 800, bottom: 404 };
  f.viewport.setAttribute('content', 'width=device-width');
  f.mutate(f.viewport, 'content');
  f.flush();

  assert.equal(f.diagnostics().active, true);
  assert.equal(f.hasListener('scroll'), true);
  assert.equal(f.computed(f.body).paddingTop, '32px');
  assert.equal(f.computed(header).top, '40px');
});

test('late cover stylesheet can replace fallback offsets with native env padding', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover' });
  f.body.style.setProperty('padding-top', '4px');
  const header = f.element('fixed', '8px', 'header');
  header.rect = { top: 0, left: 0, width: 800, height: 56, right: 800, bottom: 56 };
  const main = f.element('static', 'auto', 'main');
  main.textContent = 'Visible content';
  main.rect = { top: 4, left: 0, width: 800, height: 400, right: 800, bottom: 404 };
  f.start();
  assert.equal(f.computed(f.body).paddingTop, '32px');
  assert.equal(f.computed(header).top, '40px');

  f.body.style.setProperty('padding-top', 'env(safe-area-inset-top)');
  header.style.setProperty('top', 'env(safe-area-inset-top)');
  header.rect.top = 32;
  header.rect.bottom = 88;
  main.rect.top = 32;
  main.rect.bottom = 432;
  f.event('load', 'document', f.element('static', 'auto', 'link'));
  f.flush();
  assert.equal(f.computed(f.body).paddingTop, '32px');
  assert.equal(f.computed(header).top, '32px');
  f.configure({ revision: 2 });
  assert.equal(f.computed(header).top, '32px');
});

test('Reddit helper remains active for viewport cover and receives the bounded native inset', () => {
  const f = fixture({ hostname: 'www.reddit.com', viewportContent: 'viewport-fit=cover' });
  const configured = [];
  f.context.CandyRedditSafeArea = {
    owns: () => false,
    ownsSource: () => false,
    added: () => {},
    sync: () => {},
    flowProtected: () => true,
    configure: (active) => configured.push(active),
  };

  f.start();

  assert.equal(f.diagnostics().active, false, 'Generic CSS layer still respects viewport cover');
  assert.equal(configured.at(-1), true);
  assert.equal(
    f.context.document.documentElement.style.getPropertyValue('--candy-safe-area-inset-top'),
    '32px',
  );
  f.configure({ enabled: false });
  assert.equal(configured.at(-1), false);
  assert.equal(f.context.document.documentElement.style.getPropertyValue('--candy-safe-area-inset-top'), '');
});

test('viewport-cover sticky header gets a theme-color backdrop before scroll without recurring work', () => {
  const f = fixture({ viewportContent: 'width=device-width, viewport-fit=cover', themeColor: '#ff4500' });
  const header = f.element('sticky', '32px', 'header');
  header.rect = { top: 32, left: 0, width: 800, height: 56, right: 800, bottom: 88 };
  f.start();
  assert.equal(f.diagnostics().active, false);
  assert.equal(f.hasListener('scroll'), false);
  assert.equal(f.hasListener('scroll', 'document'), false);
  assert.deepEqual(f.backdrops, [[1, 1, '#ff4500']]);
  const beforeScroll = { ...f.reads };
  const matchesBeforeScroll = f.matches();
  for (let index = 0; index < 100; index++) {
    f.scrollTo(index * 12);
    f.event('scroll', index % 2 ? 'document' : 'window');
  }
  f.flush();
  assert.deepEqual(f.reads, beforeScroll, 'Stable scrolling does not inspect styles, geometry or selectors');
  assert.equal(f.matches(), matchesBeforeScroll);
  assert.equal(f.timers.size, 0);
  assert.deepEqual(f.backdrops, [[1, 1, '#ff4500']]);
  assert.deepEqual(f.fallbacks, []);
});

test('status backdrop requires an opaque theme color and a pinned header below the inset', () => {
  const noTheme = fixture({ viewportContent: 'viewport-fit=cover' });
  noTheme.element('sticky', '32px', 'header').rect =
    { top: 32, left: 0, width: 800, height: 56, right: 800, bottom: 88 };
  noTheme.start();
  const noThemeReads = { ...noTheme.reads };
  noTheme.event('scroll');
  noTheme.flush();
  assert.deepEqual(noTheme.backdrops, []);
  assert.deepEqual(noTheme.reads, noThemeReads);

  const wrongPosition = fixture({ viewportContent: 'viewport-fit=cover', themeColor: '#abcdef' });
  wrongPosition.element('static', 'auto', 'header').rect =
    { top: 32, left: 0, width: 800, height: 56, right: 800, bottom: 88 };
  wrongPosition.start();
  const wrongPositionReads = { ...wrongPosition.reads };
  wrongPosition.event('scroll');
  wrongPosition.flush();
  assert.deepEqual(wrongPosition.backdrops, []);
  assert.deepEqual(wrongPosition.reads, wrongPositionReads);
});

test('Reddit backdrop prioritizes its custom header after earlier semantic elements', () => {
  const f = fixture({
    hostname: 'www.reddit.com',
    viewportContent: 'viewport-fit=cover',
    themeColor: '#ff4500',
  });
  for (let index = 0; index < 12; index++) f.element('static', 'auto', 'header');
  const header = f.element('sticky', 'max(var(--candy-safe-area-inset-top), env(safe-area-inset-top))',
    'reddit-header-small');
  header.rect = { top: 32, left: 0, width: 800, height: 56, right: 800, bottom: 88 };
  f.start();
  f.event('scroll');
  f.flush();
  assert.deepEqual(f.backdrops, [[1, 1, '#ff4500']]);
  const beforeScroll = { ...f.reads };
  const matchesBeforeScroll = f.matches();
  for (let index = 0; index < 100; index++) {
    f.event('scroll');
    f.mutate(header, 'class');
  }
  f.flush();
  assert.deepEqual(f.reads, beforeScroll, 'Reddit scroll-state classes do not trigger backdrop discovery');
  assert.equal(f.matches(), matchesBeforeScroll);
});

test('Reddit backdrop finds a protected header in its open app shadow root', () => {
  const f = fixture({ hostname: 'www.reddit.com', viewportContent: 'viewport-fit=cover', themeColor: '#ff4500' });
  const app = f.element('static', 'auto', 'shreddit-app');
  app.shadowRoot = f.context.document.createElement('shadow-root');
  const header = f.context.document.createElement('reddit-header-small');
  header.computed = { position: 'sticky', top: '32px' };
  header.rect = { top: 32, left: 0, width: 800, height: 56, right: 800, bottom: 88 };
  app.shadowRoot.append(header);
  vm.runInContext(redditSource, f.context);
  f.start();
  f.event('scroll');
  f.flush();
  assert.deepEqual(f.backdrops, [[1, 1, '#ff4500']]);
});

test('late cover header is detected from a structural change without scrolling', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover', themeColor: '#123456' });
  f.start();
  assert.deepEqual(f.backdrops, []);
  const header = f.element('sticky', '32px', 'header');
  header.rect = { top: 32, left: 0, width: 800, height: 56, right: 800, bottom: 88 };
  f.added(header);
  f.flush();
  assert.deepEqual(f.backdrops, [[1, 1, '#123456']]);
});

test('late cover header takes priority when earlier candidates fill the scan budget', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover', themeColor: '#123456' });
  for (let index = 0; index < 40; index++) f.element('static', 'auto', 'header');
  f.start();
  assert.deepEqual(f.backdrops, []);
  const header = f.element('sticky', '32px', 'header');
  header.rect = { top: 32, left: 0, width: 800, height: 56, right: 800, bottom: 88 };
  f.added(header);
  f.added(f.element('static', 'auto', 'header'));
  f.flush();
  assert.deepEqual(f.backdrops, [[1, 1, '#123456']]);
});

test('late cover wrapper exposes its nested header without a document scan', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover', themeColor: '#123456' });
  f.start();
  const wrapper = f.element('static', 'auto', 'div');
  const header = f.element('sticky', '32px', 'header');
  header.rect = { top: 32, left: 0, width: 800, height: 56, right: 800, bottom: 88 };
  wrapper.append(header);
  const selectorReads = f.reads.selector;
  f.added(wrapper);
  f.flush();
  assert.deepEqual(f.backdrops, [[1, 1, '#123456']]);
  assert.equal(f.reads.selector, selectorReads + 2, 'Only theme metadata is read during reporting');
});

test('cover header becoming sticky before scrolling enables the backdrop', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover', themeColor: '#123456' });
  const header = f.element('static', 'auto', 'header');
  header.rect = { top: 32, left: 0, width: 800, height: 56, right: 800, bottom: 88 };
  f.start();
  assert.deepEqual(f.backdrops, []);
  Object.assign(header.computed, { position: 'sticky', top: '32px' });
  f.mutate(header, 'class');
  f.flush();
  assert.deepEqual(f.backdrops, [[1, 1, '#123456']]);
});

test('cover header becoming sticky after scrolling gets one quiet, bounded check', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover', themeColor: '#123456' });
  const header = f.element('static', 'auto', 'header');
  header.rect = { top: 32, left: 0, width: 800, height: 56, right: 800, bottom: 88 };
  f.start();
  f.event('scroll');
  f.flush();
  header.setAttribute('class', 'waiting');
  f.mutate(header, 'class');
  f.flush();
  const beforeRepeat = { ...f.reads };
  const matchesBeforeRepeat = f.matches();
  for (let index = 0; index < 100; index++) {
    f.event('scroll');
    f.mutate(header, 'class');
  }
  f.flush();
  assert.deepEqual(f.reads, beforeRepeat, 'Repeated scroll-state class has no layout probes');
  assert.equal(f.matches(), matchesBeforeRepeat);
  assert.equal(f.timers.size, 0);
  Object.assign(header.computed, { position: 'sticky', top: '32px' });
  header.setAttribute('class', 'is-sticky');
  f.mutate(header, 'class');
  f.flush();
  assert.deepEqual(f.backdrops, [[1, 1, '#123456']]);
});

test('late sticky state remains detectable after more than eight class changes', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover', themeColor: '#123456' });
  const header = f.element('static', 'auto', 'header');
  header.rect = { top: 32, left: 0, width: 800, height: 56, right: 800, bottom: 88 };
  f.start();
  f.event('scroll');
  for (let index = 0; index < 9; index++) {
    header.setAttribute('class', `state-${index}`);
    if (index === 8) Object.assign(header.computed, { position: 'sticky', top: '32px' });
    f.mutate(header, 'class');
    f.flush();
  }
  assert.deepEqual(f.backdrops, [[1, 1, '#123456']]);
});

test('backdrop clears when a connected cover header stops being sticky', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover', themeColor: '#123456' });
  const header = f.element('sticky', '32px', 'header');
  header.rect = { top: 32, left: 0, width: 800, height: 56, right: 800, bottom: 88 };
  f.start();
  Object.assign(header.computed, { position: 'static', top: 'auto' });
  header.classes = ['static'];
  header.setAttribute('class', 'unpinned');
  f.mutate(header, 'class');
  f.flush();
  assert.deepEqual(f.backdrops, [[1, 1, '#123456'], [1, 1, null]]);
});

test('cover page rechecks author padding after a body class change following scroll', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover' });
  f.body.style.setProperty('padding-top', 'env(safe-area-inset-top)');
  const main = f.element('static', 'auto', 'main');
  main.content = 'Protected content';
  main.rect = { top: 32, left: 0, width: 800, height: 400, right: 800, bottom: 432 };
  f.start();
  assert.equal(f.diagnostics().active, false);
  f.event('scroll');
  f.body.style.setProperty('padding-top', '0px');
  main.rect.top = 0;
  f.body.setAttribute('class', 'no-safe-padding');
  f.mutate(f.body, 'class');
  f.flush();
  assert.equal(f.diagnostics().active, true);
  assert.equal(f.computed(f.body).paddingTop, '32px');
});

test('confirmed cover header skips lazy-load discovery and clears after removal', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover', themeColor: '#123456' });
  const header = f.element('sticky', '32px', 'header');
  header.rect = { top: 32, left: 0, width: 800, height: 56, right: 800, bottom: 88 };
  f.start();
  const beforeLazyLoad = { ...f.reads };
  const matchesBeforeLazyLoad = f.matches();
  for (let index = 0; index < 100; index++) f.added(f.element('static', 'auto', 'article'));
  f.flush();
  assert.deepEqual(
    { style: f.reads.style, rect: f.reads.rect, selector: f.reads.selector },
    { style: beforeLazyLoad.style, rect: beforeLazyLoad.rect, selector: beforeLazyLoad.selector },
  );
  assert.ok(f.matches() - matchesBeforeLazyLoad <= 200,
    'Each new article receives at most two direct metadata/layout selector checks');
  assert.equal(f.timers.size, 0);
  header.remove();
  f.mutate(f.body, 'class');
  f.flush();
  assert.deepEqual(f.backdrops, [[1, 1, '#123456'], [1, 1, null]]);
});

test('late Reddit shadow header is detected by its helper without scrolling', () => {
  const f = fixture({ hostname: 'www.reddit.com', viewportContent: 'viewport-fit=cover', themeColor: '#ff4500' });
  const app = f.element('static', 'auto', 'shreddit-app');
  app.shadowRoot = f.context.document.createElement('shadow-root');
  vm.runInContext(redditSource, f.context);
  f.start();
  assert.deepEqual(f.backdrops, []);
  const header = f.context.document.createElement('reddit-header-small');
  header.computed = { position: 'sticky', top: '32px' };
  header.rect = { top: 32, left: 0, width: 800, height: 56, right: 800, bottom: 88 };
  app.shadowRoot.append(header);
  f.context.CandyRedditSafeArea.sync();
  f.flush();
  assert.deepEqual(f.backdrops, [[1, 1, '#ff4500']]);
});

test('Reddit shadow router rechecks same header after route signal and content move', () => {
  const f = fixture({ hostname: 'www.reddit.com', viewportContent: 'viewport-fit=cover', themeColor: '#ff4500' });
  const app = f.element('static', 'auto', 'shreddit-app');
  app.shadowRoot = f.context.document.createElement('shadow-root');
  const header = f.context.document.createElement('reddit-header-small');
  header.computed = { position: 'static', top: '32px' };
  header.rect = { top: 32, left: 0, width: 800, height: 56, right: 800, bottom: 88 };
  app.shadowRoot.append(header);
  vm.runInContext(redditSource, f.context);
  f.start();
  assert.deepEqual(f.backdrops, []);

  f.configure({ navigationGeneration: 2 });
  assert.deepEqual(f.backdrops, []);
  header.computed.position = 'sticky';
  const routerContent = f.context.document.createElement('div');
  f.shadowChildAdded(app.shadowRoot, routerContent);
  f.flush();
  assert.deepEqual(f.backdrops.at(-1), [2, 1, '#ff4500']);
});

test('backdrop recognizes a sticky wrapper around a semantic header', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover', themeColor: '#123456' });
  const wrapper = f.element('sticky', '32px', 'div');
  wrapper.rect = { top: 32, left: 0, width: 800, height: 56, right: 800, bottom: 88 };
  const header = f.element('static', 'auto', 'header');
  wrapper.append(header);
  f.start();
  f.event('scroll');
  f.flush();
  assert.deepEqual(f.backdrops, [[1, 1, '#123456']]);
});

test('backdrop updates and clears when the page changes theme-color', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover', themeColor: '#123456' });
  const header = f.element('sticky', '32px', 'header');
  header.rect = { top: 32, left: 0, width: 800, height: 56, right: 800, bottom: 88 };
  f.start();
  f.event('scroll');
  f.flush();
  f.theme.setAttribute('content', '#abcdef');
  f.mutate(f.theme, 'content');
  f.flush();
  f.theme.setAttribute('content', 'transparent');
  f.mutate(f.theme, 'content');
  f.flush();
  assert.deepEqual(f.backdrops, [[1, 1, '#123456'], [1, 1, '#abcdef'], [1, 1, null]]);
});

test('disabling safe-area policy clears the reported status-bar backdrop', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover', themeColor: '#123456' });
  const header = f.element('sticky', '32px', 'header');
  header.rect = { top: 32, left: 0, width: 800, height: 56, right: 800, bottom: 88 };
  f.start();
  f.event('scroll');
  f.flush();
  f.configure({ enabled: false, revision: 2 });
  assert.deepEqual(f.backdrops, [[1, 1, '#123456'], [1, 2, null]]);
});

test('sticky headers switch immediately while fixed headers must remain pinned after scrolling', () => {
  const sticky = fixture({ themeColor: '#123AbC' });
  const stickyHeader = sticky.element('sticky', '0px', 'header');
  stickyHeader.rect = { top: 0, left: 0, width: 800, height: 56, right: 800, bottom: 56 };
  sticky.start();
  assert.deepEqual(sticky.fallbacks, [[1, 1, '#123abc', true]]);

  const fixed = fixture({ themeColor: '#234AbC' });
  const fixedHeader = fixed.element('fixed', '0px', 'header');
  fixedHeader.rect = { top: 0, left: 0, width: 800, height: 56, right: 800, bottom: 56 };
  fixed.start();
  assert.deepEqual(fixed.fallbacks, [], 'Initial fixed positioning retains edge-to-edge');
  fixed.scrollTo(80);
  fixed.event('scroll');
  fixed.flush();
  assert.deepEqual(fixed.fallbacks, [[1, 1, '#234abc', true]]);

  const scrollingAway = fixture({ themeColor: '#345AbC' });
  const transientHeader = scrollingAway.element('fixed', '0px', 'header');
  transientHeader.rect = { top: 0, left: 0, width: 800, height: 56, right: 800, bottom: 56 };
  scrollingAway.start();
  scrollingAway.scrollTo(80);
  transientHeader.rect = { top: -56, left: 0, width: 800, height: 56, right: 800, bottom: 0 };
  scrollingAway.event('scroll');
  scrollingAway.flush();
  assert.deepEqual(scrollingAway.fallbacks, [], 'A fixed declaration that scrolls away stays edge-to-edge');

  const restored = fixture({ themeColor: '#456AbC' });
  restored.scrollTo(500);
  const restoredHeader = restored.element('fixed', '0px', 'header');
  restoredHeader.rect = { top: 0, left: 0, width: 800, height: 56, right: 800, bottom: 56 };
  restored.start();
  assert.deepEqual(restored.fallbacks, [], 'Restored scroll position is only a new baseline');
  restored.event('scroll');
  restored.flush();
  assert.deepEqual(restored.fallbacks, []);
  restored.scrollTo(580);
  restored.event('scroll');
  restored.flush();
  assert.deepEqual(restored.fallbacks, [[1, 1, '#456abc', true]]);
});

test('an early scroll still checks a semantic sticky header', () => {
  const f = fixture({ themeColor: '#123abc' });
  const header = f.element('sticky', '0px', 'header');
  header.rect = { top: 32, left: 0, width: 800, height: 56, right: 800, bottom: 88 };
  f.start(false);
  const selectorReads = f.reads.selector;
  f.scrollTo(80);
  f.event('scroll');
  f.scrollTo(120);
  f.event('scroll');
  assert.equal(f.reads.selector, selectorReads, 'Scroll itself does not query the DOM');
  f.flush();

  assert.equal(f.reads.selector, selectorReads + 1, 'Queued semantic check runs once after scroll quiet');
  assert.deepEqual(f.fallbacks, [[1, 1, '#123abc', true]]);
});

test('cached Reddit custom headers coalesce scroll work and accept nested content movement', () => {
  const f = fixture({ hostname: 'www.reddit.com', themeColor: '#ff4500' });
  for (let index = 0; index < 12; index++) f.element('static', 'auto', 'header');
  const customHeader = f.element('fixed', '0px', 'reddit-header-large');
  customHeader.rect = { top: 0, left: 0, width: 800, height: 56, right: 800, bottom: 56 };
  const content = f.element('static', 'auto', 'main');
  content.rect = { top: 56, left: 0, width: 800, height: 1600, right: 800, bottom: 1656 };
  f.start();
  assert.deepEqual(f.fallbacks, []);

  const beforeScroll = { ...f.reads };
  for (let index = 0; index < 100; index++) f.event('scroll', index % 2 ? 'document' : 'window');
  assert.deepEqual(f.reads, beforeScroll, 'Scroll events must not read style, geometry or selectors');
  assert.equal(f.timers.size, 1, 'A scroll burst must retain one quiet worker');
  content.rect = { ...content.rect, top: -80, bottom: 1520 };
  f.flush();

  assert.deepEqual(f.fallbacks, [[1, 1, '#ff4500', true]]);
  assert.equal(f.reads.selector, beforeScroll.selector, 'Quiet verification must use cached candidates');
});

test('fixed header position changes reset persistence proof before a later scroll', () => {
  const f = fixture({ hostname: 'www.reddit.com', themeColor: '#123456' });
  const header = f.element('fixed', '0px', 'shreddit-header');
  header.rect = { top: 0, left: 0, width: 800, height: 56, right: 800, bottom: 56 };
  f.element('static', 'auto', 'main').rect =
    { top: 56, left: 0, width: 800, height: 1600, right: 800, bottom: 1656 };
  f.start();

  f.scrollTo(80);
  f.event('scroll');
  Object.assign(header.computed, { position: 'static', top: 'auto' });
  f.mutate(header, 'class');
  f.flush();
  assert.deepEqual(f.fallbacks, [], 'A stale fixed baseline must not survive a static phase');

  Object.assign(header.computed, { position: 'fixed', top: '0px' });
  header.classes = ['fixed'];
  f.mutate(header, 'class');
  f.flush();
  assert.deepEqual(f.fallbacks, [], 'Returning to fixed starts a new baseline');
  f.scrollTo(160);
  f.event('scroll');
  f.flush();
  assert.deepEqual(f.fallbacks, [[1, 1, '#123456', true]]);
});

test('semantic checks cover later candidates, hydrated custom topbars and scroll transitions', () => {
  const multiple = fixture({ themeColor: '#123456' });
  multiple.element('static', 'auto', 'header');
  const second = multiple.element('sticky', '0px', 'nav');
  second.rect = { top: 0, left: 0, width: 800, height: 56, right: 800, bottom: 56 };
  multiple.start();
  assert.deepEqual(multiple.fallbacks, [[1, 1, '#123456', true]]);

  const hydrated = fixture({ themeColor: '#234567' });
  hydrated.start();
  const topbar = hydrated.element('fixed', '0px', 'ytm-mobile-topbar-renderer');
  topbar.rect = { top: 0, left: 0, width: 800, height: 48, right: 800, bottom: 48 };
  const header = hydrated.element('static', 'auto', 'header');
  hydrated.body.children.splice(hydrated.body.children.indexOf(header), 1);
  topbar.append(header);
  hydrated.added(topbar);
  hydrated.flush();
  assert.deepEqual(hydrated.fallbacks, []);
  hydrated.scrollTo(80);
  hydrated.event('scroll');
  hydrated.flush();
  assert.deepEqual(hydrated.fallbacks, [[1, 1, '#234567', true]]);

  const scrolled = fixture({ themeColor: '#345678' });
  const changing = scrolled.element('static', 'auto', 'header');
  changing.rect = { top: 0, left: 0, width: 800, height: 64, right: 800, bottom: 64 };
  scrolled.start();
  assert.deepEqual(scrolled.fallbacks, []);
  Object.assign(changing.computed, { position: 'sticky', top: '0px' });
  scrolled.event('scroll');
  scrolled.flush();
  assert.deepEqual(scrolled.fallbacks, [[1, 1, '#345678', true]]);

  const promoted = fixture({ themeColor: '#456789', reparseStyles: true });
  const owned = promoted.element('fixed', '0px');
  owned.classes = ['owned-topbar'];
  owned.rect = { top: 0, left: 0, width: 800, height: 48, right: 800, bottom: 48 };
  const style = promoted.context.document.createElement('style');
  style.textContent = '.owned-topbar { top: 0px !important; }';
  promoted.context.document.documentElement.append(style);
  promoted.start();
  assert.deepEqual(promoted.fallbacks, []);
  owned.role = 'navigation';
  promoted.mutate(owned, 'role');
  promoted.flush();
  assert.deepEqual(promoted.fallbacks, []);
  promoted.scrollTo(80);
  promoted.event('scroll');
  promoted.flush();
  assert.deepEqual(promoted.fallbacks, [[1, 1, '#456789', true]]);

  const filled = fixture({ themeColor: '#56789a' });
  const earlyHeader = filled.element('static', 'auto', 'header');
  earlyHeader.rect = { top: 0, left: 0, width: 800, height: 0, right: 800, bottom: 0 };
  filled.start();
  assert.deepEqual(filled.fallbacks, []);
  Object.assign(earlyHeader.computed, { position: 'sticky', top: '0px' });
  earlyHeader.rect = { top: 0, left: 0, width: 800, height: 70, right: 800, bottom: 70 };
  filled.childAdded(earlyHeader, new filled.context.Element());
  filled.flush();
  assert.deepEqual(filled.fallbacks, [[1, 1, '#56789a', true]]);
});

test('top header falls back to its opaque computed background color', () => {
  const f = fixture();
  const header = f.element('sticky', '0px', 'nav');
  header.computed.backgroundColor = 'rgb(18, 52, 86)';
  header.rect = { top: 0, left: 0, width: 800, height: 56, right: 800, bottom: 56 };

  f.start();

  assert.deepEqual(f.fallbacks, [[1, 1, '#123456', true]]);
});

test('narrow sticky controls keep CSS protection without native fallback', () => {
  const f = fixture();
  const control = f.element('sticky', '0px');

  f.start();

  assert.deepEqual(f.fallbacks, []);
  assert.equal(f.computed(control).top, '32px');
});

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

test('Amazon India toolbar keeps its translated offscreen state without recurring discovery', () => {
  for (const hostname of ['amazon.in', 'www.amazon.in', 'www.amazon.in.']) {
    const f = fixture({ hostname, reparseStyles: true });
    const toolbar = f.element('sticky', '0px');
    toolbar.classes = ['s-mobile-toolbar-sticky'];
    toolbar.computed.transform = 'matrix(1, 0, 0, 1, 0, -96)';
    f.start(false);
    const layer = f.sheets.find((node) => node.textContent.includes('.s-mobile-toolbar-sticky'));
    assert.ok(layer?.isConnected, 'Toolbar states must be protected before delayed classification');
    const visibleTop = 'calc(0px + var(--candy-safe-area-inset-top))';
    assert.equal(f.computed(toolbar).top, visibleTop);
    toolbar.classes.push('s-mobile-toolbar-offscreen');
    assert.equal(f.computed(toolbar).top, '0px', 'A fully translated toolbar must not reveal one inset of content');
    assert.equal(f.computed(toolbar).transform, 'matrix(1, 0, 0, 1, 0, -96)', 'Keep the author hide transition');
    toolbar.classes.pop();
    assert.equal(f.computed(toolbar).top, visibleTop, 'Revealed toolbar regains its inset before any worker');
    f.flush();
    const before = { queries: f.reads.selector, rules: f.ruleWrites(), writes: f.writes() };
    for (let repeat = 0; repeat < 100; repeat++) {
      toolbar.classes.push('s-mobile-toolbar-offscreen');
      f.mutate(toolbar, 'class'); f.event('scroll');
      assert.equal(f.computed(toolbar).top, '0px');
      toolbar.classes.pop();
      f.mutate(toolbar, 'class'); f.event('scroll');
      assert.equal(f.computed(toolbar).top, visibleTop);
    }
    f.flush();
    assert.deepEqual({ queries: f.reads.selector, rules: f.ruleWrites(), writes: f.writes() }, before);
    assert.equal(toolbar.style.getPropertyValue('top'), '', 'State protection stays in persistent CSS');
    assert.deepEqual(f.fallbacks, []);
    f.configure({ enabled: false });
    assert.equal(layer.isConnected, false);
    assert.equal(f.computed(toolbar).top, '0px');
    assert.equal(toolbar.attributes.size, 0, 'Disabling restores all owned toolbar markers');
  }
});

test('Amazon India toolbar selectors remain restricted to the actual host', () => {
  for (const hostname of ['amazon.in.example.org', 'notamazon.in', 'amazon.com', 'example.org']) {
    const f = fixture({ hostname, reparseStyles: true });
    const toolbar = f.element('sticky', '0px');
    toolbar.classes = ['s-mobile-toolbar-sticky', 's-mobile-toolbar-offscreen'];
    f.start(false);
    assert.equal(f.sheets.some((node) => node.textContent.includes('.s-mobile-toolbar-sticky')), false);
    assert.equal(f.computed(toolbar).top, '0px');
  }
});

test('Amazon India visible and hidden toolbar rules preserve negative author tops', () => {
  const f = fixture({ hostname: 'www.amazon.in', reparseStyles: true });
  const toolbar = f.element('sticky', '-96px');
  toolbar.classes = ['s-mobile-toolbar-sticky'];
  f.start();
  assert.equal(f.computed(toolbar).top, '-96px');
  toolbar.classes.push('s-mobile-toolbar-offscreen');
  f.mutate(toolbar, 'class'); f.flush();
  assert.equal(f.computed(toolbar).top, '-96px', 'Offscreen top reset must also exclude authored negative anchors');
  toolbar.classes.pop();
  f.mutate(toolbar, 'class'); f.flush();
  assert.equal(f.computed(toolbar).top, '-96px');
  toolbar.computed.top = '0px';
  toolbar.style.setProperty('top', '0px'); f.flush();
  assert.equal(f.computed(toolbar).top, 'calc(0px + var(--candy-safe-area-inset-top))');
  toolbar.classes.push('s-mobile-toolbar-offscreen');
  assert.equal(f.computed(toolbar).top, '0px');
  f.configure({ enabled: false });
  assert.equal(toolbar.style.getPropertyValue('top'), '0px');
  assert.equal(toolbar.style.getPropertyPriority('top'), '');
  assert.equal(toolbar.attributes.size, 0);
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

test('fullscreen video roots and their controls never acquire header top offsets', () => {
  const f = fixture();
  const player = f.element('fixed', '0px');
  const control = player.append(f.element('fixed', '8px'));
  const pageControl = f.element('fixed', '8px');
  f.context.document.fullscreenElement = player;
  f.start();
  assert.equal(f.computed(player).top, '0px');
  assert.equal(f.computed(control).top, '8px');
  assert.equal(f.computed(pageControl).top, '40px', 'Unrelated fixed content stays protected');
  player.computed.position = 'relative';
  f.context.document.fullscreenElement = null;
  f.event('fullscreenchange'); f.flush();
  assert.equal(f.computed(player).top, '0px', 'Inline player cannot retain a fullscreen header rule');
});

test('fullscreen releases only owned top rules and preserves newer author inline top', () => {
  const f = fixture();
  const player = f.element('fixed', '0px');
  f.start();
  assert.equal(f.computed(player).top, '32px');
  player.style.setProperty('top', '7px');
  f.context.document.fullscreenElement = player;
  f.event('fullscreenchange'); f.flush();
  assert.equal(f.computed(player).top, '7px');
  assert.equal(player.style.getPropertyValue('top'), '7px');
  player.computed.position = 'relative';
  f.context.document.fullscreenElement = null;
  f.event('fullscreenchange'); f.flush();
  assert.equal(f.computed(player).top, '7px');
});

test('still-fixed surfaces regain one original inset after repeated fullscreen transitions', () => {
  const f = fixture();
  const player = f.element('fixed', '8px');
  f.start();
  for (let transition = 0; transition < 4; transition++) {
    assert.equal(f.computed(player).top, '40px');
    f.context.document.fullscreenElement = player;
    f.event('fullscreenchange'); f.flush();
    assert.equal(f.computed(player).top, '8px');
    f.context.document.fullscreenElement = null;
    f.event('fullscreenchange'); f.flush();
  }
  assert.equal(f.computed(player).top, '40px');
});

test('persistent body and finite fixed/sticky rules do not accumulate on authorized rechecks', () => {
  const f = fixture(); f.body.style.setProperty('padding-top', '4px');
  f.config.addInsetToNegativeTop = true;
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

test('negative fixed and sticky tops stay authored by default and opt in without accumulating', () => {
  const f = fixture();
  const fixed = f.element('fixed', '-64px');
  const sticky = f.element('sticky', '-0.01px');
  const zero = f.element('fixed', '0px');
  f.sheet([{ selector: '#negative', declarations: { position: 'fixed', top: '-80px' } }]);
  const selector = f.element('static', 'auto'); selector.id = 'negative';
  f.start();
  assert.equal(f.computed(fixed).top, '-64px');
  assert.equal(f.computed(sticky).top, '-0.01px');
  assert.equal(f.computed(selector).top, '-80px');
  assert.equal(f.computed(zero).top, '32px');
  for (let repeat = 0; repeat < 3; repeat++) {
    f.configure({ addInsetToNegativeTop: true });
    assert.equal(f.computed(fixed).top, '-32px');
    assert.equal(f.computed(sticky).top, '31.99px');
    assert.equal(f.computed(selector).top, '-48px');
    f.configure({ addInsetToNegativeTop: false });
    assert.equal(f.computed(fixed).top, '-64px');
    assert.equal(f.computed(sticky).top, '-0.01px');
    assert.equal(f.computed(selector).top, '-80px');
  }
  f.configure({ addInsetToNegativeTop: 'true' });
  assert.equal(f.computed(fixed).top, '-64px', 'Only the explicit boolean opts in');
  f.configure({ enabled: false });
  assert.equal(fixed.attributes.size, 0, 'Owned negative markers are removed on disable');
});

test('passive inline negative tops escape retained element and selector rules before paint', () => {
  const f = fixture();
  const fixed = f.element('fixed', '0px');
  f.sheet([{ selector: '#selected', declarations: { position: 'sticky', top: '0px' } }]);
  const selected = f.element('static', 'auto'); selected.id = 'selected';
  f.start();
  assert.equal(f.computed(fixed).top, '32px');
  assert.equal(f.computed(selected).top, '32px');
  f.event('scroll');
  fixed.style.setProperty('top', '-64px');
  selected.style.setProperty('top', '-80px');
  f.flush();
  assert.equal(f.computed(fixed).top, '-64px');
  assert.equal(f.computed(selected).top, '-80px');
  assert.equal(fixed.style.getPropertyPriority('top'), '');
  const before = { reads: { ...f.reads }, rules: f.ruleWrites() };
  for (let repeat = 0; repeat < 100; repeat++) {
    f.mutate(selected, 'style'); f.event('scroll');
  }
  f.flush();
  assert.deepEqual({ reads: { ...f.reads }, rules: f.ruleWrites() }, before);
  fixed.style.setProperty('top', '0px'); selected.style.removeProperty('top'); f.flush();
  assert.equal(f.computed(fixed).top, '32px');
  assert.equal(f.computed(selected).top, '32px');
  f.configure({ enabled: false });
  assert.equal(fixed.attributes.size, 0);
  assert.equal(selected.attributes.size, 0);
});

test('split negative class and ancestor states escape positive selectors outside interaction gate', () => {
  const f = fixture();
  f.sheet([{ selector: '#selected', declarations: { position: 'fixed', top: '0px' } },
    { selector: '#selected.hidden', declarations: { top: '-64px' } },
    { selector: '.hidden', declarations: { top: '-80px' } }]);
  const selected = f.element('static', 'auto'); selected.id = 'selected';
  f.start();
  assert.equal(f.computed(selected).top, '32px');
  f.event('scroll'); selected.classes = ['hidden']; f.mutate(selected, 'class'); f.flush();
  assert.equal(f.computed(selected).top, '-80px');
  selected.classes = []; f.mutate(selected, 'class'); f.flush();
  assert.equal(f.computed(selected).top, '32px');
  selected.computed.top = '-64px';
  // Simulate an author ancestor rule changing the resolved top of a cached target.
  const sheet = f.context.document.styleSheets[0];
  sheet.cssRules[0].style.setProperty('top', '-64px');
  f.body.classes = ['hide-header']; f.mutate(f.body, 'class'); f.flush();
  assert.equal(f.computed(selected).top, '-64px');
});

test('negative selector declarations supersede captured positive tops and late nodes stay hidden', () => {
  const f = fixture();
  f.sheet([{ selector: '#same', declarations: { position: 'fixed', top: '0px' } }]);
  f.sheet([{ selector: '#same', declarations: { position: 'fixed', top: '-64px' } }]);
  f.sheet([{ selector: '#late', declarations: { position: 'fixed', top: '0px' } },
    { selector: '#late.hidden', declarations: { top: '-80px' } }]);
  f.start();
  const same = f.element('static', 'auto'); same.id = 'same'; f.added(same);
  const late = f.element('static', 'auto'); late.id = 'late'; late.classes = ['hidden']; f.added(late);
  f.flush();
  assert.equal(f.computed(same).top, '-64px');
  assert.equal(f.computed(late).top, '-80px');
  f.configure({ addInsetToNegativeTop: true });
  assert.equal(f.computed(same).top, '-32px');
});

test('removing a positive inline top can expose a negative author stylesheet top', () => {
  const f = fixture();
  f.sheet([{ selector: '#hidden', declarations: { top: '-64px' } }]);
  const hidden = f.element('fixed', '0px'); hidden.id = 'hidden'; hidden.style.setProperty('top', '0px');
  f.start();
  assert.equal(f.computed(hidden).top, '32px');
  hidden.style.removeProperty('top'); f.flush();
  assert.equal(f.computed(hidden).top, '-64px');
});

test('negative selector exclusions retain comma-list specificity and precede pseudo-elements', () => {
  const f = fixture();
  f.sheet([{ selector: '#one, .two', declarations: { position: 'fixed', top: '0px' } },
    { selector: '#pseudo::before', declarations: { position: 'fixed', top: '8px' } }]);
  f.start();
  const injected = f.sheets.filter((element) => element.isConnected && element.parentElement?.localName === 'html')
    .flatMap((element) => element.sheet.cssRules).map((rule) => rule.selectorText);
  assert.ok(injected.some((selector) => /^#one(?::not\(:where\(\[[^\]]+\]\)\)){2}, \.two(?::not\(:where\(\[[^\]]+\]\)\)){2}$/.test(selector)));
  assert.ok(injected.some((selector) => /^#pseudo(?::not\(:where\(\[[^\]]+\]\)\)){2}::before$/.test(selector)));
});

test('default negative policy does no repeated author reads for unchanged selector targets', () => {
  const f = fixture();
  f.sheet([{ selector: '#stable', declarations: { position: 'fixed', top: '0px' } }]);
  const stable = f.element('static', 'auto'); stable.id = 'stable';
  f.start();
  f.event('click'); f.mutate(stable, 'class'); f.flush();
  const before = { style: f.reads.style, rect: f.reads.rect, rules: f.ruleWrites() };
  for (let repeat = 0; repeat < 100; repeat++) {
    f.event('click'); f.mutate(stable, 'class'); f.mutate(stable, 'style'); f.flush();
  }
  assert.deepEqual({ style: f.reads.style, rect: f.reads.rect, rules: f.ruleWrites() }, before);
  assert.equal(f.computed(stable).top, '32px');
});

test('bottom-anchored fixed navigation keeps its resolved top and bottom', () => {
  const f = fixture();
  const navigation = f.element('fixed', '752px', 'ytm-pivot-bar-renderer');
  navigation.computed.bottom = '0px';
  const tallPanel = f.element('fixed', '100px');
  tallPanel.computed.bottom = '0px';
  f.start();

  assert.equal(f.computed(navigation).top, '752px');
  assert.equal(f.computed(navigation).bottom, '0px');
  assert.equal(navigation.style.getPropertyValue('top'), '');
  assert.equal(f.computed(tallPanel).top, '100px');
});

test('full-viewport fixed inset modal gains only a top inset at initial load', () => {
  const f = fixture();
  const modal = f.element('fixed', '0px');
  modal.setAttribute('role', 'dialog');
  modal.computed.bottom = '0px';
  modal.rect = { top: 0, left: 0, width: 800, height: 800, right: 800, bottom: 800 };
  const narrow = f.element('fixed', '0px');
  narrow.computed.bottom = '0px';
  narrow.rect = { top: 0, left: 0, width: 400, height: 800, right: 400, bottom: 800 };

  f.start();

  assert.equal(f.computed(modal).top, '32px');
  assert.equal(f.computed(modal).bottom, '0px');
  assert.equal(modal.style.getPropertyValue('top'), '');
  assert.equal(f.computed(narrow).top, '0px', 'Partial-width fixed panels keep their geometry');
});

test('automatic inline modal insertion bypasses the click discovery gate', () => {
  const f = fixture();
  f.start();
  const modal = f.context.document.createElement('div');
  modal.setAttribute('role', 'dialog');
  modal.computed = { position: 'fixed', top: '0px', bottom: '0px' };
  modal.rect = { top: 0, left: 0, width: 800, height: 800, right: 800, bottom: 800 };
  modal.style.setProperty('inset', '0px');
  f.body.append(modal);
  f.added(modal);
  f.flush();

  assert.equal(f.computed(modal).top, '32px');
  assert.equal(f.computed(modal).bottom, '0px');
});

test('clicked class modal and automatic interstitial both receive the top inset', () => {
  const f = fixture();
  f.start();
  const clicked = f.context.document.createElement('div');
  clicked.setAttribute('role', 'dialog');
  clicked.computed = { position: 'fixed', top: '0px', bottom: '0px' };
  clicked.rect = { top: 0, left: 0, width: 800, height: 800, right: 800, bottom: 800 };
  f.event('click');
  f.body.append(clicked);
  f.added(clicked);
  f.flush();
  assert.equal(f.computed(clicked).top, '32px');

  const interstitial = f.context.document.createElement('div');
  interstitial.className = 'interstitial-ad';
  interstitial.computed = { position: 'fixed', top: '0px', bottom: '0px' };
  interstitial.rect = { top: 0, left: 0, width: 800, height: 800, right: 800, bottom: 800 };
  f.body.append(interstitial);
  f.added(interstitial);
  f.flush();
  assert.equal(f.computed(interstitial).top, '32px');
});

test('inset stylesheet selector leaves a narrow drawer and checks modal geometry', () => {
  const f = fixture();
  f.sheet([{ selector: '.full-screen', declarations: { position: 'fixed', inset: '0px' } }]);
  f.start();
  const drawer = f.element('fixed', '0px');
  drawer.classes = ['full-screen'];
  drawer.className = 'full-screen';
  drawer.computed.bottom = '0px';
  drawer.rect = { top: 0, left: 0, width: 320, height: 800, right: 320, bottom: 800 };
  f.added(drawer);
  const modal = f.element('fixed', '0px');
  modal.classes = ['full-screen', 'modal'];
  modal.className = 'full-screen modal';
  modal.computed.bottom = '0px';
  modal.rect = { top: 0, left: 0, width: 800, height: 800, right: 800, bottom: 800 };
  f.added(modal);
  f.flush();

  assert.equal(f.computed(drawer).top, '0px');
  assert.equal(f.computed(modal).top, '32px');
});

test('fullscreen scrim stays behind the safe area while dialog surface moves', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover' });
  f.body.style.setProperty('padding-top', 'env(safe-area-inset-top)');
  f.start();
  const scrim = f.context.document.createElement('div');
  scrim.className = 'modal-backdrop';
  scrim.computed = { position: 'fixed', top: '0px', bottom: '0px' };
  scrim.rect = { top: 0, left: 0, width: 800, height: 800, right: 800, bottom: 800 };
  scrim.style.setProperty('inset', '0px');
  const dialog = f.context.document.createElement('div');
  dialog.setAttribute('role', 'dialog');
  dialog.computed = { position: 'fixed', top: '0px', bottom: '0px' };
  dialog.rect = { top: 0, left: 0, width: 800, height: 800, right: 800, bottom: 800 };
  dialog.style.setProperty('inset', '0px');
  f.body.append(scrim);
  f.body.append(dialog);
  f.dispatchMutations([{ type: 'childList', target: f.body,
    addedNodes: [scrim, dialog], removedNodes: [] }]);
  f.flush();

  assert.equal(f.computed(scrim).top, '0px');
  assert.equal(f.computed(dialog).top, '32px');
});

test('generic backdrop wrapper with safe child retains full viewport coverage', () => {
  const f = fixture();
  const overlay = f.element('fixed', '0px');
  overlay.className = 'page-overlay';
  overlay.computed.bottom = '0px';
  overlay.rect = { top: 0, left: 0, width: 800, height: 800, right: 800, bottom: 800 };
  const dialog = f.context.document.createElement('div');
  dialog.rect = { top: 32, left: 100, width: 600, height: 300, right: 700, bottom: 332 };
  overlay.append(dialog);

  f.start();

  assert.equal(f.computed(overlay).top, '0px');
});

test('ReactModal fullscreen content gains padding while its overlay covers the status bar', () => {
  const f = fixture();
  f.start();
  const portal = f.context.document.createElement('div');
  const overlay = f.context.document.createElement('div');
  overlay.className = 'ReactModal__Overlay';
  overlay.computed = { position: 'fixed', top: '0px', bottom: '0px' };
  overlay.rect = { top: 0, left: 0, width: 800, height: 800, right: 800, bottom: 800 };
  const content = f.context.document.createElement('div');
  content.className = 'ReactModal__Content';
  content.setAttribute('role', 'dialog');
  content.computed = { position: 'absolute', top: '50%', paddingTop: '20px', boxSizing: 'border-box' };
  content.rect = { top: 0, left: 0, width: 800, height: 800, right: 800, bottom: 800 };
  const header = f.context.document.createElement('header');
  header.rect = { top: 20, left: 0, width: 800, height: 48, right: 800, bottom: 68 };
  const close = f.context.document.createElement('button');
  close.computed = { position: 'absolute', top: '16px' };
  close.rect = { top: 16, left: 752, width: 32, height: 32, right: 784, bottom: 48 };
  header.append(close);
  content.append(header);
  overlay.append(content);
  portal.append(overlay);
  f.body.append(portal);
  f.added(portal);
  f.flush();

  assert.equal(f.computed(overlay).top, '0px');
  assert.equal(f.computed(overlay).bottom, '0px');
  assert.equal(f.computed(content).paddingTop, '32px');
  assert.equal(f.computed(close).top, '32px', 'Absolute close button clears the status bar');
  assert.equal(content.style.getPropertyValue('padding-top'), '');

  content.className = 'sheet';
  content.removeAttribute('role');
  f.mutate(content, 'role');
  f.flush();
  assert.equal(f.computed(content).paddingTop, '20px', 'Content without dialog semantics releases padding');
  assert.equal(f.computed(close).top, '16px');

  content.setAttribute('aria-modal', 'true');
  f.mutate(content, 'aria-modal');
  f.flush();
  assert.equal(f.computed(content).paddingTop, '32px', 'Late dialog semantics restore padding');
  assert.equal(f.computed(close).top, '32px');

  content.rect = { top: 100, left: 100, width: 600, height: 500, right: 700, bottom: 600 };
  f.mutate(content, 'class');
  f.flush();
  assert.equal(f.computed(content).paddingTop, '20px', 'A smaller dialog releases full-screen padding');
  assert.equal(f.computed(close).top, '16px');
});

test('evicting an old tracked overlay also releases its content padding', () => {
  const f = fixture();
  f.start();
  const contents = [];
  for (let index = 0; index < 9; index++) {
    const overlay = f.context.document.createElement('div');
    overlay.className = 'ReactModal__Overlay';
    overlay.computed = { position: 'fixed', top: '0px', bottom: '0px' };
    overlay.rect = { top: 0, left: 0, width: 800, height: 800, right: 800, bottom: 800 };
    const content = f.context.document.createElement('div');
    content.setAttribute('role', 'dialog');
    content.computed = { position: 'absolute', top: '50%', paddingTop: '0px', boxSizing: 'border-box' };
    content.rect = { top: 0, left: 0, width: 800, height: 800, right: 800, bottom: 800 };
    const header = f.context.document.createElement('header');
    header.rect = { top: 0, left: 0, width: 800, height: 48, right: 800, bottom: 48 };
    content.append(header);
    overlay.append(content);
    f.body.append(overlay);
    f.added(overlay);
    f.flush();
    contents.push(content);
  }

  assert.equal(f.computed(contents[0]).paddingTop, '0px');
  assert.equal(f.computed(contents[8]).paddingTop, '32px');
});

test('cover page activates for a late ReactModal portal and clears when it closes', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover' });
  f.body.style.setProperty('padding-top', 'env(safe-area-inset-top)');
  const main = f.element('static', 'auto', 'main');
  main.rect = { top: 32, left: 0, width: 800, height: 400, right: 800, bottom: 432 };
  f.start();
  assert.equal(f.diagnostics().active, false);

  const portal = f.context.document.createElement('div');
  const overlay = f.context.document.createElement('div');
  overlay.className = 'ReactModal__Overlay';
  overlay.computed = { position: 'fixed', top: '0px', bottom: '0px' };
  overlay.rect = { top: 0, left: 0, width: 800, height: 800, right: 800, bottom: 800 };
  const content = f.context.document.createElement('div');
  content.className = 'ReactModal__Content';
  content.setAttribute('role', 'dialog');
  content.computed = { position: 'absolute', top: '50%', paddingTop: '0px', boxSizing: 'border-box' };
  content.rect = { top: 0, left: 0, width: 800, height: 800, right: 800, bottom: 800 };
  const header = f.context.document.createElement('header');
  header.rect = { top: 0, left: 0, width: 800, height: 48, right: 800, bottom: 48 };
  content.append(header);
  overlay.append(content);
  portal.append(overlay);
  f.body.append(portal);
  f.added(portal);
  f.flush();

  assert.equal(f.diagnostics().active, true);
  assert.equal(f.computed(overlay).top, '0px');
  assert.equal(f.computed(content).paddingTop, '32px');

  f.removed(overlay);
  f.flush();
  assert.equal(f.diagnostics().active, false);
  assert.equal(f.computed(content).paddingTop, '0px');
});

test('modal candidate discovery stays bounded and prioritizes latest inserted overlay', () => {
  const f = fixture();
  f.start();
  let inlineReads = 0;
  const records = Array.from({ length: 31 }, () => ({ type: 'childList', target: f.body,
    addedNodes: Array.from({ length: 8 }, () => {
      const node = f.context.document.createElement('div');
      const getAttribute = node.getAttribute.bind(node);
      node.getAttribute = (name) => { if (name === 'style') inlineReads++; return getAttribute(name); };
      f.body.append(node);
      return node;
    }), removedNodes: [] }));
  const modal = f.context.document.createElement('div');
  modal.setAttribute('role', 'dialog');
  modal.computed = { position: 'fixed', top: '0px', bottom: '0px' };
  modal.rect = { top: 0, left: 0, width: 800, height: 800, right: 800, bottom: 800 };
  f.body.append(modal);
  records.push({ type: 'childList', target: f.body, addedNodes: [modal], removedNodes: [] });
  f.dispatchMutations(records);
  f.flush();

  assert.equal(f.computed(modal).top, '32px');
  assert.ok(inlineReads <= 16, `Candidate prefilter inspected ${inlineReads} noise nodes`);
  const before = { style: f.reads.style, rect: f.reads.rect };
  for (let index = 0; index < 100; index++) {
    f.dispatchMutations([{ type: 'attributes', target: modal, attributeName: 'class', oldValue: null }]);
  }
  for (let index = 0; index < 100; index++) f.event('scroll');
  f.flush();
  assert.deepEqual({ style: f.reads.style, rect: f.reads.rect }, before);
});

test('known full-screen modal releases its top rule when it becomes a narrow drawer', () => {
  const f = fixture();
  const modal = f.element('fixed', '0px');
  modal.setAttribute('role', 'dialog');
  modal.computed.bottom = '0px';
  modal.rect = { top: 0, left: 0, width: 800, height: 800, right: 800, bottom: 800 };
  f.start();
  assert.equal(f.computed(modal).top, '32px');

  modal.rect = { top: 0, left: 0, width: 320, height: 800, right: 320, bottom: 800 };
  f.mutate(modal, 'class');
  f.flush();
  assert.equal(f.computed(modal).top, '0px');

  modal.rect = { top: 0, left: 0, width: 800, height: 800, right: 800, bottom: 800 };
  f.mutate(modal, 'class');
  f.flush();
  assert.equal(f.computed(modal).top, '32px');
});

test('cover page releases modal protection when the same node is hidden', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover' });
  f.body.style.setProperty('padding-top', 'env(safe-area-inset-top)');
  const main = f.element('static', 'auto', 'main');
  main.rect = { top: 32, left: 0, width: 800, height: 400, right: 800, bottom: 432 };
  f.start();
  assert.equal(f.diagnostics().active, false);

  const modal = f.context.document.createElement('div');
  modal.setAttribute('role', 'dialog');
  modal.computed = { position: 'fixed', top: '0px', bottom: '0px' };
  modal.rect = { top: 0, left: 0, width: 800, height: 800, right: 800, bottom: 800 };
  f.body.append(modal);
  f.added(modal);
  f.flush();
  assert.equal(f.computed(modal).top, '32px');

  modal.computed.display = 'none';
  modal.rect = { top: 0, left: 0, width: 0, height: 0, right: 0, bottom: 0 };
  f.mutate(modal, 'class');
  f.flush();
  assert.equal(f.diagnostics().active, false);
  assert.equal(f.computed(modal).top, '0px');

  modal.computed.display = 'block';
  modal.rect = { top: 0, left: 0, width: 800, height: 800, right: 800, bottom: 800 };
  f.mutate(modal, 'class');
  f.flush();
  assert.equal(f.diagnostics().active, true);
  assert.equal(f.computed(modal).top, '32px');
});

test('cover page shifts an unsafe full-screen modal and restores after removal', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover' });
  f.body.style.setProperty('padding-top', 'env(safe-area-inset-top)');
  const main = f.element('static', 'auto', 'main');
  main.content = 'Safe page content';
  main.rect = { top: 32, left: 0, width: 800, height: 400, right: 800, bottom: 432 };
  f.start();
  assert.equal(f.diagnostics().active, false);

  const modal = f.context.document.createElement('div');
  modal.computed = { position: 'fixed', top: '0px', bottom: '0px' };
  modal.rect = { top: 0, left: 0, width: 800, height: 800, right: 800, bottom: 800 };
  modal.style.setProperty('inset', '0px');
  const content = f.context.document.createElement('div');
  content.rect = { top: 0, left: 0, width: 800, height: 60, right: 800, bottom: 60 };
  modal.append(content);
  f.body.append(modal);
  f.added(modal);
  f.flush();
  assert.equal(f.diagnostics().active, true);
  assert.equal(f.computed(modal).top, '32px');

  f.removed(modal);
  f.flush();
  assert.equal(f.diagnostics().active, false);
});

test('cover page leaves a full-screen modal with author safe-area padding in place', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover' });
  f.body.style.setProperty('padding-top', 'env(safe-area-inset-top)');
  const modal = f.element('fixed', '0px');
  modal.computed.bottom = '0px';
  modal.style.setProperty('padding-top', 'env(safe-area-inset-top)');
  modal.rect = { top: 0, left: 0, width: 800, height: 800, right: 800, bottom: 800 };
  const content = f.context.document.createElement('div');
  content.rect = { top: 32, left: 0, width: 800, height: 60, right: 800, bottom: 92 };
  modal.append(content);

  f.start();

  assert.equal(f.diagnostics().active, false);
  assert.equal(f.computed(modal).top, '0px');
  assert.equal(f.computed(modal).paddingTop, '32px');
});

test('cover page finds a replacement portal modal in the same mutation batch', () => {
  const f = fixture({ viewportContent: 'viewport-fit=cover' });
  f.body.style.setProperty('padding-top', 'env(safe-area-inset-top)');
  for (let index = 0; index < 70; index++) f.element('static', 'auto');
  const portal = f.element('static', 'auto');
  for (let index = 0; index < 10; index++) f.element('static', 'auto');
  const oldModal = f.element('fixed', '0px');
  oldModal.setAttribute('role', 'dialog');
  oldModal.computed.bottom = '0px';
  oldModal.rect = { top: 0, left: 0, width: 800, height: 800, right: 800, bottom: 800 };
  f.start();
  assert.equal(f.diagnostics().active, true);

  const wrapper = f.context.document.createElement('div');
  wrapper.append(f.context.document.createElement('div'));
  wrapper.append(f.context.document.createElement('div'));
  const replacement = f.context.document.createElement('div');
  replacement.setAttribute('role', 'dialog');
  replacement.computed = { position: 'fixed', top: '0px', bottom: '0px' };
  replacement.rect = { top: 0, left: 0, width: 800, height: 800, right: 800, bottom: 800 };
  wrapper.append(replacement);
  oldModal.remove();
  portal.append(wrapper);
  f.dispatchMutations([
    { type: 'childList', target: f.body, addedNodes: [], removedNodes: [oldModal] },
    { type: 'childList', target: portal, addedNodes: [wrapper], removedNodes: [] },
  ]);
  f.flush();

  assert.equal(f.diagnostics().active, true);
  assert.equal(f.computed(replacement).top, '32px');
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

test('known headers follow positive author tops without interaction or a policy reset', () => {
  const f = fixture();
  const header = f.element('fixed', '64px', 'header');
  header.rect = { top: 64, left: 0, width: 200, height: 40, right: 200, bottom: 104 };
  header.style.setProperty('top', '64px');
  f.start();
  assert.equal(f.computed(header).top, '96px');
  const layer = f.sheets.find((node) => node.sheet.cssRules.some((rule) => rule.style.getPropertyValue('top')));
  const queries = f.reads.selector;
  header.style.setProperty('top', '0px');
  f.flush();
  assert.equal(f.computed(header).top, '32px');
  assert.equal(header.style.getPropertyValue('top'), '0px');
  assert.equal(layer.isConnected, true, 'Local correction retains the existing stylesheet');
  assert.equal(f.reads.selector, queries, 'Known header updates do not query the document');
  assert.deepEqual(f.fallbacks, [], 'Local top changes do not switch the native policy');

  const settled = { reads: { ...f.reads }, rules: f.ruleWrites() };
  for (let index = 0; index < 100; index++) header.style.setProperty('top', '0px');
  f.flush();
  assert.deepEqual({ reads: { ...f.reads }, rules: f.ruleWrites() }, settled,
    'Repeated identical author states do not read styles or write rules');
  header.style.setProperty('top', '16px');
  f.flush();
  assert.equal(f.computed(header).top, '48px');
  f.configure({ enabled: false });
  assert.equal(f.computed(header).top, '16px');
  assert.equal(header.attributes.size, 0);
});

test('ancestor changes are coalesced and survive cancellation by scrolling', () => {
  const f = fixture();
  const parent = f.element('static', 'auto');
  const header = f.element('fixed', '64px', 'header');
  parent.append(header);
  header.rect = { top: 64, left: 0, width: 200, height: 40, right: 200, bottom: 104 };
  f.start();
  header.computed.top = '0px';
  parent.classes = ['compact'];
  const before = { ...f.reads };
  f.mutate(parent, 'class');
  assert.deepEqual(f.reads, before, 'Mutation collection never measures layout');
  f.event('scroll');
  f.flush();
  assert.equal(f.computed(header).top, '32px');
  assert.equal(f.reads.selector, before.selector, 'Scroll preserves only cached header work');
  header.computed.top = '-64px';
  parent.classes = ['hidden'];
  f.mutate(parent, 'class');
  f.flush();
  assert.equal(f.computed(header).top, '-64px');
  header.computed.top = '0px';
  parent.classes = ['compact'];
  f.mutate(parent, 'class');
  f.flush();
  assert.equal(f.computed(header).top, '32px');
});

test('tracked header corrections supersede selector clones and later source updates', () => {
  const f = fixture();
  const style = f.sheet([{ selector: '#tracked', declarations: { position: 'fixed', top: '64px' } }]);
  const header = f.element('static', 'auto', 'header'); header.id = 'tracked';
  header.rect = { top: 64, left: 0, width: 200, height: 40, right: 200, bottom: 104 };
  f.start();
  assert.equal(f.computed(header).top, '96px');
  header.style.setProperty('top', '0px');
  f.flush();
  assert.equal(f.computed(header).top, '32px');
  style.sheet.cssRules[0].style.setProperty('top', '80px');
  f.textChanged(style);
  f.flush();
  assert.equal(f.computed(header).top, '32px', 'A later selector build cannot override the local header rule');
  header.style.removeProperty('top');
  f.flush();
  assert.equal(f.computed(header).top, '112px');
  f.configure({ enabled: false });
  assert.equal(f.computed(header).top, '80px');
  assert.equal(header.attributes.size, 0);
});

test('header replacement releases a full cache and pending work stops on policy replacement', () => {
  const f = fixture();
  const headers = Array.from({ length: 8 }, () => f.element('fixed', '64px', 'header'));
  for (const header of headers) header.rect = { top: 64, left: 0, width: 200, height: 40, right: 200, bottom: 104 };
  f.start();
  for (const header of headers) f.removed(header);
  const replacement = f.element('fixed', '64px', 'header');
  replacement.rect = { top: 64, left: 0, width: 200, height: 40, right: 200, bottom: 104 };
  f.added(replacement); f.flush();
  replacement.style.setProperty('top', '0px');
  f.flush();
  assert.equal(f.computed(replacement).top, '32px');
  replacement.style.setProperty('top', '16px');
  f.configure({ enabled: false, navigationGeneration: 2 });
  assert.equal(f.computed(replacement).top, '16px');
  assert.equal(f.timers.size, 0);
  assert.ok(headers.every((header) => header.attributes.size === 0));
});

test('empty boolean hidden attributes are distinct from absent header attributes', () => {
  const f = fixture();
  const header = f.element('fixed', '64px', 'header');
  header.rect = { top: 64, left: 0, width: 200, height: 40, right: 200, bottom: 104 };
  f.start();
  header.setAttribute('hidden', ''); header.computed.top = '-64px';
  f.mutate(header, 'hidden'); f.flush();
  assert.equal(f.computed(header).top, '-64px');
  header.removeAttribute('hidden'); header.computed.top = '0px';
  f.mutate(header, 'hidden'); f.flush();
  assert.equal(f.computed(header).top, '32px');
});

test('stylesheet load and viewport changes recheck cached headers without network polling', () => {
  const f = fixture();
  const header = f.element('fixed', '64px', 'header');
  header.rect = { top: 64, left: 0, width: 200, height: 40, right: 200, bottom: 104 };
  f.start();
  const queries = f.reads.selector;
  header.computed.top = '0px';
  const link = f.context.document.createElement('link');
  link.setAttribute('rel', 'stylesheet');
  f.event('load', 'document', link);
  f.flush();
  assert.equal(f.computed(header).top, '32px');
  header.computed.top = '16px';
  f.event('resize');
  f.flush();
  assert.equal(f.computed(header).top, '48px');
  assert.equal(f.reads.selector, queries);

  const unrelated = f.element('static', 'auto');
  const before = { reads: { ...f.reads }, rules: f.ruleWrites() };
  for (let index = 0; index < 100; index++) {
    f.textChanged(unrelated);
    f.event('load', 'document', f.context.document.createElement('img'));
  }
  f.flush();
  assert.deepEqual({ reads: { ...f.reads }, rules: f.ruleWrites() }, before);
});

test('inserted and removed stylesheets refresh existing per-element header rules', () => {
  const f = fixture();
  const header = f.element('fixed', '64px', 'header');
  header.rect = { top: 64, left: 0, width: 200, height: 40, right: 200, bottom: 104 };
  f.start();
  const style = f.sheet([{ selector: 'header', declarations: { top: '0px' } }]);
  f.added(style); f.flush();
  assert.equal(f.computed(header).top, '32px');
  f.removed(style); f.flush();
  assert.equal(f.computed(header).top, '96px');
});

test('header mutation bursts have one worker, bounded reads and no idle polling', () => {
  const f = fixture();
  const header = f.element('fixed', '64px', 'header');
  header.rect = { top: 64, left: 0, width: 200, height: 40, right: 200, bottom: 104 };
  f.start();
  const before = { ...f.reads };
  const rules = f.ruleWrites();
  for (let index = 0; index < 100; index++) header.style.setProperty('top', `${index}px`);
  f.step();
  assert.deepEqual(f.reads, before, 'A burst only queues the current header state');
  assert.equal(f.timers.size, 1);
  f.flush();
  assert.equal(f.computed(header).top, '131px');
  assert.equal(f.reads.style, before.style + 1);
  assert.equal(f.ruleWrites(), rules + 1);

  const lastCheck = f.now();
  f.advance(10); header.style.setProperty('top', '0px'); f.step();
  f.advance(10); header.style.setProperty('top', '16px'); f.step();
  assert.equal(f.timers.size, 1);
  assert.equal([...f.timers.values()][0].at, lastCheck + 50, 'Later events cannot postpone the scheduled check');
  f.flush();
  assert.equal(f.computed(header).top, '48px');
  const idle = { reads: { ...f.reads }, rules: f.ruleWrites() };
  f.advance(10000); f.flush();
  assert.equal(f.timers.size, 0);
  assert.deepEqual({ reads: { ...f.reads }, rules: f.ruleWrites() }, idle);
});

test('pending header refresh does not delay an immediate semantic check', () => {
  const f = fixture();
  const header = f.element('fixed', '64px', 'header');
  header.rect = { top: 64, left: 0, width: 200, height: 40, right: 200, bottom: 104 };
  f.start();
  header.style.setProperty('top', '0px');
  f.flush();

  const lastCheck = f.now();
  header.style.setProperty('top', '16px');
  f.step();
  assert.equal([...f.timers.values()][0].at, lastCheck + 50);

  const addedHeader = f.element('fixed', '0px', 'header');
  f.added(addedHeader);
  assert.equal([...f.timers.values()][0].at, f.now(),
    'A new semantic header must run now while the cached header remains rate-limited');
  f.flush();
  assert.equal(f.computed(header).top, '48px');
  assert.equal(f.computed(addedHeader).top, '32px');
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
  assert.equal(streamed.computed(nav).top, '32px');
  assert.equal(streamed.reads.selector, 2);
  streamed.event('load', 'window'); streamed.flush(); assert.equal(streamed.reads.selector, 3);
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
  assert.deepEqual(
    { style: f.reads.style, rect: f.reads.rect, writes: f.writes(), rules: f.ruleWrites() },
    { style: before.reads.style, rect: before.reads.rect, writes: before.writes, rules: before.rules },
  );
  assert.equal(f.reads.selector, before.reads.selector, 'Quiet scroll checks use cached candidates');
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
  assert.deepEqual(
    { style: f.reads.style, rect: f.reads.rect, writes: f.writes(), rules: f.ruleWrites() },
    { style: before.reads.style + 2, rect: before.reads.rect, writes: before.writes, rules: before.rules },
    'Changed and added selector targets each get one author-top check; scroll does no broad repair',
  );
  assert.equal(f.reads.selector, before.reads.selector, 'Scroll does not restart semantic discovery');
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
  const own = f.sheets.find((node) => node !== inaccessible && node.sheet.cssRules.some((rule) => rule.selectorText.startsWith('#valid.future') && rule.style.getPropertyPriority('top') === 'important') && node.parentElement?.localName === 'html');
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
  assert.equal(reservedLayers.flatMap((node) => node.sheet.cssRules).filter((rule) => rule.selectorText.startsWith('#reserved.future')).length, 1);
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
  assert.match(own.textContent, /#gecko\.fixed(?::not\(:where\(\[data-candy-safe-area-[^\]]+\]\)\)){2} \{ top: 40px !important; \}/);
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
  f.event('load', 'window'); f.flush();
  assert.deepEqual(
    { style: f.reads.style, rect: f.reads.rect },
    { style: before.style, rect: before.rect },
    'Author padding recapture happens only once',
  );
  assert.equal(f.reads.selector, before.selector + 1);
  f.body.style.setProperty('padding-top', '120px'); f.flush(); f.configure({ enabled: false });
  assert.equal(f.computed(f.body).paddingTop, '120px', 'Disable exposes latest author inline padding');
  const existing = fixture(); existing.body.computed.paddingTop = '100px'; existing.start(false);
  assert.equal(existing.computed(existing.body).paddingTop, '100px', 'First configure protects an existing body synchronously');
});
