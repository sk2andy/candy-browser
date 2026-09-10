"use strict";

const candyUsesBackgroundVideoVisibilityFix =
  /(^|\.)youtube(?:-nocookie)?\.com$/.test(location.hostname);
if (candyUsesBackgroundVideoVisibilityFix) {
  try {
    Object.defineProperties(document.wrappedJSObject, {
      hidden: { value: false },
      visibilityState: { value: "visible" },
    });
  } catch (_) { }
  window.addEventListener("visibilitychange", (event) => {
    event.stopImmediatePropagation();
  }, true);
}

const candyPictureInPicturePlayback = {
  candidates: new Set(),
  expected: false,
  generation: 0,
  presentedVideo: null,
  alignmentFrame: null,
  layoutObserver: null,
  resizeObserver: null,
};

const CANDY_PICTURE_IN_PICTURE_ROOT_ATTRIBUTE = "data-candy-picture-in-picture";
const CANDY_PICTURE_IN_PICTURE_VIDEO_ATTRIBUTE = "data-candy-picture-in-picture-video";
const CANDY_PICTURE_IN_PICTURE_STYLE_ATTRIBUTE = "data-candy-picture-in-picture-style";
const CANDY_PICTURE_IN_PICTURE_OFFSET_X = "--candy-picture-in-picture-offset-x";
const CANDY_PICTURE_IN_PICTURE_OFFSET_Y = "--candy-picture-in-picture-offset-y";

function clearCandyPictureInPictureVideoPresentation(video) {
  video?.removeAttribute(CANDY_PICTURE_IN_PICTURE_VIDEO_ATTRIBUTE);
  video?.style.removeProperty(CANDY_PICTURE_IN_PICTURE_OFFSET_X);
  video?.style.removeProperty(CANDY_PICTURE_IN_PICTURE_OFFSET_Y);
}

function clearCandyPictureInPicturePresentation() {
  if (candyPictureInPicturePlayback.alignmentFrame !== null) {
    cancelAnimationFrame(candyPictureInPicturePlayback.alignmentFrame);
    candyPictureInPicturePlayback.alignmentFrame = null;
  }
  candyPictureInPicturePlayback.layoutObserver?.disconnect();
  candyPictureInPicturePlayback.layoutObserver = null;
  candyPictureInPicturePlayback.resizeObserver?.disconnect();
  candyPictureInPicturePlayback.resizeObserver = null;
  clearCandyPictureInPictureVideoPresentation(
    candyPictureInPicturePlayback.presentedVideo,
  );
  candyPictureInPicturePlayback.presentedVideo = null;
  document.documentElement?.removeAttribute(CANDY_PICTURE_IN_PICTURE_ROOT_ATTRIBUTE);
  document.querySelector(
    `style[${CANDY_PICTURE_IN_PICTURE_STYLE_ATTRIBUTE}]`,
  )?.remove();
}

function currentCandyPictureInPictureVideo() {
  return Array.from(candyPictureInPicturePlayback.candidates)
    .filter((video) => video.isConnected && !video.ended)
    .sort((first, second) => {
      const playbackDifference = Number(first.paused) - Number(second.paused);
      if (playbackDifference !== 0) return playbackDifference;
      return second.clientWidth * second.clientHeight - first.clientWidth * first.clientHeight;
    })[0] || null;
}

function scheduleCandyPictureInPictureAlignment() {
  const video = candyPictureInPicturePlayback.presentedVideo;
  if (!candyPictureInPicturePlayback.expected || !video?.isConnected) return;
  if (candyPictureInPicturePlayback.alignmentFrame !== null) {
    cancelAnimationFrame(candyPictureInPicturePlayback.alignmentFrame);
  }
  candyPictureInPicturePlayback.alignmentFrame = requestAnimationFrame(() => {
    candyPictureInPicturePlayback.alignmentFrame = null;
    if (
      !candyPictureInPicturePlayback.expected ||
      candyPictureInPicturePlayback.presentedVideo !== video ||
      !video.isConnected
    ) return;
    video.style.setProperty(CANDY_PICTURE_IN_PICTURE_OFFSET_X, "0px");
    video.style.setProperty(CANDY_PICTURE_IN_PICTURE_OFFSET_Y, "0px");
    const bounds = video.getBoundingClientRect();
    video.style.setProperty(CANDY_PICTURE_IN_PICTURE_OFFSET_X, `${-bounds.left}px`);
    video.style.setProperty(CANDY_PICTURE_IN_PICTURE_OFFSET_Y, `${-bounds.top}px`);
  });
}

