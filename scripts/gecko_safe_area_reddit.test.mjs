import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import vm from 'node:vm';

const source = readFileSync(new URL('../app/src/gecko/assets/candy_privacy/content_safe_area_reddit.js', import.meta.url), 'utf8');

function fixture({ hostname = 'reddit.com', topFrame = true } = {}) {
  const timers = new Map(); const observers = []; const definitions = new Map();
  let timerId = 0;
  class Node {
    constructor(tag, classes = []) {
      this.localName = tag; this.nodeType = 1; this.children = []; this.parentNode = null;
      this.classList = { contains: (name) => classes.includes(name) };
      this.textContent = ''; this.shadowRoot = null;
    }
    get parentElement() { return this.parentNode?.nodeType === 1 ? this.parentNode : null; }
    get isConnected() { return this.documentRoot === true || !!(this.parentNode?.isConnected || this.host?.isConnected); }
    appendChild(node) { node.remove(); this.children.push(node); node.parentNode = this; return node; }
    remove() {
      if (this.parentNode) this.parentNode.children.splice(this.parentNode.children.indexOf(this), 1);
      this.parentNode = null;
    }
    closest(tag) { for (let node = this; node; node = node.parentElement) if (node.localName === tag) return node; return null; }
    getRootNode() { let node = this; while (node.parentNode) node = node.parentNode; return node; }
    querySelectorAll(selector) {
      const result = [];
      const visit = (parent) => {
        for (const child of parent.children) {
          if (selector.startsWith('.') ? child.classList.contains(selector.slice(1)) : child.localName === selector) result.push(child);
          visit(child);
        }
      };
      visit(this); return result;
    }
    querySelector(selector) { return this.querySelectorAll(selector)[0] || null; }
    openShadow() { this.shadowRoot = new Node('#shadow-root'); this.shadowRoot.nodeType = 11; this.shadowRoot.host = this; return this.shadowRoot; }
  }
  const root = new Node('html'); root.documentRoot = true;
  const body = root.appendChild(new Node('body'));
  const document = { documentElement: root, body, createElement: (tag) => new Node(tag),
    querySelectorAll: (selector) => root.querySelectorAll(selector) };
  const windowProxy = {};
  const context = vm.createContext({ document, self: windowProxy, top: topFrame ? windowProxy : {},
    location: { hostname },
    getComputedStyle: () => { throw new Error('Reddit helper must not measure computed styles'); },
    MutationObserver: class {
      constructor(callback) { this.callback = callback; observers.push(this); }
      observe(target, options) { this.target = target; this.options = options; this.connected = true; }
      disconnect() { this.connected = false; }
    },
    setTimeout: (callback) => { const id = ++timerId; timers.set(id, callback); return id; },
    clearTimeout: (id) => timers.delete(id),
    customElements: { whenDefined: (tag) => new Promise((resolve) => definitions.set(tag, resolve)) },
  });
  vm.runInContext(source, context);
  const flush = () => {
    for (let count = 0; timers.size; count++) {
      assert.ok(count < 8, 'Structural tasks must settle without polling');
      const callbacks = [...timers.values()]; timers.clear(); callbacks.forEach((callback) => callback());
    }
  };
  const app = (shadow = false, headerTag = 'reddit-header-small') => {
    const host = body.appendChild(new Node('shreddit-app'));
    const scope = shadow ? host.openShadow() : host;
    const main = scope.appendChild(new Node('div', ['main-container']));
    const header = scope.appendChild(new Node(headerTag));
    return { host, scope, main, header };
  };
  return { root, body, Node, app, flush, timers, observers, definitions, helper: context.CandyRedditSafeArea };
}

