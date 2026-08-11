package dev.sk2andy.materialbrowser.browser

internal object VideoAutoplayBlockerScript {
    private const val CLEANUP_KEY = "__candyVideoAutoplayBlockerCleanup"
    private const val CLEANUP_MESSAGE = "__candyVideoAutoplayBlockerCleanupMessage"

    val cleanupScript: String = "window.$CLEANUP_KEY?.();"

    val installScript: String = """
        (() => {
          window.$CLEANUP_KEY?.();

          const mediaPrototype = window.HTMLMediaElement?.prototype;
          const videoType = window.HTMLVideoElement;
          if (!mediaPrototype || !videoType) return;

          const originalPlayDescriptor = Object.getOwnPropertyDescriptor(mediaPrototype, 'play');
          const originalPlay = mediaPrototype.play;
          const originalPause = mediaPrototype.pause;
          const playbackGrants = new WeakMap();
          let activationGeneration = 0;
          let active = true;

          const grantPlayback = (video) => {
            playbackGrants.set(video, performance.now() + 1000);
          };
          const consumePlaybackGrant = (video) => {
            const expiresAt = playbackGrants.get(video) ?? 0;
            playbackGrants.delete(video);
            return expiresAt >= performance.now();
          };
          const hasCurrentTrustedActivation = () =>
            activationGeneration !== 0 && (navigator.userActivation?.isActive ?? true);
          const associatedGestureVideo = (event) => {
            if (event.type === 'pointerdown') return null;
            const eventPath = event.composedPath?.() ?? [event.target];
            const directVideo = eventPath.find((node) => node instanceof videoType);
            if (directVideo) return directVideo;
            for (const node of eventPath) {
              if (!(node instanceof Element)) continue;
              const className = typeof node.className === 'string' ? node.className : '';
              const identity = `${'$'}{node.localName} ${'$'}{node.id} ${'$'}{className}`;
              if (!/(player|video|media)/i.test(identity)) continue;
              const videos = node.querySelectorAll('video');
              if (videos.length === 1) return videos[0];
            }
            return null;
          };
          const beginTrustedActivation = (event) => {
            if (!event.isTrusted) return;
            const generation = ++activationGeneration;
            const video = associatedGestureVideo(event);
            if (video) grantPlayback(video);
            queueMicrotask(() => {
              if (activationGeneration === generation) activationGeneration = 0;
            });
          };

          const blockAutoplayAttribute = (root) => {
            const videos = root instanceof videoType
              ? [root]
              : root.querySelectorAll?.('video[autoplay]') ?? [];
            for (const video of videos) {
              if (video.autoplay) video.autoplay = false;
              if (!video.paused) originalPause.call(video);
            }
          };
          const mutationObserver = new MutationObserver((records) => {
            for (const record of records) {
              if (record.type === 'attributes') {
                blockAutoplayAttribute(record.target);
              } else {
                for (const node of record.addedNodes) {
                  if (node.nodeType === Node.ELEMENT_NODE) blockAutoplayAttribute(node);
                }
              }
            }
          });
          mutationObserver.observe(document, {
            attributes: true,
            attributeFilter: ['autoplay'],
            childList: true,
            subtree: true
          });
          blockAutoplayAttribute(document);

          const blockedPlay = function(...args) {
            if (!active || !(this instanceof videoType)) {
              return originalPlay.apply(this, args);
            }
            if (!hasCurrentTrustedActivation() && !consumePlaybackGrant(this)) {
              return Promise.reject(
                new DOMException('Video playback requires user interaction.', 'NotAllowedError')
              );
            }
            grantPlayback(this);
            try {
              const result = originalPlay.apply(this, args);
              result?.then?.(undefined, () => playbackGrants.delete(this));
              return result;
            } catch (error) {
              playbackGrants.delete(this);
              throw error;
            }
          };
          if (originalPlayDescriptor) {
            Object.defineProperty(
              mediaPrototype,
              'play',
              { ...originalPlayDescriptor, value: blockedPlay }
            );
          } else {
            mediaPrototype.play = blockedPlay;
          }

          const stopAutomaticPlayback = (event) => {
            const video = event.target;
            if (!(video instanceof videoType)) return;
            if (consumePlaybackGrant(video) || hasCurrentTrustedActivation()) return;
            originalPause.call(video);
          };
          window.addEventListener('play', stopAutomaticPlayback, true);
          window.addEventListener('pointerdown', beginTrustedActivation, true);
          window.addEventListener('click', beginTrustedActivation, true);
          window.addEventListener('keydown', beginTrustedActivation, true);

          const cleanup = () => {
            if (!active) return;
            active = false;
            mutationObserver.disconnect();
            window.removeEventListener('play', stopAutomaticPlayback, true);
            window.removeEventListener('pointerdown', beginTrustedActivation, true);
            window.removeEventListener('click', beginTrustedActivation, true);
            window.removeEventListener('keydown', beginTrustedActivation, true);
            window.removeEventListener('message', cleanupFromParent, true);
            if (mediaPrototype.play === blockedPlay) {
              if (originalPlayDescriptor) {
                Object.defineProperty(mediaPrototype, 'play', originalPlayDescriptor);
              } else {
                mediaPrototype.play = originalPlay;
              }
            }
            if (window.$CLEANUP_KEY === cleanup) delete window.$CLEANUP_KEY;
            for (let index = 0; index < window.frames.length; index++) {
              try {
                window.frames[index].postMessage('$CLEANUP_MESSAGE', '*');
              } catch (_) {
                // A frame may disappear while cleanup is running.
              }
            }
          };
          const cleanupFromParent = (event) => {
            if (event.data === '$CLEANUP_MESSAGE') cleanup();
          };
          window.addEventListener('message', cleanupFromParent, true);
          window.$CLEANUP_KEY = cleanup;
        })();
    """.trimIndent()
}
