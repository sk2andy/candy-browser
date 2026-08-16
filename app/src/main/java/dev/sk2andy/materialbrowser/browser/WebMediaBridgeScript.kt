package dev.sk2andy.materialbrowser.browser

internal object WebMediaBridgeScript {
    fun javascript(bridgeToken: String): String {
        require(bridgeToken.matches(Regex("[A-Za-z0-9_-]{32,80}")))
        return """
            (() => {
              if (globalThis.__candyWebMediaInstalled) return;
              const bridge = globalThis.${WebMediaContract.BRIDGE_NAME};
              if (!bridge || typeof bridge.postMessage !== 'function') return;
              globalThis.__candyWebMediaInstalled = true;
              const bridgeToken = '$bridgeToken';
              const nativePost = bridge.postMessage.bind(bridge);
              const stringify = JSON.stringify.bind(JSON);
              const parse = JSON.parse.bind(JSON);
              const createObject = Object.create.bind(Object);
              const objectKeys = Object.keys.bind(Object);
              const scheduleMicrotask = globalThis.queueMicrotask.bind(globalThis);
              const newDocumentId = () => {
                try { return crypto.randomUUID().replaceAll('-', ''); }
                catch (_) { return Math.random().toString(36).slice(2) + Date.now().toString(36); }
              };
              let documentId = newDocumentId();
              const ids = new WeakMap();
              const mediaById = new Map();
              const lastTimeReports = new WeakMap();
              const originals = new Map();
              const appliedStyles = new Map();
              const observedRoots = new WeakSet();
              const nativePause = HTMLMediaElement.prototype.pause;
              const candyProperties = [
                'display', 'position', 'top', 'right', 'bottom', 'left', 'width', 'height',
                'max-width', 'max-height', 'margin', 'padding', 'background', 'object-fit',
                'z-index', 'visibility', 'content-visibility', 'overflow-x', 'overflow-y',
                'transform', 'translate', 'scale', 'rotate', 'filter', 'backdrop-filter',
                'perspective', 'contain', 'container-type', 'will-change', 'clip', 'clip-path',
                'mask', 'opacity', 'isolation', 'mix-blend-mode', 'transition', 'animation'
              ];
              let nextId = 1;
              let presented = null;
              let keepPlaying = null;
              let presentationObserver = null;
              let presentationRepairScheduled = false;

              HTMLMediaElement.prototype.pause = function() {
                if (keepPlaying === this) return;
                return nativePause.call(this);
              };

              const mediaId = media => {
                let id = ids.get(media);
                if (!id) {
                  id = 'm' + nextId++;
                  ids.set(media, id);
                }
                mediaById.set(id, media);
                return id;
              };
              const finite = value => Number.isFinite(value) ? value : null;
              const visibleRatio = media => {
                const rect = media.getBoundingClientRect();
                const width = Math.max(0, Math.min(rect.right, innerWidth) - Math.max(rect.left, 0));
                const height = Math.max(0, Math.min(rect.bottom, innerHeight) - Math.max(rect.top, 0));
                const area = Math.max(1, rect.width * rect.height);
                return Math.max(0, Math.min(1, width * height / area));
              };
              const send = value => {
                try {
                  const envelope = createObject(null);
                  objectKeys(value).forEach(key => { envelope[key] = value[key]; });
                  envelope.bridgeToken = bridgeToken;
                  nativePost(stringify(envelope));
                } catch (_) {}
              };
              const report = (media, eventName, removed = false) => {
                if (!(media instanceof HTMLMediaElement)) return;
                const now = Date.now();
                const lastReport = lastTimeReports.get(media) || 0;
                if (eventName === 'timeupdate' && now - lastReport < 1000) return;
                if (eventName === 'timeupdate') lastTimeReports.set(media, now);
                const video = media instanceof HTMLVideoElement;
                send({
                  v: 1,
                  event: 'state',
                  documentId,
                  mediaId: mediaId(media),
                  kind: video ? 'video' : 'audio',
                  paused: removed || !!media.paused,
                  ended: removed || !!media.ended,
                  currentTime: finite(media.currentTime),
                  duration: finite(media.duration),
                  playbackRate: finite(media.playbackRate),
                  muted: !!media.muted,
                  volume: finite(media.volume),
                  videoWidth: video ? media.videoWidth : 0,
                  videoHeight: video ? media.videoHeight : 0,
                  clientWidth: media.clientWidth || 0,
                  clientHeight: media.clientHeight || 0,
                  visibleRatio: removed ? 0 : visibleRatio(media)
                });
              };
              const scan = root => {
                if (!root || !root.querySelectorAll) return;
                root.querySelectorAll('video,audio').forEach(media => report(media, 'scan'));
                root.querySelectorAll('*').forEach(element => {
                  if (element.shadowRoot) observe(element.shadowRoot);
                });
              };
              const saveStyle = element => {
                if (originals.has(element)) return;
                const values = new Map();
                candyProperties.forEach(property => values.set(property, {
                  value: element.style.getPropertyValue(property),
                  priority: element.style.getPropertyPriority(property)
                }));
                originals.set(element, values);
                appliedStyles.set(element, new Map());
              };
              const setCandyStyle = (element, property, value) => {
                element.style.setProperty(property, value, 'important');
                appliedStyles.get(element).set(property, {
                  value: element.style.getPropertyValue(property),
                  priority: element.style.getPropertyPriority(property)
                });
              };
              const unclipAncestor = element => {
                if (!element) return;
                const computedDisplay = getComputedStyle(element).display;
                saveStyle(element);
                setCandyStyle(element, 'transition', 'none');
                setCandyStyle(element, 'animation', 'none');
                setCandyStyle(
                  element,
                  'display',
                  computedDisplay === 'none' ? 'block' : computedDisplay
                );
                setCandyStyle(element, 'position', 'relative');
                setCandyStyle(element, 'top', 'auto');
                setCandyStyle(element, 'right', 'auto');
                setCandyStyle(element, 'bottom', 'auto');
                setCandyStyle(element, 'left', 'auto');
                setCandyStyle(element, 'z-index', 'auto');
                setCandyStyle(element, 'visibility', 'visible');
                setCandyStyle(element, 'content-visibility', 'visible');
                setCandyStyle(element, 'overflow-x', 'visible');
                setCandyStyle(element, 'overflow-y', 'visible');
                setCandyStyle(element, 'transform', 'none');
                setCandyStyle(element, 'translate', 'none');
                setCandyStyle(element, 'scale', 'none');
                setCandyStyle(element, 'rotate', 'none');
                setCandyStyle(element, 'filter', 'none');
                setCandyStyle(element, 'backdrop-filter', 'none');
                setCandyStyle(element, 'perspective', 'none');
                setCandyStyle(element, 'contain', 'none');
                setCandyStyle(element, 'container-type', 'normal');
                setCandyStyle(element, 'will-change', 'auto');
                setCandyStyle(element, 'clip', 'auto');
                setCandyStyle(element, 'clip-path', 'none');
                setCandyStyle(element, 'mask', 'none');
                setCandyStyle(element, 'opacity', '1');
                setCandyStyle(element, 'isolation', 'auto');
                setCandyStyle(element, 'mix-blend-mode', 'normal');
              };
              const fillViewport = element => {
                if (!element) return;
                saveStyle(element);
                setCandyStyle(element, 'display', 'block');
                setCandyStyle(element, 'position', 'fixed');
                setCandyStyle(element, 'top', '0');
                setCandyStyle(element, 'right', '0');
                setCandyStyle(element, 'bottom', '0');
                setCandyStyle(element, 'left', '0');
                setCandyStyle(element, 'width', '100vw');
                setCandyStyle(element, 'height', '100vh');
                setCandyStyle(element, 'max-width', 'none');
                setCandyStyle(element, 'max-height', 'none');
                setCandyStyle(element, 'margin', '0');
                setCandyStyle(element, 'padding', '0');
                setCandyStyle(element, 'background', 'black');
                setCandyStyle(element, 'object-fit', 'contain');
                setCandyStyle(element, 'z-index', '2147483647');
                setCandyStyle(element, 'visibility', 'visible');
                setCandyStyle(element, 'transform', 'none');
                setCandyStyle(element, 'translate', 'none');
                setCandyStyle(element, 'scale', 'none');
                setCandyStyle(element, 'rotate', 'none');
                setCandyStyle(element, 'filter', 'none');
                setCandyStyle(element, 'clip', 'auto');
                setCandyStyle(element, 'clip-path', 'none');
                setCandyStyle(element, 'opacity', '1');
              };
              const restore = (element, values) => {
                const applied = appliedStyles.get(element);
                values.forEach((saved, property) => {
                  const candy = applied && applied.get(property);
                  if (!candy) return;
                  if (element.style.getPropertyValue(property) !== candy.value) return;
                  if (element.style.getPropertyPriority(property) !== candy.priority) return;
                  if (saved.value) element.style.setProperty(property, saved.value, saved.priority);
                  else element.style.removeProperty(property);
                });
              };
              const exitPresentation = () => {
                if (!presented && originals.size === 0) return;
                if (presentationObserver) presentationObserver.disconnect();
                presentationObserver = null;
                presentationRepairScheduled = false;
                originals.forEach((values, element) => restore(element, values));
                originals.clear();
                appliedStyles.clear();
                presented = null;
              };
              const presentationElements = media => {
                const elements = [document.documentElement, document.body];
                let node = media;
                while (node && node instanceof HTMLElement) {
                  if (!elements.includes(node)) elements.push(node);
                  const root = node.getRootNode && node.getRootNode();
                  node = node.parentElement || (root && root.host) || null;
                }
                return elements.filter(Boolean);
              };
              const presentationIsApplied = elements => {
                if (
                  !presented ||
                  elements.length !== originals.size ||
                  !elements.every(element => originals.has(element))
                ) return false;
                return elements.every(element => {
                  const applied = appliedStyles.get(element);
                  if (!applied) return false;
                  let matches = true;
                  applied.forEach((candy, property) => {
                    if (element.style.getPropertyValue(property) !== candy.value) matches = false;
                    if (element.style.getPropertyPriority(property) !== candy.priority) matches = false;
                  });
                  return matches;
                });
              };
              const observePresentation = elements => {
                presentationObserver = new MutationObserver(() => {
                  if (presentationRepairScheduled) return;
                  presentationRepairScheduled = true;
                  scheduleMicrotask(() => {
                    presentationRepairScheduled = false;
                    if (!presented) return;
                    if (!presented.isConnected) {
                      exitPresentation();
                      return;
                    }
                    const currentElements = presentationElements(presented);
                    if (!presentationIsApplied(currentElements)) enterPresentation(presented);
                  });
                });
                elements.forEach(element => presentationObserver.observe(
                  element,
                  { attributes: true, attributeFilter: ['style'], childList: true }
                ));
              };
              const enterPresentation = media => {
                const elements = presentationElements(media);
                if (presented === media && presentationIsApplied(elements)) {
                  report(media, 'presentation');
                  return;
                }
                if (keepPlaying && keepPlaying !== media) keepPlaying = null;
                exitPresentation();
                presented = media;
                elements.forEach(unclipAncestor);
                fillViewport(media);
                observePresentation(elements);
                report(media, 'presentation');
              };
              bridge.onmessage = event => {
                try {
                  const message = parse(event.data);
                  if (message.v !== 1 || message.documentId !== documentId) return;
                  if (message.command === 'exit-presentation') {
                    exitPresentation();
                    return;
                  }
                  const media = mediaById.get(message.mediaId);
                  if (!media) return;
                  switch (message.command) {
                    case 'play': media.play().catch(() => {}); break;
                    case 'pause':
                      if (keepPlaying === media) keepPlaying = null;
                      nativePause.call(media);
                      break;
                    case 'stop':
                      if (keepPlaying === media) keepPlaying = null;
                      try { nativePause.call(media); media.currentTime = 0; }
                      finally { exitPresentation(); }
                      break;
                    case 'keep-playing':
                      keepPlaying = media;
                      media.play().catch(() => {});
                      break;
                    case 'allow-pause':
                      if (keepPlaying === media) keepPlaying = null;
                      break;
                    case 'seek-to':
                      if (Number.isFinite(message.position)) media.currentTime = Math.max(0, message.position);
                      break;
                    case 'enter-presentation': enterPresentation(media); break;
                  }
                  report(media, 'command');
                } catch (_) {}
              };
              const events = [
                'play', 'playing', 'pause', 'ended', 'emptied', 'loadedmetadata',
                'durationchange', 'ratechange', 'volumechange', 'timeupdate'
              ];
              const reportRemoved = node => {
                if (node && node.isConnected) return;
                if (node instanceof HTMLMediaElement) {
                  if (keepPlaying === node) keepPlaying = null;
                  if (presented === node) exitPresentation();
                  report(node, 'removed', true);
                  mediaById.delete(mediaId(node));
                }
                if (node && node.shadowRoot) reportRemoved(node.shadowRoot);
                if (node && node.querySelectorAll) {
                  node.querySelectorAll('video,audio').forEach(media => reportRemoved(media));
                }
              };
              const observe = root => {
                if (!root || observedRoots.has(root)) return;
                observedRoots.add(root);
                events.forEach(name => root.addEventListener(name, event => {
                  if (
                    (name === 'ended' || name === 'emptied') &&
                    presented === event.target
                  ) {
                    if (keepPlaying === event.target) keepPlaying = null;
                    exitPresentation();
                  }
                  report(event.target, name);
                }, true));
                scan(root);
                new MutationObserver(records => {
                  const removed = [];
                  records.forEach(record => {
                    record.removedNodes.forEach(node => removed.push(node));
                    record.addedNodes.forEach(node => {
                      if (node instanceof HTMLMediaElement) report(node, 'added');
                      if (node && node.shadowRoot) observe(node.shadowRoot);
                      scan(node);
                    });
                  });
                  scheduleMicrotask(() => removed.forEach(reportRemoved));
                }).observe(root, { childList: true, subtree: true });
              };
              const originalAttachShadow = Element.prototype.attachShadow;
              Element.prototype.attachShadow = function(init) {
                const root = originalAttachShadow.call(this, init);
                if (init && init.mode === 'open') observe(root);
                return root;
              };
              if (document.documentElement) observe(document.documentElement);
              else document.addEventListener(
                'DOMContentLoaded',
                () => observe(document.documentElement),
                { once: true }
              );
              addEventListener('pagehide', () => {
                keepPlaying = null;
                exitPresentation();
                send({ v: 1, event: 'document-gone', documentId });
              });
              addEventListener('pageshow', event => {
                if (!event.persisted) return;
                documentId = newDocumentId();
                scan(document);
              });
            })();
        """.trimIndent()
    }
}
