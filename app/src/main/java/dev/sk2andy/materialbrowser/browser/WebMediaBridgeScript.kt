package dev.sk2andy.materialbrowser.browser

internal object WebMediaBridgeScript {
    fun javascript(bridgeToken: String, pictureInPictureEnabled: Boolean): String {
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
              const getOwnPropertyDescriptor = Object.getOwnPropertyDescriptor.bind(Object);
              const scheduleMicrotask = globalThis.queueMicrotask.bind(globalThis);
              const scheduleTimeout = globalThis.setTimeout.bind(globalThis);
              const cancelTimeout = globalThis.clearTimeout.bind(globalThis);
              const userActivation = navigator.userActivation;
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
              const suppressedPauses = new WeakSet();
              const nativePlay = HTMLMediaElement.prototype.play;
              const nativePause = HTMLMediaElement.prototype.pause;
              const nativeRequestFullscreen = Element.prototype.requestFullscreen;
              const nativeExitFullscreen = Document.prototype.exitFullscreen;
              const nativeFullscreenElementGetter = getOwnPropertyDescriptor(
                Document.prototype,
                'fullscreenElement'
              )?.get;
              const isTopLevelDocument = globalThis === globalThis.top;
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
              let nextPictureInPictureRequestId = 1;
              let pendingPictureInPicture = null;
              let pictureInPictureMedia = null;
              let pictureInPictureWindow = null;

              const releaseKeepPlaying = (media, honorSuppressedPause) => {
                if (keepPlaying === media) keepPlaying = null;
                const pauseWasSuppressed = suppressedPauses.delete(media);
                if (honorSuppressedPause && pauseWasSuppressed && !media.paused) {
                  nativePause.call(media);
                }
              };

              const reconcilePlaying = media => {
                if (keepPlaying === media) keepPlaying = null;
                const pauseWasSuppressed = suppressedPauses.delete(media);
                if (pauseWasSuppressed && !media.paused) nativePause.call(media);
                if (pauseWasSuppressed || media.paused) {
                  nativePlay.call(media).catch(() => {});
                }
              };

              HTMLMediaElement.prototype.pause = function() {
                if (keepPlaying === this) {
                  suppressedPauses.add(this);
                  return;
                }
                suppressedPauses.delete(this);
                return nativePause.call(this);
              };

              HTMLMediaElement.prototype.play = function() {
                if (suppressedPauses.delete(this) && !this.paused) {
                  nativePause.call(this);
                }
                return nativePlay.call(this);
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
              const report = (
                media,
                eventName,
                removed = false,
                bridgeEvent = 'state',
                requestId = null
              ) => {
                if (!(media instanceof HTMLMediaElement)) return;
                const now = Date.now();
                const lastReport = lastTimeReports.get(media) || 0;
                if (eventName === 'timeupdate' && now - lastReport < 1000) return;
                if (eventName === 'timeupdate') lastTimeReports.set(media, now);
                const video = media instanceof HTMLVideoElement;
                send({
                  v: 1,
                  event: bridgeEvent,
                  documentId,
                  mediaId: mediaId(media),
                  requestId,
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
              const domError = (message, name) => {
                try { return new DOMException(message, name); }
                catch (_) {
                  const error = new Error(message);
                  error.name = name;
                  return error;
                }
              };
              const pictureInPicturePolicyAllows = () => {
                const policy = document.permissionsPolicy || document.featurePolicy;
                if (!policy || typeof policy.allowsFeature !== 'function') {
                  return isTopLevelDocument;
                }
                try { return policy.allowsFeature('picture-in-picture'); }
                catch (_) { return isTopLevelDocument; }
              };
              const pictureInPicturePresentationAvailable = () =>
                isTopLevelDocument || (
                  document.fullscreenEnabled === true &&
                  typeof nativeRequestFullscreen === 'function' &&
                  typeof nativeExitFullscreen === 'function' &&
                  typeof nativeFullscreenElementGetter === 'function'
                );
              const pictureInPictureAvailable = () =>
                pictureInPicturePolicyAllows() && pictureInPicturePresentationAvailable();
              const exitPictureInPictureFullscreen = request => {
                if (
                  !request?.usesFullscreen ||
                  nativeFullscreenElementGetter.call(document) !== request.media ||
                  typeof nativeExitFullscreen !== 'function'
                ) return;
                try {
                  const result = nativeExitFullscreen.call(document);
                  if (result && typeof result.catch === 'function') result.catch(() => {});
                } catch (_) {}
              };
              const createPictureInPictureWindow = media => {
                const target = new EventTarget();
                Object.defineProperties(target, {
                  width: {
                    get: () => Math.max(1, media.clientWidth || media.videoWidth || 1)
                  },
                  height: {
                    get: () => Math.max(1, media.clientHeight || media.videoHeight || 1)
                  }
                });
                return target;
              };
              const pictureInPictureEvent = name => {
                const event = new Event(name);
                try {
                  Object.defineProperty(event, 'pictureInPictureWindow', {
                    value: pictureInPictureWindow
                  });
                } catch (_) {}
                return event;
              };
              const rejectPictureInPicture = (request, name, message) => {
                if (!request) return;
                request.reject(domError(message, name));
              };
              const leavePictureInPicture = media => {
                if (pictureInPictureMedia !== media) return;
                pictureInPictureMedia = null;
                media.dispatchEvent(pictureInPictureEvent('leavepictureinpicture'));
                pictureInPictureWindow = null;
              };
              const requestPictureInPicture = media => {
                if (!pictureInPictureAvailable()) {
                  return Promise.reject(domError(
                    'Picture-in-picture is unavailable for this frame.',
                    'NotAllowedError'
                  ));
                }
                if (media.disablePictureInPicture) {
                  return Promise.reject(domError(
                    'Picture-in-picture is disabled for this video.',
                    'InvalidStateError'
                  ));
                }
                if (
                  !media.isConnected ||
                  media.ended ||
                  media.readyState === HTMLMediaElement.HAVE_NOTHING ||
                  media.videoWidth <= 0 ||
                  media.videoHeight <= 0
                ) {
                  return Promise.reject(domError(
                    'Video metadata is not available.',
                    'InvalidStateError'
                  ));
                }
                if (!userActivation || !userActivation.isActive) {
                  return Promise.reject(domError(
                    'Picture-in-picture requires a user gesture.',
                    'NotAllowedError'
                  ));
                }
                if (pictureInPictureMedia === media && pictureInPictureWindow) {
                  return Promise.resolve(pictureInPictureWindow);
                }
                if (pendingPictureInPicture || pictureInPictureMedia) {
                  return Promise.reject(domError(
                    'Another picture-in-picture request is active.',
                    'InvalidStateError'
                  ));
                }
                const requestId = 'p' + nextPictureInPictureRequestId++;
                return new Promise((resolve, reject) => {
                  const request = {
                    media,
                    requestId,
                    resolve,
                    reject,
                    timeoutId: null,
                    usesFullscreen: !isTopLevelDocument
                  };
                  pendingPictureInPicture = request;
                  request.timeoutId = scheduleTimeout(() => {
                    if (pendingPictureInPicture !== request) return;
                    pendingPictureInPicture = null;
                    exitPictureInPictureFullscreen(request);
                    rejectPictureInPicture(
                      request,
                      'AbortError',
                      'Picture-in-picture request timed out.'
                    );
                  }, 6000);
                  const reportRequest = () => {
                    if (pendingPictureInPicture !== request) return;
                    report(
                      media,
                      'picture-in-picture-request',
                      false,
                      'picture-in-picture-request',
                      requestId
                    );
                  };
                  if (!request.usesFullscreen) {
                    reportRequest();
                    return;
                  }
                  try {
                    Promise.resolve(nativeRequestFullscreen.call(media)).then(
                      () => {
                        if (
                          pendingPictureInPicture !== request ||
                          nativeFullscreenElementGetter.call(document) !== media
                        ) {
                          throw domError(
                            'Video did not enter fullscreen.',
                            'NotAllowedError'
                          );
                        }
                        reportRequest();
                      },
                      error => { throw error; }
                    ).catch(error => {
                      if (pendingPictureInPicture !== request) return;
                      pendingPictureInPicture = null;
                      cancelTimeout(request.timeoutId);
                      exitPictureInPictureFullscreen(request);
                      rejectPictureInPicture(
                        request,
                        error?.name || 'NotAllowedError',
                        error?.message || 'Fullscreen was rejected.'
                      );
                    });
                  } catch (error) {
                    pendingPictureInPicture = null;
                    cancelTimeout(request.timeoutId);
                    rejectPictureInPicture(
                      request,
                      error?.name || 'NotAllowedError',
                      error?.message || 'Fullscreen was rejected.'
                    );
                  }
                });
              };
              const installPictureInPictureApi = () => {
                if (!${pictureInPictureEnabled} || !pictureInPicturePresentationAvailable()) return;
                if (
                  document.pictureInPictureEnabled === true &&
                  typeof HTMLVideoElement.prototype.requestPictureInPicture === 'function'
                ) return;
                try {
                  Object.defineProperties(document, {
                    pictureInPictureEnabled: {
                      configurable: true,
                      get: pictureInPictureAvailable
                    },
                    pictureInPictureElement: {
                      configurable: true,
                      get: () => pictureInPictureMedia
                    }
                  });
                  Object.defineProperty(
                    HTMLVideoElement.prototype,
                    'requestPictureInPicture',
                    {
                      configurable: true,
                      writable: true,
                      value: function() { return requestPictureInPicture(this); }
                    }
                  );
                } catch (_) {}
              };
              installPictureInPictureApi();
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
                if (keepPlaying && keepPlaying !== media) {
                  releaseKeepPlaying(keepPlaying, true);
                }
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
                  if (message.command === 'picture-in-picture-entered') {
                    const request = pendingPictureInPicture;
                    if (!request || request.requestId !== message.requestId || request.media !== media) {
                      return;
                    }
                    pendingPictureInPicture = null;
                    cancelTimeout(request.timeoutId);
                    pictureInPictureMedia = media;
                    pictureInPictureWindow = createPictureInPictureWindow(media);
                    media.dispatchEvent(pictureInPictureEvent('enterpictureinpicture'));
                    request.resolve(pictureInPictureWindow);
                    return;
                  }
                  if (message.command === 'picture-in-picture-failed') {
                    const request = pendingPictureInPicture;
                    if (!request || request.requestId !== message.requestId || request.media !== media) {
                      return;
                    }
                    pendingPictureInPicture = null;
                    cancelTimeout(request.timeoutId);
                    exitPictureInPictureFullscreen(request);
                    rejectPictureInPicture(
                      request,
                      'NotAllowedError',
                      'Android rejected the picture-in-picture request.'
                    );
                    return;
                  }
                  if (message.command === 'picture-in-picture-left') {
                    leavePictureInPicture(media);
                    if (!isTopLevelDocument) {
                      exitPictureInPictureFullscreen({ media, usesFullscreen: true });
                    }
                    return;
                  }
                  switch (message.command) {
                    case 'play': media.play().catch(() => {}); break;
                    case 'pause':
                      releaseKeepPlaying(media, false);
                      nativePause.call(media);
                      break;
                    case 'stop':
                      releaseKeepPlaying(media, false);
                      try { nativePause.call(media); media.currentTime = 0; }
                      finally { exitPresentation(); }
                      break;
                    case 'keep-playing':
                      if (keepPlaying && keepPlaying !== media) {
                        releaseKeepPlaying(keepPlaying, true);
                      }
                      keepPlaying = media;
                      media.play().catch(() => {});
                      break;
                    case 'reconcile-playing':
                      reconcilePlaying(media);
                      break;
                    case 'allow-pause':
                      releaseKeepPlaying(media, true);
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
                  if (pendingPictureInPicture?.media === node) {
                    const request = pendingPictureInPicture;
                    pendingPictureInPicture = null;
                    cancelTimeout(request.timeoutId);
                    exitPictureInPictureFullscreen(request);
                    rejectPictureInPicture(
                      request,
                      'AbortError',
                      'Video was removed before picture-in-picture started.'
                    );
                  }
                  leavePictureInPicture(node);
                  releaseKeepPlaying(node, true);
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
                if (pendingPictureInPicture) {
                  cancelTimeout(pendingPictureInPicture.timeoutId);
                }
                rejectPictureInPicture(
                  pendingPictureInPicture,
                  'AbortError',
                  'Document left before picture-in-picture started.'
                );
                pendingPictureInPicture = null;
                pictureInPictureMedia = null;
                pictureInPictureWindow = null;
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