function presentCandyPictureInPictureVideo() {
  if (
    !candyPictureInPicturePlayback.expected ||
    !document.documentElement ||
    !document.body
  ) return;
  const video = currentCandyPictureInPictureVideo();
  if (!video) {
    clearCandyPictureInPicturePresentation();
    return;
  }
  if (candyPictureInPicturePlayback.presentedVideo !== video) {
    candyPictureInPicturePlayback.layoutObserver?.disconnect();
    candyPictureInPicturePlayback.resizeObserver?.disconnect();
    clearCandyPictureInPictureVideoPresentation(
      candyPictureInPicturePlayback.presentedVideo,
    );
    candyPictureInPicturePlayback.resizeObserver = new ResizeObserver(
      scheduleCandyPictureInPictureAlignment,
    );
    candyPictureInPicturePlayback.resizeObserver.observe(video);
    candyPictureInPicturePlayback.layoutObserver = new MutationObserver(
      scheduleCandyPictureInPictureAlignment,
    );
    for (let ancestor = video.parentElement; ancestor; ancestor = ancestor.parentElement) {
      candyPictureInPicturePlayback.resizeObserver.observe(ancestor);
      candyPictureInPicturePlayback.layoutObserver.observe(ancestor, {
        attributes: true,
        attributeFilter: ["class", "style"],
      });
    }
  }
  candyPictureInPicturePlayback.presentedVideo = video;
  video.setAttribute(CANDY_PICTURE_IN_PICTURE_VIDEO_ATTRIBUTE, "");
  document.documentElement.setAttribute(CANDY_PICTURE_IN_PICTURE_ROOT_ATTRIBUTE, "");
  if (!document.querySelector(`style[${CANDY_PICTURE_IN_PICTURE_STYLE_ATTRIBUTE}]`)) {
    const style = document.createElement("style");
    style.setAttribute(CANDY_PICTURE_IN_PICTURE_STYLE_ATTRIBUTE, "");
    style.textContent = `
html[${CANDY_PICTURE_IN_PICTURE_ROOT_ATTRIBUTE}],
html[${CANDY_PICTURE_IN_PICTURE_ROOT_ATTRIBUTE}] body {
  width: 100% !important;
  height: 100% !important;
  margin: 0 !important;
  padding: 0 !important;
  overflow: hidden !important;
  background: #000 !important;
}
html[${CANDY_PICTURE_IN_PICTURE_ROOT_ATTRIBUTE}] body * {
  visibility: hidden !important;
}
html[${CANDY_PICTURE_IN_PICTURE_ROOT_ATTRIBUTE}] video[${CANDY_PICTURE_IN_PICTURE_VIDEO_ATTRIBUTE}] {
  visibility: visible !important;
  display: block !important;
  position: fixed !important;
  inset: 0 !important;
  width: 100vw !important;
  height: 100vh !important;
  max-width: none !important;
  max-height: none !important;
  margin: 0 !important;
  transform: translate3d(
    var(${CANDY_PICTURE_IN_PICTURE_OFFSET_X}, 0px),
    var(${CANDY_PICTURE_IN_PICTURE_OFFSET_Y}, 0px),
    0
  ) !important;
  object-fit: contain !important;
  z-index: 2147483647 !important;
}
`;
    document.documentElement.appendChild(style);
  }
  scheduleCandyPictureInPictureAlignment();
}

function rememberCandyPictureInPictureVideos() {
  document.querySelectorAll("video").forEach((video) => {
    if (!video.paused && !video.ended) candyPictureInPicturePlayback.candidates.add(video);
  });
}

function playCandyPictureInPictureVideos(generation) {
  if (
    !candyPictureInPicturePlayback.expected ||
    candyPictureInPicturePlayback.generation !== generation
  ) return;
  candyPictureInPicturePlayback.candidates.forEach((video) => {
    if (!video.isConnected || video.ended) {
      candyPictureInPicturePlayback.candidates.delete(video);
      return;
    }
    Promise.resolve(video.play()).catch(() => {});
  });
  presentCandyPictureInPictureVideo();
}