test('preseeds document CSS and scopes open app/header roots independently', () => {
  const f = fixture({ hostname: 'WWW.Reddit.com.' });
  f.helper.configure(true);
  const documentStyle = f.root.querySelector('style');
  assert.match(documentStyle.textContent, /:root shreddit-app \{ padding-top: calc\(var\(--page-y-padding, 0px\) \+ max\(var\(--candy-safe-area-inset-top, 0px\), env\(safe-area-inset-top, 0px\)\)\)/);
  assert.doesNotMatch(documentStyle.textContent, /\.main-container/);
  assert.match(documentStyle.textContent, /reddit-header-small\[hidden-by-scroll\] header \{ padding-top:/);
  assert.match(documentStyle.textContent, /reddit-header-large \{ top: max\(/);
  assert.match(documentStyle.textContent, /shreddit-header \{ top: max\(/);
  assert.doesNotMatch(documentStyle.textContent, /subreddit-banner-img/);
  assert.equal(f.helper.flowProtected(), false);
  const app = f.app(true); const headerRoot = app.header.openShadow();
  f.helper.added(app.host); f.flush();
  assert.match(app.scope.querySelector('style').textContent, /^reddit-header-small \{ top: max\(/);
  assert.doesNotMatch(app.scope.querySelector('style').textContent, /:root|shreddit-app/);
  assert.match(app.scope.querySelector('style').textContent, /:host \{ padding-top: calc\(var\(--page-y-padding, 0px\) \+ max\(var\(--candy-safe-area-inset-top, 0px\), env\(safe-area-inset-top, 0px\)\)\)/);
  assert.doesNotMatch(app.scope.querySelector('style').textContent, /subreddit-banner-img|\.main-container/);
  assert.match(headerRoot.querySelector('style').textContent, /^:host \{ top:/);
  assert.match(headerRoot.querySelector('style').textContent, /:host\(\.relative\) \{ top: calc\(0px - var\(--page-y-padding, 0px\)\)/);
  assert.match(headerRoot.querySelector('style').textContent, /:host\(\[hidden-by-scroll\]\) header \{ padding-top:/);
  assert.doesNotMatch(headerRoot.querySelector('style').textContent, /subreddit-banner-img/);
  assert.equal(f.helper.flowProtected(), true);
});

test('owns only app headers and repeated synchronization retains style identity', () => {
  const f = fixture(); const light = f.app(); const shadow = f.app(true);
  const large = light.host.appendChild(new f.Node('reddit-header-large'));
  const renamed = light.host.appendChild(new f.Node('shreddit-header'));
  f.helper.configure(true);
  const styles = [f.root.querySelector('style'), shadow.scope.querySelector('style')];
  assert.equal(f.helper.owns(light.header), true);
  assert.equal(f.helper.owns(shadow.header), true);
  assert.equal(f.helper.owns(large), true);
  assert.equal(f.helper.owns(renamed), true);
  assert.equal(f.helper.owns(light.main), false);
  assert.equal(f.helper.owns(f.body.appendChild(new f.Node('reddit-header-small'))), false);
  for (let count = 0; count < 4; count++) f.helper.sync();
  assert.equal(f.root.querySelectorAll('style').length, 1);
  assert.equal(shadow.scope.querySelectorAll('style').length, 1);
  assert.equal(f.root.querySelector('style'), styles[0]);
  assert.equal(shadow.scope.querySelector('style'), styles[1]);
  styles.forEach((style) => assert.equal(f.helper.ownsSource(style), true));
  f.helper.added(styles[0]); assert.equal(f.timers.size, 0);
  assert.ok(f.observers.every((observer) => !observer.options.attributes && !observer.options.subtree),
    'Scroll-state attributes must remain CSS-only');
});

test('flow callback follows app coverage even before main-container exists', () => {
  const f = fixture(); const values = [];
  f.helper.configure(true, (value) => values.push(value));
  const app = f.app(); f.helper.added(app.host); f.flush();
  f.helper.sync(); f.helper.configure(true, (value) => values.push(value));
  assert.deepEqual(values, [true]);
  app.main.remove(); f.helper.sync(); f.helper.sync();
  assert.deepEqual(values, [true]);
  app.host.remove(); f.helper.sync();
  assert.deepEqual(values, [true, false]);
  f.body.appendChild(app.host); f.helper.sync(); f.helper.configure(false, (value) => values.push(value));
  assert.deepEqual(values, [true, false, true, false]);
});

test('disable removes every owned root style, observer and pending structural task', async () => {
  const f = fixture(); const app = f.app(true); const headerRoot = app.header.openShadow();
  f.helper.configure(true);
  f.helper.added(app.header); assert.equal(f.timers.size, 1);
  f.helper.configure(false);
  assert.equal(f.timers.size, 0);
  assert.equal(f.root.querySelectorAll('style').length, 0);
  assert.equal(app.scope.querySelectorAll('style').length, 0);
  assert.equal(headerRoot.querySelectorAll('style').length, 0);
  assert.ok(f.observers.every((observer) => !observer.connected));
  for (const resolve of f.definitions.values()) resolve();
  await Promise.resolve(); f.flush();
  assert.equal(f.timers.size, 0); assert.equal(f.helper.owns(app.header), false);
  f.helper.sync(); assert.equal(f.root.querySelectorAll('style').length, 0);
});

test('wrong hostname and child frames install no helper or effects', () => {
  for (const options of [{ hostname: 'notreddit.com' }, { hostname: 'reddit.com.example.org' }, { topFrame: false }]) {
    const f = fixture(options);
    assert.equal(f.helper, undefined);
    assert.equal(f.root.querySelectorAll('style').length, 0);
    assert.equal(f.observers.length, 0); assert.equal(f.timers.size, 0);
  }
});
