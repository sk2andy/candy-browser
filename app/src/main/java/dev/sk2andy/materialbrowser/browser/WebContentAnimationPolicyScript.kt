package dev.sk2andy.materialbrowser.browser

internal object WebContentAnimationPolicyScript {
    private const val CLEANUP_KEY = "__candyAnimationPolicyCleanup"

    fun script(animationsEnabled: Boolean): String = if (animationsEnabled) {
        "globalThis.$CLEANUP_KEY?.();"
    } else {
        installScript
    }

    private val installScript: String = """
        (() => {
          "use strict";
          globalThis.$CLEANUP_KEY?.();

          const style = document.createElement("style");
          style.dataset.candyAnimationPolicy = "disabled";
          style.textContent = `
            *, *::before, *::after {
              animation-delay: 0s !important;
              animation-duration: 0.001ms !important;
              animation-iteration-count: 1 !important;
              scroll-behavior: auto !important;
              transition-delay: 0s !important;
              transition-duration: 0s !important;
            }
            ::view-transition-group(*),
            ::view-transition-image-pair(*),
            ::view-transition-old(*),
            ::view-transition-new(*) {
              animation-delay: 0s !important;
              animation-duration: 0.001ms !important;
              animation-iteration-count: 1 !important;
            }
          `;
          let styleObserver = null;
          const attachStyle = () => {
            const parent = document.head || document.documentElement;
            if (!parent) return false;
            parent.appendChild(style);
            styleObserver?.disconnect();
            styleObserver = null;
            return true;
          };
          if (!attachStyle()) {
            styleObserver = new MutationObserver(attachStyle);
            styleObserver.observe(document, { childList: true, subtree: true });
          }

          let active = true;
          const settleAnimation = (animation) => {
            if (!active || animation?.playState === "finished" || animation?.playState === "idle") {
              return;
            }
            try {
              if (animation.effect?.getTiming?.().iterations === Infinity) animation.cancel();
              else animation.finish();
            } catch (_) {
              try { animation.cancel(); } catch (_) {}
            }
          };
          const settleDocumentAnimations = () => {
            try { document.getAnimations().forEach(settleAnimation); } catch (_) {}
          };
          const settleStartedAnimation = (event) => {
            try {
              event.target?.getAnimations?.({ subtree: true }).forEach(settleAnimation);
            } catch (_) {
              settleDocumentAnimations();
            }
          };
          document.addEventListener("animationstart", settleStartedAnimation, true);
          document.addEventListener("transitionrun", settleStartedAnimation, true);

          const elementPrototype = globalThis.Element?.prototype;
          const originalAnimate = elementPrototype?.animate;
          const blockedAnimate = originalAnimate && function(...args) {
            const animation = originalAnimate.apply(this, args);
            settleAnimation(animation);
            return animation;
          };
          if (elementPrototype && blockedAnimate) elementPrototype.animate = blockedAnimate;

          const originalScrollMethods = [];
          const forceInstantScroll = (prototype, name) => {
            const original = prototype?.[name];
            if (typeof original !== "function") return;
            const replacement = function(...args) {
              if (args[0] && typeof args[0] === "object") {
                args[0] = { ...args[0], behavior: "auto" };
              }
              return original.apply(this, args);
            };
            try {
              prototype[name] = replacement;
              originalScrollMethods.push({ prototype, name, original, replacement });
            } catch (_) {}
          };
          for (const name of ["scroll", "scrollBy", "scrollTo"]) {
            forceInstantScroll(globalThis, name);
            forceInstantScroll(globalThis.Element?.prototype, name);
          }
          forceInstantScroll(globalThis.Element?.prototype, "scrollIntoView");

          settleDocumentAnimations();
          const cleanup = () => {
            if (!active) return;
            active = false;
            document.removeEventListener("animationstart", settleStartedAnimation, true);
            document.removeEventListener("transitionrun", settleStartedAnimation, true);
            styleObserver?.disconnect();
            style.remove();
            if (elementPrototype?.animate === blockedAnimate) {
              elementPrototype.animate = originalAnimate;
            }
            for (const entry of originalScrollMethods) {
              if (entry.prototype[entry.name] === entry.replacement) {
                entry.prototype[entry.name] = entry.original;
              }
            }
            if (globalThis.$CLEANUP_KEY === cleanup) delete globalThis.$CLEANUP_KEY;
          };
          globalThis.$CLEANUP_KEY = cleanup;
        })();
    """.trimIndent()
}