function scheduleCandyPictureInPicturePlayback() {
  if (!candyPictureInPicturePlayback.expected) return;
  const generation = candyPictureInPicturePlayback.generation;
  [0, 100, 400, 1200, 2500].forEach((delayMillis) => {
    setTimeout(() => playCandyPictureInPictureVideos(generation), delayMillis);
  });
}

function updateCandyPictureInPicturePlayback(expected) {
  candyPictureInPicturePlayback.generation += 1;
  candyPictureInPicturePlayback.expected = expected;
  if (!expected) {
    clearCandyPictureInPicturePresentation();
    candyPictureInPicturePlayback.candidates.clear();
    return;
  }
  rememberCandyPictureInPictureVideos();
  presentCandyPictureInPictureVideo();
  scheduleCandyPictureInPicturePlayback();
}

document.addEventListener("play", (event) => {
  if (
    candyPictureInPicturePlayback.expected &&
    event.target instanceof HTMLVideoElement
  ) {
    candyPictureInPicturePlayback.candidates.add(event.target);
    presentCandyPictureInPictureVideo();
  }
}, true);
document.addEventListener("pause", (event) => {
  if (
    candyPictureInPicturePlayback.expected &&
    event.target instanceof HTMLVideoElement &&
    candyPictureInPicturePlayback.candidates.has(event.target)
  ) {
    scheduleCandyPictureInPicturePlayback();
  }
}, true);
document.addEventListener("visibilitychange", scheduleCandyPictureInPicturePlayback, true);
document.addEventListener("fullscreenchange", scheduleCandyPictureInPictureAlignment, true);
document.addEventListener("transitionend", scheduleCandyPictureInPictureAlignment, true);
window.addEventListener("visibilitychange", (event) => {
  if (!candyPictureInPicturePlayback.expected) return;
  scheduleCandyPictureInPicturePlayback();
}, true);
window.addEventListener("resize", scheduleCandyPictureInPictureAlignment, true);
window.addEventListener("orientationchange", scheduleCandyPictureInPictureAlignment, true);

(function installCandyCosmetics() {
  for (let frame = self; frame !== top;) {
    frame = frame.parent;
    try { void frame.document; } catch (_) { return; }
  }

  browser.runtime.sendMessage({ type: "cosmetics", url: location.href }).then((payload) => {
    if (!payload || payload.type !== "cosmetics") return;
    const selectors = Array.isArray(payload.selectors) ? payload.selectors.filter((selector) =>
      typeof selector === "string" && selector.length > 0 && selector.length <= 2048,
    ).slice(0, 4096) : [];
    if (selectors.length) {
      const style = document.createElement("style");
      style.dataset.candyPrivacy = "cosmetic";
      style.textContent = selectors.map((selector) =>
        `${selector}{display:none!important}`,
      ).join("\n");
      (document.documentElement || document).appendChild(style);

      if (selectors.includes("#data-protection-consent-dialog")) {
        const removeRedditConsentDialog = () => {
          const dialog = document.getElementById("data-protection-consent-dialog");
          if (!dialog) return false;
          dialog.remove();
          document.body?.classList.remove("rpl-scroll-lock");
          return true;
        };
        if (!removeRedditConsentDialog()) {
          const observer = new MutationObserver(() => {
            if (removeRedditConsentDialog()) observer.disconnect();
          });
          observer.observe(document.documentElement, { childList: true, subtree: true });
          setTimeout(() => observer.disconnect(), 10000);
        }
      }
    }

    const rules = Array.isArray(payload.procedural) ? payload.procedural.filter((rule) =>
      rule && (rule.action === "H" || rule.action === "R") &&
        typeof rule.selector === "string" && rule.selector.length > 0 && rule.selector.length <= 2048 &&
        typeof rule.text === "string" && rule.text.length <= 128 &&
        typeof rule.ignoreCase === "boolean",
    ).slice(0, 64) : [];
    if (!rules.length) return;

    let runs = 0;
    let scheduled = false;
    const apply = () => {
      scheduled = false;
      if (++runs > 20) {
        observer.disconnect();
        return;
      }
      const deadline = performance.now() + 8;
      rules.some((rule) => {
        if (performance.now() > deadline) return true;
        let nodes;
        try { nodes = document.querySelectorAll(rule.selector); } catch (_) { return false; }
        return Array.prototype.slice.call(nodes, 0, 128).some((node) => {
          if (performance.now() > deadline) return true;
          let content = node.textContent || "";
          let expected = rule.text;
          if (expected) {
            if (rule.ignoreCase) {
              content = content.toLowerCase();
              expected = expected.toLowerCase();
            }
            if (!content.includes(expected)) return false;
          }
          if (rule.action === "R") {
            node.remove();
          } else {
            node.dataset.candyProceduralHidden = "1";
            node.style.setProperty("display", "none", "important");
          }
          return false;
        });
      });
    };
    const schedule = () => {
      if (scheduled) return;
      scheduled = true;
      requestAnimationFrame(apply);
    };
    const observer = new MutationObserver(schedule);
    const start = () => {
      apply();
      observer.observe(document.documentElement, {
        childList: true,
        subtree: true,
        characterData: true,
      });
      setTimeout(() => observer.disconnect(), 5000);
    };
    if (document.documentElement) start();
    else addEventListener("DOMContentLoaded", start, { once: true });
  }).catch(() => { });

})();

function extractCandyReaderPayload() {
  const pageRoot = document.body;
  const visibleText = (pageRoot?.innerText || "").replace(/[\u0000-\u001f\u007f]+/g, " ")
    .replace(/\s+/g, " ").trim().slice(0, 2000);
  const hasVisibleContent = (() => {
    if (!pageRoot) return false;
    if (visibleText) return true;
    return Array.from(
      pageRoot.querySelectorAll("img,svg,canvas,video,iframe,object,embed"),
    ).some((node) => {
      if (
        node.id === "gt-nvframe" ||
        node.closest('#gt-nvframe,[hidden],[aria-hidden="true"]')
      ) return false;
      const style = getComputedStyle(node);
      if (
        style.display === "none" ||
        style.visibility === "hidden" ||
        style.opacity === "0" ||
        style.contentVisibility === "hidden"
      ) return false;
      const rect = node.getBoundingClientRect();
      return rect.width >= 32 && rect.height >= 32 && rect.width * rect.height >= 4096;
    });
  })();
  const source = document.querySelector("article") || document.querySelector("main") || document.body;
  if (!source) return { error: "missing-root", hasVisibleContent, visibleText };
  const root = source.cloneNode(true);
  root.querySelectorAll("script,style,noscript,template,iframe,object,embed,canvas,svg,form,input,button,nav,aside,footer,video,audio").forEach((node) => node.remove());
  const clean = (value, maxLength) => (value || "").replace(/[\u0000-\u001f\u007f]+/g, " ")
    .replace(/\s+/g, " ").trim().slice(0, maxLength);
  const blocks = [];
  let totalChars = 0;
  let totalLinks = 0;
  root.querySelectorAll("h1,h2,h3,h4,h5,h6,p,blockquote,li").forEach((node) => {
    if (blocks.length >= 600 || totalChars >= 500000) return;
    const text = clean(
      node.innerText || node.textContent,
      Math.min(12000, 500000 - totalChars),
    );
    if (!text || text.length < 2) return;
    const tag = node.tagName.toLowerCase();
    const kind = tag.startsWith("h") ? "heading" : tag === "blockquote" ? "quote" :
      tag === "li" ? "listitem" : "paragraph";
    const links = Array.from(node.querySelectorAll("a[href]"))
      .slice(0, Math.min(40, 500 - totalLinks)).map((anchor) => ({
        label: clean(anchor.innerText || anchor.textContent, 300),
        url: (anchor.href || "").slice(0, 2048),
      }));
    blocks.push({
      kind,
      level: kind === "heading" ? Number(tag.substring(1)) : 0,
      text,
      links,
    });
    totalChars += text.length;
    totalLinks += links.length;
  });
  return {
    title: clean(document.querySelector('meta[property="og:title"]')?.content, 500) ||
      clean(document.title, 500),
    siteName: clean(document.querySelector('meta[property="og:site_name"]')?.content, 200) ||
      clean(location.hostname, 200),
    sourceUrl: location.href.slice(0, 2048),
    hasVisibleContent,
    visibleText,
    blocks,
  };
}

browser.runtime.onMessage.addListener((message) => {
  if (!message) return undefined;
  if (message.type === "picture-in-picture-playback" && typeof message.expected === "boolean") {
    updateCandyPictureInPicturePlayback(message.expected);
    return undefined;
  }
  if (self === top && message.type === "reader-extract") {
    return Promise.resolve(extractCandyReaderPayload());
  }
  return undefined;
});
