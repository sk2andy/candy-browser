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
  inlinePresentationExpected: false,
  inlineMediaPlayerEnabled: false,
  inlineMediaPlayerMode: "button_fullscreen",
  inlineOpenRequestKey: null,
  inlineOpenRequestTimer: null,
  inlineMediaPolicyRevision: 0,
  inlineMediaNavigationGeneration: 0,
  inlineMediaPlayerActionLabel: "Open in Candy Player",
  inlineMediaPlayerPlayLabel: "Play",
  inlineMediaPlayerPauseLabel: "Pause",
  inlineMediaPlayerSeekLabel: "Seek",
  inlineMediaPlayerEnterFullscreenLabel: "Enter fullscreen",
  inlineMediaPlayerExitFullscreenLabel: "Exit fullscreen",
  inlineMediaPlayerCloseLabel: "Close Candy Player",
  generation: 0,
  presentedVideo: null,
  alignmentFrame: null,
  alignmentMonitorFrame: null,
  pictureInPictureAncestors: [],
  layoutObserver: null,
  resizeObserver: null,
  inlineStateObserver: null,
  inlineStateFrame: null,
  inlineActionHost: null,
  inlineActionVideo: null,
  inlineActionResizeObserver: null,
  inlineActionLayoutObserver: null,
  inlineControlsObserver: null,
  inlinePresentationResizeObserver: null,
  inlinePresentationLayoutObserver: null,
  inlineControlsHost: null,
  inlineControlsVideo: null,
  inlineControlsCleanup: null,
  inlineSitePlayer: null,
  inlineSiteStyle: null,
  inlineStableOrigin: null,
  inlineFullscreenOrigin: null,
};
const candyInlineVideoDocumentNonce = candyInlineVideoNonce();
const candyInlineVideoElementNonces = new WeakMap();
const candyInlineVideoOriginalControls = new WeakMap();
const candyPictureInPictureOriginalControls = new WeakMap();

const CANDY_PICTURE_IN_PICTURE_ROOT_ATTRIBUTE = "data-candy-picture-in-picture";
const CANDY_PICTURE_IN_PICTURE_VIDEO_ATTRIBUTE = "data-candy-picture-in-picture-video";
const CANDY_PICTURE_IN_PICTURE_STYLE_ATTRIBUTE = "data-candy-picture-in-picture-style";
const CANDY_PICTURE_IN_PICTURE_ANCESTOR_ATTRIBUTE = "data-candy-picture-in-picture-ancestor";
const CANDY_PICTURE_IN_PICTURE_OFFSET_X = "--candy-picture-in-picture-offset-x";
const CANDY_PICTURE_IN_PICTURE_OFFSET_Y = "--candy-picture-in-picture-offset-y";
const CANDY_INLINE_VIDEO_ACTION_ATTRIBUTE = "data-candy-inline-video-action";
const CANDY_INLINE_VIDEO_CONTROLS_ATTRIBUTE = "data-candy-inline-video-controls";
const CANDY_INLINE_VIDEO_SITE_PLAYER_ATTRIBUTE = "data-candy-inline-video-site-player";
const CANDY_INLINE_VIDEO_SITE_STYLE_ATTRIBUTE = "data-candy-inline-video-site-style";
const CANDY_INLINE_VIDEO_FULLSCREEN_ORIGIN_ATTRIBUTE = "data-candy-inline-video-fullscreen-origin";
const CANDY_INLINE_VIDEO_FULLSCREEN_ORIGIN_STYLE_ATTRIBUTE =
  "data-candy-inline-video-fullscreen-origin-style";
const CANDY_INLINE_VIDEO_FULLSCREEN_ORIGIN_TOP = "--candy-inline-video-fullscreen-origin-top";
const CANDY_INLINE_VIDEO_FULLSCREEN_ORIGIN_LEFT = "--candy-inline-video-fullscreen-origin-left";
const CANDY_INLINE_VIDEO_ORIGIN_ATTRIBUTE = "data-candy-inline-video-origin";
const CANDY_INLINE_VIDEO_ORIGIN_X = "--candy-inline-video-origin-x";
const CANDY_INLINE_VIDEO_ORIGIN_Y = "--candy-inline-video-origin-y";
const CANDY_INLINE_VIDEO_ORIGIN_SCALE_X = "--candy-inline-video-origin-scale-x";
const CANDY_INLINE_VIDEO_ORIGIN_SCALE_Y = "--candy-inline-video-origin-scale-y";
const CANDY_INLINE_VIDEO_ACTION_SIZE_PX = 56;
const CANDY_INLINE_VIDEO_ACTION_INSET_PX = 16;
const CANDY_INLINE_VIDEO_CONTROLS_HEIGHT_PX = 88;
const CANDY_INLINE_FULLSCREEN_GESTURE_TOUCH_SLOP_PX = 10;
const CANDY_INLINE_FULLSCREEN_GESTURE_MIN_THRESHOLD_PX = 48;
const CANDY_INLINE_FULLSCREEN_GESTURE_MAX_THRESHOLD_PX = 96;
const CANDY_INLINE_FULLSCREEN_GESTURE_THRESHOLD_FRACTION = 0.13;
const CANDY_INLINE_FULLSCREEN_GESTURE_STICKY_FRACTION = 0.18;
const CANDY_INLINE_MEDIA_PLAYER_MODES = new Set([
  "button_fullscreen",
  "button_inline_and_fullscreen",
  "always_for_fullscreen",
  "automatic",
]);

function candyInlineFullscreenGestureDirection(deltaX, deltaY, touchSlop) {
  if (![deltaX, deltaY, touchSlop].every(Number.isFinite) || touchSlop < 0) {
    return "rejected";
  }
  if (Math.hypot(deltaX, deltaY) < touchSlop) return "pending";
  if (deltaY >= 0 || Math.abs(deltaY) <= Math.abs(deltaX) * 1.15) return "rejected";
  return "up";
}

function candyInlineFullscreenGestureUpdate(upwardDistance, viewportHeight) {
  if (!Number.isFinite(viewportHeight) || viewportHeight <= 0) {
    return { distance: 0, threshold: 0, offset: 0, shouldCommit: false };
  }
  const distance = Number.isFinite(upwardDistance) ? Math.max(0, upwardDistance) : 0;
  const threshold = Math.max(
    CANDY_INLINE_FULLSCREEN_GESTURE_MIN_THRESHOLD_PX,
    Math.min(
      CANDY_INLINE_FULLSCREEN_GESTURE_MAX_THRESHOLD_PX,
      viewportHeight * CANDY_INLINE_FULLSCREEN_GESTURE_THRESHOLD_FRACTION,
    ),
  );
  const stickyOffset = threshold * CANDY_INLINE_FULLSCREEN_GESTURE_STICKY_FRACTION;
  const progress = Math.min(1, distance / threshold);
  const rubberbandProgress = 1 - (1 - progress) * (1 - progress);
  return {
    distance,
    threshold,
    offset: distance >= threshold ? stickyOffset + distance - threshold :
      stickyOffset * rubberbandProgress,
    shouldCommit: distance >= threshold,
  };
}

function candyInlineMediaPlayerShowsButton() {
  return candyPictureInPicturePlayback.inlineMediaPlayerMode === "button_fullscreen" ||
    candyPictureInPicturePlayback.inlineMediaPlayerMode === "button_inline_and_fullscreen";
}

function candyInlineMediaPlayerStartsAutomatically() {
  return candyPictureInPicturePlayback.inlineMediaPlayerMode === "automatic";
}

function candyInlineMediaPlayerReplacesFullscreen() {
  return candyPictureInPicturePlayback.inlineMediaPlayerMode === "always_for_fullscreen";
}

function candyInlineMediaPlayerIsFullscreenOnly() {
  return candyPictureInPicturePlayback.inlineMediaPlayerMode === "button_fullscreen" ||
    candyInlineMediaPlayerReplacesFullscreen();
}

function candyInlineVideoNonce() {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (value) => value.toString(16).padStart(2, "0")).join("");
}

function candyInlineVideoElementNonce(video) {
  let nonce = candyInlineVideoElementNonces.get(video);
  if (!nonce) {
    nonce = candyInlineVideoNonce();
    candyInlineVideoElementNonces.set(video, nonce);
  }
  return nonce;
}

function candyVideoPresentationExpected() {
  return candyPictureInPicturePlayback.expected ||
    candyPictureInPicturePlayback.inlinePresentationExpected;
}

function clearCandyInlineVideoControls(video) {
  if (!video || !candyInlineVideoOriginalControls.has(video)) return;
  video.controls = candyInlineVideoOriginalControls.get(video);
  candyInlineVideoOriginalControls.delete(video);
}

function candyInlineVideoTime(seconds) {
  if (!Number.isFinite(seconds) || seconds < 0) return "--:--";
  const rounded = Math.floor(seconds);
  const minutes = Math.floor(rounded / 60);
  const remainingSeconds = String(rounded % 60).padStart(2, "0");
  return `${minutes}:${remainingSeconds}`;
}

function candyWobblyProgressPath(value) {
  const progress = Math.min(1000, Math.max(0, Number(value) || 0));
  if (progress <= 0) return "M 0 16";
  const points = [{ x: 0, y: 16 }];
  const segments = Math.max(4, Math.ceil(progress / 42));
  const amplitude = Math.min(4.5, 1.6 + progress / 320);
  for (let index = 1; index <= segments; index += 1) {
    const x = progress * index / segments;
    const y = 16 + Math.sin(index * 1.45) * amplitude;
    points.push({ x, y });
  }
  const path = ["M 0 16"];
  for (let index = 0; index < segments; index += 1) {
    const previous = points[Math.max(0, index - 1)];
    const start = points[index];
    const end = points[index + 1];
    const next = points[Math.min(segments, index + 2)];
    const firstControlX = start.x + (end.x - previous.x) / 6;
    const firstControlY = start.y + (end.y - previous.y) / 6;
    const secondControlX = end.x - (next.x - start.x) / 6;
    const secondControlY = end.y - (next.y - start.y) / 6;
    path.push(
      `C ${firstControlX.toFixed(2)} ${firstControlY.toFixed(2)} ` +
      `${secondControlX.toFixed(2)} ${secondControlY.toFixed(2)} ` +
      `${end.x.toFixed(2)} ${end.y.toFixed(2)}`,
    );
  }
  return path.join(" ");
}

function candyInlineVideoSitePlayer(video) {
  if (!candyUsesBackgroundVideoVisibilityFix || !video?.isConnected) return null;
  return video.closest("#movie_player, .html5-video-player, ytm-player");
}

function rememberCandyInlineVideoStableOrigin(video) {
  if (!candyPictureInPicturePlayback.inlinePresentationExpected ||
      candyPictureInPicturePlayback.presentedVideo !== video ||
      candyPictureInPicturePlayback.expected || document.fullscreenElement ||
      candyPictureInPicturePlayback.inlineFullscreenOrigin) return;
  const player = candyInlineVideoSitePlayer(video);
  if (!player) return;
  const previous = candyPictureInPicturePlayback.inlineStableOrigin;
  if (previous?.video === video && previous.player === player &&
      previous.url === location.href &&
      (previous.viewportWidth !== innerWidth ||
        Math.abs(previous.viewportHeight - innerHeight) >
          Math.max(200, previous.viewportHeight * 0.2))) return;
  const videoBounds = video.getBoundingClientRect();
  const playerBounds = player.getBoundingClientRect();
  const parent = player.parentElement;
  const parentBounds = parent?.getBoundingClientRect();
  const playerStyle = getComputedStyle(player);
  if (videoBounds.width <= 0 || videoBounds.height <= 0 ||
      playerBounds.width <= 0 || playerBounds.height <= 0) return;
  candyPictureInPicturePlayback.inlineStableOrigin = {
    video,
    player,
    parent,
    url: location.href,
    videoBounds: {
      left: videoBounds.left, top: videoBounds.top,
      width: videoBounds.width, height: videoBounds.height,
    },
    playerBounds: {
      left: playerBounds.left, top: playerBounds.top,
      width: playerBounds.width, height: playerBounds.height,
    },
    parentBounds: parentBounds ? {
      left: parentBounds.left, top: parentBounds.top,
      width: parentBounds.width, height: parentBounds.height,
    } : null,
    viewportWidth: innerWidth,
    viewportHeight: innerHeight,
    scrollX,
    scrollY,
    top: Number.parseFloat(playerStyle.top),
    left: Number.parseFloat(playerStyle.left),
  };
}

function clearCandyInlineVideoSiteControls() {
  candyPictureInPicturePlayback.inlineSitePlayer?.removeAttribute(
    CANDY_INLINE_VIDEO_SITE_PLAYER_ATTRIBUTE,
  );
  candyPictureInPicturePlayback.inlineSitePlayer = null;
  candyPictureInPicturePlayback.inlineSiteStyle?.remove();
  candyPictureInPicturePlayback.inlineSiteStyle = null;
}

function suppressCandyInlineVideoSiteControls(video) {
  const player = candyInlineVideoSitePlayer(video);
  if (candyPictureInPicturePlayback.inlineSitePlayer !== player) {
    clearCandyInlineVideoSiteControls();
  }
  if (!player || !document.documentElement) return;
  player.setAttribute(CANDY_INLINE_VIDEO_SITE_PLAYER_ATTRIBUTE, "");
  candyPictureInPicturePlayback.inlineSitePlayer = player;
  if (candyPictureInPicturePlayback.inlineSiteStyle?.isConnected) return;
  const style = document.createElement("style");
  style.setAttribute(CANDY_INLINE_VIDEO_SITE_STYLE_ATTRIBUTE, "");
  style.textContent = `
[${CANDY_INLINE_VIDEO_SITE_PLAYER_ATTRIBUTE}] .ytp-chrome-bottom,
[${CANDY_INLINE_VIDEO_SITE_PLAYER_ATTRIBUTE}] .ytp-chrome-top,
[${CANDY_INLINE_VIDEO_SITE_PLAYER_ATTRIBUTE}] .ytp-gradient-bottom,
[${CANDY_INLINE_VIDEO_SITE_PLAYER_ATTRIBUTE}] .ytp-gradient-top,
[${CANDY_INLINE_VIDEO_SITE_PLAYER_ATTRIBUTE}] .ytp-bezel,
[${CANDY_INLINE_VIDEO_SITE_PLAYER_ATTRIBUTE}] .ytp-cards-button,
[${CANDY_INLINE_VIDEO_SITE_PLAYER_ATTRIBUTE}] .ytp-cards-teaser,
[${CANDY_INLINE_VIDEO_SITE_PLAYER_ATTRIBUTE}] .ytp-ce-element,
[${CANDY_INLINE_VIDEO_SITE_PLAYER_ATTRIBUTE}] .ytp-pause-overlay,
[${CANDY_INLINE_VIDEO_SITE_PLAYER_ATTRIBUTE}] .ytp-title,
[${CANDY_INLINE_VIDEO_SITE_PLAYER_ATTRIBUTE}] .ytp-watermark,
[${CANDY_INLINE_VIDEO_SITE_PLAYER_ATTRIBUTE}] ytm-player-controls,
[${CANDY_INLINE_VIDEO_SITE_PLAYER_ATTRIBUTE}] .player-controls-content,
[${CANDY_INLINE_VIDEO_SITE_PLAYER_ATTRIBUTE}] .player-controls-background,
[${CANDY_INLINE_VIDEO_SITE_PLAYER_ATTRIBUTE}] .ytm-player-bar,
[${CANDY_INLINE_VIDEO_SITE_PLAYER_ATTRIBUTE}] .ytm-custom-control,
[${CANDY_INLINE_VIDEO_SITE_PLAYER_ATTRIBUTE}] .ytm-unmute-button {
  opacity: 0 !important;
  visibility: hidden !important;
  pointer-events: none !important;
}
`;
  document.documentElement.appendChild(style);
  candyPictureInPicturePlayback.inlineSiteStyle = style;
}

function candyInlineVideoControlsParent(video) {
  const fullscreenElement = document.fullscreenElement;
  if (
    fullscreenElement &&
    fullscreenElement !== video &&
    fullscreenElement.contains(video)
  ) return fullscreenElement;
  return document.documentElement;
}

function setCandyInlineFullscreenGestureOffset(gesture, host, offset) {
  if (!gesture?.video?.isConnected || !host?.isConnected) return;
  const boundedOffset = Math.max(0, Number.isFinite(offset) ? offset : 0);
  host.dataset.fullscreenGestureOffset = String(boundedOffset);
  gesture.video.style.setProperty(
    "transform",
    `translate3d(0, ${-boundedOffset.toFixed(2)}px, 0) ${gesture.baseTransform}`,
    "important",
  );
  host.style.setProperty(
    "transform",
    `translate3d(0, ${-boundedOffset.toFixed(2)}px, 0)`,
    "important",
  );
}

function clearCandyInlineFullscreenGestureOffset(gesture, host) {
  if (!gesture) return;
  const video = gesture.video;
  if (video?.style) {
    if (gesture.originalTransform) {
      video.style.setProperty(
        "transform",
        gesture.originalTransform,
        gesture.originalTransformPriority,
      );
    } else {
      video.style.removeProperty("transform");
    }
  }
  if (host) {
    delete host.dataset.fullscreenGestureOffset;
    host.style.setProperty("transform", "none", "important");
  }
}

function removeCandyInlineVideoControlsOverlay() {
  candyPictureInPicturePlayback.inlineControlsCleanup?.();
  candyPictureInPicturePlayback.inlineControlsCleanup = null;
  candyPictureInPicturePlayback.inlineControlsHost?.remove();
  candyPictureInPicturePlayback.inlineControlsHost = null;
  candyPictureInPicturePlayback.inlineControlsVideo = null;
}

function positionCandyInlineVideoControls(video, host) {
  if (!video?.isConnected || !host?.isConnected) return;
  const bounds = video.getBoundingClientRect();
  rememberCandyInlineVideoStableOrigin(video);
  const gestureOffset = Number(host.dataset.fullscreenGestureOffset) || 0;
  const left = Math.max(0, bounds.left);
  const right = Math.min(innerWidth, bounds.right);
  const top = Math.max(0, bounds.top + gestureOffset);
  const bottom = Math.min(innerHeight, bounds.bottom + gestureOffset);
  const width = Math.max(0, right - left);
  const height = Math.max(0, bottom - top);
  if (
    width < CANDY_INLINE_VIDEO_ACTION_SIZE_PX * 2 ||
    height < CANDY_INLINE_VIDEO_CONTROLS_HEIGHT_PX
  ) {
    host.style.setProperty("display", "none", "important");
    return;
  }
  host.style.setProperty("display", "block", "important");
  setCandyInlineActionStyle(host, "left", `${Math.round(left)}px`);
  setCandyInlineActionStyle(host, "top", `${Math.round(top)}px`);
  setCandyInlineActionStyle(host, "width", `${Math.round(width)}px`);
  setCandyInlineActionStyle(host, "height", `${Math.round(height)}px`);
}

function createCandyInlineVideoControlsOverlay(video) {
  removeCandyInlineVideoControlsOverlay();
  if (!document.documentElement || candyPictureInPicturePlayback.expected) return;
  const host = document.createElement("div");
  host.setAttribute(CANDY_INLINE_VIDEO_CONTROLS_ATTRIBUTE, "");
  [
    ["all", "initial"],
    ["position", "fixed"],
    ["display", "block"],
    ["box-sizing", "border-box"],
    ["height", `${CANDY_INLINE_VIDEO_CONTROLS_HEIGHT_PX}px`],
    ["margin", "0"],
    ["padding", "0"],
    ["border", "0"],
    ["visibility", "visible"],
    ["opacity", "1"],
    ["transform", "none"],
    ["pointer-events", "none"],
    ["isolation", "isolate"],
    ["contain", "strict"],
    ["z-index", "2147483647"],
  ].forEach(([property, value]) => setCandyInlineActionStyle(host, property, value));
  const shadow = host.attachShadow({ mode: "closed" });
  const style = document.createElement("style");
  style.textContent = `
:host { all: initial; pointer-events: none; }
.stage {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  color: white;
  font: 500 12px/1.1 system-ui, sans-serif;
  pointer-events: none;
  -webkit-tap-highlight-color: transparent;
}
.fullscreen-gesture {
  position: absolute;
  inset: 0;
  z-index: 0;
  touch-action: none;
  pointer-events: auto;
}
.controls {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  box-sizing: border-box;
  display: grid;
  grid-template-rows: 30px 44px;
  width: 100%;
  height: ${CANDY_INLINE_VIDEO_CONTROLS_HEIGHT_PX}px;
  padding: 0 16px 14px;
  background: linear-gradient(transparent, rgba(7, 5, 14, 0.92));
  pointer-events: none;
  z-index: 2;
}
.timeline {
  position: relative;
  min-width: 0;
  height: 28px;
  pointer-events: auto;
}
.timeline svg,
.timeline input {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}
.timeline svg { overflow: visible; }
.track {
  fill: none;
  stroke: rgba(255, 255, 255, 0.42);
  stroke-linecap: round;
  stroke-width: 3;
  vector-effect: non-scaling-stroke;
}
.progress {
  fill: none;
  stroke: #ff397f;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 5;
  vector-effect: non-scaling-stroke;
  filter: drop-shadow(0 0 4px rgba(255, 57, 127, 0.65));
}
.cursor {
  stroke: white;
  stroke-linecap: round;
  stroke-width: 4;
  vector-effect: non-scaling-stroke;
}
.timeline input {
  margin: 0;
  opacity: 0.001;
  cursor: pointer;
}
.timeline:focus-within { filter: drop-shadow(0 0 5px white); }
.actions {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  pointer-events: none;
}
.pill {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 44px;
  padding: 0 4px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 22px;
  background: rgba(28, 25, 35, 0.78);
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.28);
  backdrop-filter: blur(14px) saturate(1.25);
  pointer-events: auto;
}
.transport {
  border: 0;
  background: linear-gradient(135deg, rgba(255, 47, 124, 0.9), rgba(111, 70, 225, 0.88));
}
.utility { margin-left: auto; }
button {
  all: initial;
  box-sizing: border-box;
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border: 0;
  border-radius: 20px;
  outline: none;
  color: white;
  cursor: pointer;
  font: 750 19px/1 system-ui, sans-serif;
  appearance: none;
  -webkit-appearance: none;
  transition: transform 180ms cubic-bezier(.2, 1.4, .4, 1), background 180ms ease;
  pointer-events: auto;
}
button:active { background: rgba(255, 255, 255, 0.22); transform: scale(0.86) rotate(-3deg); }
button:focus-visible { outline: 3px solid white; outline-offset: 3px; }
.play-pause::before,
.hero-play::after {
  content: "";
  display: block;
}
.play-pause[data-state="play"]::before {
  width: 0;
  height: 0;
  margin-left: 3px;
  border-top: 8px solid transparent;
  border-bottom: 8px solid transparent;
  border-left: 13px solid white;
}
.play-pause[data-state="pause"]::before {
  width: 12px;
  height: 16px;
  background: linear-gradient(
    90deg,
    white 0 4px,
    transparent 4px 8px,
    white 8px 12px
  );
}
.time {
  min-width: 82px;
  padding-right: 8px;
  text-align: right;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.fullscreen { font-size: 21px; }
.close { font-size: 24px; font-weight: 500; }
.hero-play {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 78px;
  height: 78px;
  border-radius: 50%;
  background:
    radial-gradient(circle at 34% 24%, rgba(255,255,255,.22), transparent 30%),
    linear-gradient(145deg, #ff397f 0 54%, #7148e7 55% 100%);
  box-shadow: 0 12px 34px rgba(11, 5, 28, 0.46);
  transform: translate(-50%, -50%);
  animation: candy-hero-breathe 2.8s ease-in-out infinite;
  pointer-events: auto;
  z-index: 1;
}
.hero-play::after {
  width: 0;
  height: 0;
  margin-left: 6px;
  border-top: 15px solid transparent;
  border-bottom: 15px solid transparent;
  border-left: 24px solid white;
  filter: drop-shadow(0 2px 2px rgba(42, 12, 72, 0.25));
}
.hero-play[hidden] { display: none; }
.hero-play:active { transform: translate(-50%, -50%) scale(0.88) rotate(-4deg); }
.hero-play[data-activating="true"] {
  animation: candy-hero-launch 620ms cubic-bezier(.18, .89, .32, 1.28);
}
@keyframes candy-hero-breathe {
  0%, 100% { box-shadow: 0 12px 34px rgba(11,5,28,.46); }
  50% { box-shadow: 0 16px 44px rgba(255,47,124,.34); }
}
@keyframes candy-hero-launch {
  0% { transform: translate(-50%, -50%) scale(1) rotate(0); }
  22% { transform: translate(-50%, -50%) scale(.82) rotate(-10deg); }
  52% {
    box-shadow: 0 0 0 18px rgba(255,57,127,.18), 0 18px 48px rgba(113,72,231,.55);
    transform: translate(-50%, -50%) scale(1.24) rotate(7deg);
  }
  76% { transform: translate(-50%, -50%) scale(.94) rotate(-3deg); }
  100% { transform: translate(-50%, -50%) scale(1) rotate(0); }
}
@media (prefers-reduced-motion: reduce) {
  button, .hero-play, .hero-play[data-activating="true"] {
    animation: none;
    transition: none;
  }
}
`;
  const stage = document.createElement("div");
  stage.className = "stage";
  const fullscreenGesture = document.createElement("div");
  fullscreenGesture.className = "fullscreen-gesture";
  fullscreenGesture.setAttribute("aria-hidden", "true");
  const heroPlay = document.createElement("button");
  heroPlay.type = "button";
  heroPlay.className = "hero-play";
  heroPlay.setAttribute("aria-label", candyPictureInPicturePlayback.inlineMediaPlayerPlayLabel);
  const controls = document.createElement("div");
  controls.className = "controls";
  const timeline = document.createElement("div");
  timeline.className = "timeline";
  const svgNamespace = "http://www.w3.org/2000/svg";
  const timelineSvg = document.createElementNS(svgNamespace, "svg");
  timelineSvg.setAttribute("viewBox", "0 0 1000 32");
  timelineSvg.setAttribute("preserveAspectRatio", "none");
  timelineSvg.setAttribute("aria-hidden", "true");
  const track = document.createElementNS(svgNamespace, "path");
  track.setAttribute("class", "track");
  track.setAttribute("d", "M 0 16 L 1000 16");
  const progress = document.createElementNS(svgNamespace, "path");
  progress.setAttribute("class", "progress");
  const cursor = document.createElementNS(svgNamespace, "line");
  cursor.setAttribute("class", "cursor");
  cursor.setAttribute("y1", "5");
  cursor.setAttribute("y2", "27");
  timelineSvg.append(track, progress, cursor);
  const playPause = document.createElement("button");
  playPause.type = "button";
  playPause.className = "play-pause";
  const seek = document.createElement("input");
  seek.type = "range";
  seek.min = "0";
  seek.max = "1000";
  seek.step = "1";
  seek.value = "0";
  seek.setAttribute("aria-label", candyPictureInPicturePlayback.inlineMediaPlayerSeekLabel);
  const time = document.createElement("span");
  time.className = "time";
  const fullscreen = document.createElement("button");
  fullscreen.type = "button";
  fullscreen.className = "fullscreen";
  fullscreen.textContent = "⛶";
  const close = document.createElement("button");
  close.type = "button";
  close.className = "close";
  close.textContent = "×";
  close.setAttribute(
    "aria-label",
    candyPictureInPicturePlayback.inlineMediaPlayerCloseLabel,
  );
  const actions = document.createElement("div");
  actions.className = "actions";
  const transport = document.createElement("div");
  transport.className = "pill transport";
  const utility = document.createElement("div");
  utility.className = "pill utility";
  let heroActivationTimer = null;
  let activeFullscreenGesture = null;
  const update = () => {
    const playing = !video.paused && !video.ended;
    playPause.dataset.state = playing ? "pause" : "play";
    heroPlay.hidden = playing && heroPlay.dataset.activating !== "true";
    playPause.setAttribute(
      "aria-label",
      playing ? candyPictureInPicturePlayback.inlineMediaPlayerPauseLabel :
        candyPictureInPicturePlayback.inlineMediaPlayerPlayLabel,
    );
    const duration = Number.isFinite(video.duration) && video.duration > 0 ? video.duration : 0;
    seek.disabled = duration <= 0;
    const seekValue = duration > 0 ? Math.round(video.currentTime / duration * 1000) : 0;
    seek.value = String(seekValue);
    progress.setAttribute("d", candyWobblyProgressPath(seekValue));
    cursor.setAttribute("x1", String(seekValue));
    cursor.setAttribute("x2", String(seekValue));
    cursor.style.display = duration > 0 ? "block" : "none";
    time.textContent = `${candyInlineVideoTime(video.currentTime)} / ${candyInlineVideoTime(duration)}`;
    const isFullscreen = Boolean(document.fullscreenElement?.contains(video));
    fullscreen.setAttribute(
      "aria-label",
      isFullscreen ? candyPictureInPicturePlayback.inlineMediaPlayerExitFullscreenLabel :
        candyPictureInPicturePlayback.inlineMediaPlayerEnterFullscreenLabel,
    );
  };
  const finishHeroActivation = () => {
    if (heroActivationTimer !== null) clearTimeout(heroActivationTimer);
    heroActivationTimer = null;
    delete heroPlay.dataset.activating;
    update();
  };
  playPause.addEventListener("click", (event) => {
    event.stopPropagation();
    if (video.paused || video.ended) Promise.resolve(video.play()).catch(() => {});
    else video.pause();
  });
  heroPlay.addEventListener("click", (event) => {
    event.stopPropagation();
    if (video.paused || video.ended) {
      heroPlay.dataset.activating = "true";
      heroActivationTimer = setTimeout(finishHeroActivation, 700);
      Promise.resolve(video.play()).catch(() => {
        finishHeroActivation();
      });
    }
  });
  heroPlay.addEventListener("animationend", (event) => {
    if (event.animationName !== "candy-hero-launch") return;
    finishHeroActivation();
  });
  const seekVideo = (event) => {
    event.stopPropagation();
    if (Number.isFinite(video.duration) && video.duration > 0) {
      video.currentTime = Number(seek.value) / 1000 * video.duration;
      update();
    }
  };
  seek.addEventListener("input", seekVideo);
  seek.addEventListener("change", seekVideo);
  fullscreen.addEventListener("click", async (event) => {
    event.stopPropagation();
    if (!event.isTrusted) return;
    try {
      if (document.fullscreenElement?.contains(video)) {
        await document.exitFullscreen();
      } else {
        await requestCandyInlineVideoFullscreen(video);
      }
    } catch (_) { }
  });
  close.addEventListener("click", (event) => {
    event.stopPropagation();
    if (!event.isTrusted) return;
    requestCandyInlineVideoClose(video);
  });
  ["pointerdown", "pointerup", "click"].forEach((eventName) => {
    controls.addEventListener(eventName, (event) => event.stopPropagation());
  });
  const finishFullscreenGesture = (stopHaptic = true) => {
    const gesture = activeFullscreenGesture;
    activeFullscreenGesture = null;
    clearCandyInlineFullscreenGestureOffset(gesture, host);
    if (stopHaptic && gesture?.rubberbandActive) {
      reportCandyInlineVideoGestureHaptic(video, "rubberband-stop");
    }
  };
  const updateFullscreenGesture = (event, emitHaptic = true) => {
    const gesture = activeFullscreenGesture;
    if (!gesture || gesture.pointerId !== event.pointerId) return null;
    if (gesture.direction === "pending") {
      gesture.direction = candyInlineFullscreenGestureDirection(
        event.clientX - gesture.startX,
        event.clientY - gesture.startY,
        CANDY_INLINE_FULLSCREEN_GESTURE_TOUCH_SLOP_PX,
      );
      if (gesture.direction === "rejected") {
        finishFullscreenGesture();
        return null;
      }
    }
    if (gesture.direction !== "up") return null;
    event.preventDefault();
    event.stopPropagation();
    const update = candyInlineFullscreenGestureUpdate(
      gesture.startY - event.clientY,
      host.getBoundingClientRect().height,
    );
    setCandyInlineFullscreenGestureOffset(gesture, host, update.offset);
    const thresholdChanged = gesture.thresholdReached !== update.shouldCommit;
    gesture.thresholdReached = update.shouldCommit;
    if (emitHaptic && thresholdChanged && update.shouldCommit) {
      if (gesture.rubberbandActive) {
        reportCandyInlineVideoGestureHaptic(video, "rubberband-stop");
      }
      reportCandyInlineVideoGestureHaptic(video, "confirm");
      gesture.rubberbandActive = false;
    } else if (
      emitHaptic &&
      !update.shouldCommit &&
      !gesture.rubberbandActive
    ) {
      reportCandyInlineVideoGestureHaptic(video, "rubberband-start");
      gesture.rubberbandActive = true;
    }
    return { update, thresholdChanged };
  };
  fullscreenGesture.addEventListener("pointerdown", (event) => {
    if (
      !event.isTrusted ||
      event.isPrimary === false ||
      event.pointerType === "mouse" ||
      document.fullscreenElement ||
      !candyPictureInPicturePlayback.inlinePresentationExpected ||
      candyPictureInPicturePlayback.presentedVideo !== video
    ) return;
    finishFullscreenGesture();
    const computedTransform = getComputedStyle(video).transform;
    activeFullscreenGesture = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      video,
      baseTransform: computedTransform === "none" ? "" : computedTransform,
      originalTransform: video.style.getPropertyValue("transform"),
      originalTransformPriority: video.style.getPropertyPriority("transform"),
      direction: "pending",
      thresholdReached: false,
      rubberbandActive: false,
    };
    fullscreenGesture.setPointerCapture?.(event.pointerId);
  });
  fullscreenGesture.addEventListener("pointermove", (event) => {
    updateFullscreenGesture(event);
  }, { passive: false });
  fullscreenGesture.addEventListener("pointerup", (event) => {
    const gesture = activeFullscreenGesture;
    const resolution = updateFullscreenGesture(event, false);
    const shouldCommit = Boolean(
      event.isTrusted &&
      gesture &&
      resolution?.update.shouldCommit,
    );
    let fullscreenRequest = null;
    if (shouldCommit) {
      try {
        // Keep this invocation in the trusted pointer event. Awaiting the native bridge first
        // would lose the transient user activation required by the Fullscreen API.
        fullscreenRequest = requestCandyInlineVideoFullscreen(video);
      } catch (_) { }
      if (resolution.thresholdChanged) {
        if (gesture.rubberbandActive) {
          reportCandyInlineVideoGestureHaptic(video, "rubberband-stop");
          gesture.rubberbandActive = false;
        }
        reportCandyInlineVideoGestureHaptic(video, "confirm");
      }
    }
    finishFullscreenGesture();
    if (fullscreenRequest) {
      Promise.resolve(fullscreenRequest).catch(() => {
        reportCandyInlineVideoGestureHaptic(video, "rubberband-stop");
      });
    }
  }, { passive: false });
  fullscreenGesture.addEventListener("pointercancel", () => finishFullscreenGesture());
  fullscreenGesture.addEventListener("lostpointercapture", () => finishFullscreenGesture());
  const mediaEvents = ["durationchange", "ended", "loadedmetadata", "pause", "play", "timeupdate"];
  mediaEvents.forEach((eventName) => video.addEventListener(eventName, update));
  document.addEventListener("fullscreenchange", update);
  candyPictureInPicturePlayback.inlineControlsCleanup = () => {
    if (heroActivationTimer !== null) clearTimeout(heroActivationTimer);
    finishFullscreenGesture();
    mediaEvents.forEach((eventName) => video.removeEventListener(eventName, update));
    document.removeEventListener("fullscreenchange", update);
  };
  timeline.append(timelineSvg, seek);
  transport.append(playPause, time);
  utility.append(fullscreen, close);
  actions.append(transport, utility);
  controls.append(timeline, actions);
  stage.append(fullscreenGesture, heroPlay, controls);
  shadow.append(style, stage);
  candyInlineVideoControlsParent(video)?.appendChild(host);
  candyPictureInPicturePlayback.inlineControlsHost = host;
  candyPictureInPicturePlayback.inlineControlsVideo = video;
  positionCandyInlineVideoControls(video, host);
  update();
}

function updateCandyInlineVideoControlsOverlay(video) {
  if (
    !candyPictureInPicturePlayback.inlinePresentationExpected ||
    candyPictureInPicturePlayback.expected ||
    candyPictureInPicturePlayback.presentedVideo !== video
  ) {
    removeCandyInlineVideoControlsOverlay();
    return;
  }
  suppressCandyInlineVideoSiteControls(video);
  if (
    candyPictureInPicturePlayback.inlineControlsVideo !== video ||
    !candyPictureInPicturePlayback.inlineControlsHost?.isConnected
  ) {
    createCandyInlineVideoControlsOverlay(video);
    return;
  }
  const parent = candyInlineVideoControlsParent(video);
  if (parent && candyPictureInPicturePlayback.inlineControlsHost.parentElement !== parent) {
    parent.appendChild(candyPictureInPicturePlayback.inlineControlsHost);
  }
  positionCandyInlineVideoControls(
    video,
    candyPictureInPicturePlayback.inlineControlsHost,
  );
}

function updateCandyPictureInPictureVideoControls(video, expected) {
  if (!video) return;
  if (expected) {
    if (!candyPictureInPictureOriginalControls.has(video)) {
      candyPictureInPictureOriginalControls.set(video, video.controls);
    }
    video.controls = false;
    return;
  }
  if (!candyPictureInPictureOriginalControls.has(video)) return;
  video.controls = candyPictureInPictureOriginalControls.get(video);
  candyPictureInPictureOriginalControls.delete(video);
}

function clearCandyPictureInPictureVideoPresentation(video) {
  video?.removeAttribute(CANDY_PICTURE_IN_PICTURE_VIDEO_ATTRIBUTE);
  video?.style.removeProperty(CANDY_PICTURE_IN_PICTURE_OFFSET_X);
  video?.style.removeProperty(CANDY_PICTURE_IN_PICTURE_OFFSET_Y);
  updateCandyPictureInPictureVideoControls(video, false);
}

function clearCandyPictureInPicturePresentation() {
  if (candyPictureInPicturePlayback.alignmentFrame !== null) {
    cancelAnimationFrame(candyPictureInPicturePlayback.alignmentFrame);
    candyPictureInPicturePlayback.alignmentFrame = null;
  }
  if (candyPictureInPicturePlayback.alignmentMonitorFrame !== null) {
    cancelAnimationFrame(candyPictureInPicturePlayback.alignmentMonitorFrame);
    candyPictureInPicturePlayback.alignmentMonitorFrame = null;
  }
  candyPictureInPicturePlayback.layoutObserver?.disconnect();
  candyPictureInPicturePlayback.layoutObserver = null;
  candyPictureInPicturePlayback.resizeObserver?.disconnect();
  candyPictureInPicturePlayback.resizeObserver = null;
  candyPictureInPicturePlayback.pictureInPictureAncestors.forEach((element) => {
    element.removeAttribute(CANDY_PICTURE_IN_PICTURE_ANCESTOR_ATTRIBUTE);
  });
  candyPictureInPicturePlayback.pictureInPictureAncestors = [];
  const presentedVideos = new Set(document.querySelectorAll(
    `video[${CANDY_PICTURE_IN_PICTURE_VIDEO_ATTRIBUTE}]`,
  ));
  if (candyPictureInPicturePlayback.presentedVideo) {
    presentedVideos.add(candyPictureInPicturePlayback.presentedVideo);
  }
  presentedVideos.forEach(clearCandyPictureInPictureVideoPresentation);
  if (!candyPictureInPicturePlayback.inlinePresentationExpected) {
    candyPictureInPicturePlayback.presentedVideo = null;
  }
  document.documentElement?.removeAttribute(CANDY_PICTURE_IN_PICTURE_ROOT_ATTRIBUTE);
  document.querySelector(
    `style[${CANDY_PICTURE_IN_PICTURE_STYLE_ATTRIBUTE}]`,
  )?.remove();
}

function clearCandyInlineVideoPresentation() {
  candyPictureInPicturePlayback.inlineControlsObserver?.disconnect();
  candyPictureInPicturePlayback.inlineControlsObserver = null;
  candyPictureInPicturePlayback.inlinePresentationResizeObserver?.disconnect();
  candyPictureInPicturePlayback.inlinePresentationResizeObserver = null;
  candyPictureInPicturePlayback.inlinePresentationLayoutObserver?.disconnect();
  candyPictureInPicturePlayback.inlinePresentationLayoutObserver = null;
  removeCandyInlineVideoControlsOverlay();
  clearCandyInlineVideoSiteControls();
  clearCandyInlineVideoControls(candyPictureInPicturePlayback.presentedVideo);
  candyPictureInPicturePlayback.inlinePresentationExpected = false;
  if (!candyPictureInPicturePlayback.expected) {
    candyPictureInPicturePlayback.presentedVideo = null;
  }
}

function presentCandyInlineVideo(video) {
  if (!isCandyInlineVideoCandidate(video)) return false;
  clearCandyInlineVideoOpenRequest();
  const previousVideo = candyPictureInPicturePlayback.presentedVideo;
  if (previousVideo !== video) {
    clearCandyInlineVideoControls(previousVideo);
    clearCandyInlineVideoFullscreenOrigin();
    candyPictureInPicturePlayback.inlineStableOrigin = null;
  }
  candyPictureInPicturePlayback.inlineControlsObserver?.disconnect();
  candyPictureInPicturePlayback.presentedVideo = video;
  candyPictureInPicturePlayback.inlinePresentationExpected = true;
  if (!candyInlineVideoOriginalControls.has(video)) {
    candyInlineVideoOriginalControls.set(video, video.controls);
  }
  candyPictureInPicturePlayback.inlineControlsObserver = new MutationObserver(() => {
    if (
      candyPictureInPicturePlayback.inlinePresentationExpected &&
      candyPictureInPicturePlayback.presentedVideo === video &&
      video.isConnected &&
      video.controls
    ) {
      video.controls = false;
    }
  });
  candyPictureInPicturePlayback.inlineControlsObserver.observe(video, {
    attributes: true,
    attributeFilter: ["controls"],
  });
  candyPictureInPicturePlayback.inlinePresentationResizeObserver = new ResizeObserver(
    scheduleCandyInlineVideoStateReport,
  );
  candyPictureInPicturePlayback.inlinePresentationResizeObserver.observe(video);
  candyPictureInPicturePlayback.inlinePresentationLayoutObserver = new MutationObserver(
    scheduleCandyInlineVideoStateReport,
  );
  for (let element = video; element; element = element.parentElement) {
    candyPictureInPicturePlayback.inlinePresentationLayoutObserver.observe(element, {
      attributes: true,
      attributeFilter: ["class", "hidden", "style"],
    });
  }
  video.controls = false;
  suppressCandyInlineVideoSiteControls(video);
  createCandyInlineVideoControlsOverlay(video);
  reportCandyInlineVideoState();
  return true;
}

function isCandyInlineVideoCandidate(video) {
  if (!video || !video.isConnected || video.ended) return false;
  const style = getComputedStyle(video);
  if (
    style.display === "none" ||
    style.visibility === "hidden" ||
    style.opacity === "0" ||
    style.contentVisibility === "hidden"
  ) return false;
  const bounds = video.getBoundingClientRect();
  const minimumVisibleSize = CANDY_INLINE_VIDEO_ACTION_SIZE_PX +
    CANDY_INLINE_VIDEO_ACTION_INSET_PX * 2;
  const visibleWidth = Math.min(bounds.right, innerWidth) - Math.max(bounds.left, 0);
  const visibleHeight = Math.min(bounds.bottom, innerHeight) - Math.max(bounds.top, 0);
  return visibleWidth >= minimumVisibleSize && visibleHeight >= minimumVisibleSize;
}

function currentCandyPictureInPictureVideo() {
  return Array.from(candyPictureInPicturePlayback.candidates)
    .filter(isCandyInlineVideoCandidate)
    .sort((first, second) => {
      const playbackDifference = Number(first.paused) - Number(second.paused);
      if (playbackDifference !== 0) return playbackDifference;
      return second.clientWidth * second.clientHeight - first.clientWidth * first.clientHeight;
    })[0] || null;
}

function candyInlineVideoForIdentity(documentNonce, elementNonce) {
  if (documentNonce !== candyInlineVideoDocumentNonce) return null;
  return Array.from(candyPictureInPicturePlayback.candidates).find((video) =>
    isCandyInlineVideoCandidate(video) && candyInlineVideoElementNonce(video) === elementNonce,
  ) || null;
}

function removeCandyInlineVideoAction() {
  candyPictureInPicturePlayback.inlineActionResizeObserver?.disconnect();
  candyPictureInPicturePlayback.inlineActionResizeObserver = null;
  candyPictureInPicturePlayback.inlineActionLayoutObserver?.disconnect();
  candyPictureInPicturePlayback.inlineActionLayoutObserver = null;
  candyPictureInPicturePlayback.inlineActionHost?.remove();
  candyPictureInPicturePlayback.inlineActionHost = null;
  candyPictureInPicturePlayback.inlineActionVideo = null;
}

function clearCandyInlineVideoOpenRequest() {
  if (candyPictureInPicturePlayback.inlineOpenRequestTimer !== null) {
    clearTimeout(candyPictureInPicturePlayback.inlineOpenRequestTimer);
    candyPictureInPicturePlayback.inlineOpenRequestTimer = null;
  }
  candyPictureInPicturePlayback.inlineOpenRequestKey = null;
}

function clearCandyInlineVideoFullscreenOrigin() {
  const origin = candyPictureInPicturePlayback.inlineFullscreenOrigin;
  if (!origin) return;
  if (origin.frame !== null) cancelAnimationFrame(origin.frame);
  if (origin.mismatchTimer !== null) clearTimeout(origin.mismatchTimer);
  origin.resizeObserver?.disconnect();
  origin.player.removeAttribute(CANDY_INLINE_VIDEO_FULLSCREEN_ORIGIN_ATTRIBUTE);
  origin.player.style.removeProperty(CANDY_INLINE_VIDEO_FULLSCREEN_ORIGIN_TOP);
  origin.player.style.removeProperty(CANDY_INLINE_VIDEO_FULLSCREEN_ORIGIN_LEFT);
  origin.video.removeAttribute(CANDY_INLINE_VIDEO_ORIGIN_ATTRIBUTE);
  origin.video.style.removeProperty(CANDY_INLINE_VIDEO_ORIGIN_X);
  origin.video.style.removeProperty(CANDY_INLINE_VIDEO_ORIGIN_Y);
  origin.video.style.removeProperty(CANDY_INLINE_VIDEO_ORIGIN_SCALE_X);
  origin.video.style.removeProperty(CANDY_INLINE_VIDEO_ORIGIN_SCALE_Y);
  origin.style.remove();
  candyPictureInPicturePlayback.inlineFullscreenOrigin = null;
}

function updateCandyInlineVideoFullscreenOriginVisibility() {
  const origin = candyPictureInPicturePlayback.inlineFullscreenOrigin;
  if (!origin) return;
  if (document.fullscreenElement || candyPictureInPicturePlayback.expected) {
    origin.player.removeAttribute(CANDY_INLINE_VIDEO_FULLSCREEN_ORIGIN_ATTRIBUTE);
    origin.video.removeAttribute(CANDY_INLINE_VIDEO_ORIGIN_ATTRIBUTE);
  } else {
    origin.player.setAttribute(CANDY_INLINE_VIDEO_FULLSCREEN_ORIGIN_ATTRIBUTE, "");
    if (origin.videoAdjusted) origin.video.setAttribute(CANDY_INLINE_VIDEO_ORIGIN_ATTRIBUTE, "");
  }
}

function candyInlineVideoOriginLayoutMatches(origin) {
  const bounds = origin.player.getBoundingClientRect();
  const parentBounds = origin.parent?.getBoundingClientRect();
  return Math.abs(innerWidth - origin.viewportWidth) <= 2 &&
    Math.abs(innerHeight - origin.viewportHeight) <= 96 &&
    bounds.width > 0 && bounds.height > 0 &&
    Math.abs(bounds.width - origin.bounds.width) <= 2 &&
    Math.abs(bounds.height - origin.bounds.height) <= 2 &&
    (!origin.parentBounds || !parentBounds || (
      Math.abs(parentBounds.width - origin.parentBounds.width) <= 2 &&
      Math.abs(parentBounds.height - origin.parentBounds.height) <= 2
    ));
}

function reconcileCandyInlineVideoFullscreenOrigin() {
  const origin = candyPictureInPicturePlayback.inlineFullscreenOrigin;
  if (!origin || document.fullscreenElement || candyPictureInPicturePlayback.expected) return;
  if (!origin.player.isConnected || origin.url !== location.href ||
      origin.player.parentElement !== origin.parent) {
    clearCandyInlineVideoFullscreenOrigin();
    return;
  }
  const playerLayoutMatches = candyInlineVideoOriginLayoutMatches(origin);
  if (!playerLayoutMatches) {
    origin.player.removeAttribute(CANDY_INLINE_VIDEO_FULLSCREEN_ORIGIN_ATTRIBUTE);
    origin.video.removeAttribute(CANDY_INLINE_VIDEO_ORIGIN_ATTRIBUTE);
    if (origin.mismatchTimer === null) {
      origin.mismatchTimer = setTimeout(() => {
        origin.mismatchTimer = null;
        if (candyPictureInPicturePlayback.inlineFullscreenOrigin === origin &&
            !candyPictureInPicturePlayback.expected && !document.fullscreenElement &&
            !candyInlineVideoOriginLayoutMatches(origin)) clearCandyInlineVideoFullscreenOrigin();
      }, 5_000);
    }
    return;
  } else if (origin.mismatchTimer !== null) {
    clearTimeout(origin.mismatchTimer);
    origin.mismatchTimer = null;
  }
  updateCandyInlineVideoFullscreenOriginVisibility();
  if (playerLayoutMatches) {
    const bounds = origin.player.getBoundingClientRect();
    const scaleY = bounds.height / origin.player.offsetHeight;
    const scaleX = bounds.width / origin.player.offsetWidth;
    if (Number.isFinite(scaleY) && scaleY > 0 && origin.top !== null) {
      const deltaY = (origin.bounds.top + origin.scrollY - scrollY - bounds.top) / scaleY;
      if (Math.abs(deltaY) >= 0.5) {
        origin.top += deltaY;
        origin.player.style.setProperty(
          CANDY_INLINE_VIDEO_FULLSCREEN_ORIGIN_TOP, `${origin.top}px`,
        );
      }
    }
    if (Number.isFinite(scaleX) && scaleX > 0 && origin.left !== null) {
      const deltaX = (origin.bounds.left + origin.scrollX - scrollX - bounds.left) / scaleX;
      if (Math.abs(deltaX) >= 0.5) {
        origin.left += deltaX;
        origin.player.style.setProperty(
          CANDY_INLINE_VIDEO_FULLSCREEN_ORIGIN_LEFT, `${origin.left}px`,
        );
      }
    }
  }
  if (Math.abs(innerWidth - origin.viewportWidth) > 2 ||
      Math.abs(innerHeight - origin.viewportHeight) > 96 ||
      !origin.video.isConnected) return;
  const target = origin.videoBounds;
  let videoBounds = origin.video.getBoundingClientRect();
  if (videoBounds.width <= 0 || videoBounds.height <= 0) return;
  const targetLeft = target.left + origin.scrollX - scrollX;
  const targetTop = target.top + origin.scrollY - scrollY;
  if ([targetLeft - videoBounds.left, targetTop - videoBounds.top,
    target.width - videoBounds.width, target.height - videoBounds.height]
    .every((delta) => Math.abs(delta) < 0.5)) return;
  if (!origin.videoAdjusted) {
    origin.videoAdjusted = true;
    origin.video.style.setProperty(CANDY_INLINE_VIDEO_ORIGIN_X, "0px");
    origin.video.style.setProperty(CANDY_INLINE_VIDEO_ORIGIN_Y, "0px");
    origin.video.style.setProperty(CANDY_INLINE_VIDEO_ORIGIN_SCALE_X, "1");
    origin.video.style.setProperty(CANDY_INLINE_VIDEO_ORIGIN_SCALE_Y, "1");
    origin.video.setAttribute(CANDY_INLINE_VIDEO_ORIGIN_ATTRIBUTE, "");
    videoBounds = origin.video.getBoundingClientRect();
  }
  if (Math.abs(target.width - videoBounds.width) >= 0.5) {
    origin.videoScaleX *= target.width / videoBounds.width;
    origin.video.style.setProperty(CANDY_INLINE_VIDEO_ORIGIN_SCALE_X, String(origin.videoScaleX));
  }
  if (Math.abs(target.height - videoBounds.height) >= 0.5) {
    origin.videoScaleY *= target.height / videoBounds.height;
    origin.video.style.setProperty(CANDY_INLINE_VIDEO_ORIGIN_SCALE_Y, String(origin.videoScaleY));
  }
  videoBounds = origin.video.getBoundingClientRect();
  const videoScaleX = videoBounds.width / (origin.video.offsetWidth * origin.videoScaleX);
  const videoScaleY = videoBounds.height / (origin.video.offsetHeight * origin.videoScaleY);
  if (Number.isFinite(videoScaleX) && videoScaleX > 0) {
    const deltaX = (targetLeft - videoBounds.left) / videoScaleX;
    if (Math.abs(deltaX) >= 0.5) {
      origin.videoX += deltaX;
      origin.video.style.setProperty(CANDY_INLINE_VIDEO_ORIGIN_X, `${origin.videoX}px`);
    }
  }
  if (Number.isFinite(videoScaleY) && videoScaleY > 0) {
    const deltaY = (targetTop - videoBounds.top) / videoScaleY;
    if (Math.abs(deltaY) >= 0.5) {
      origin.videoY += deltaY;
      origin.video.style.setProperty(
        CANDY_INLINE_VIDEO_ORIGIN_Y, `${origin.videoY}px`,
      );
    }
  }
}

function restoreCandyInlineVideoFullscreenOrigin() {
  const origin = candyPictureInPicturePlayback.inlineFullscreenOrigin;
  if (!origin) return;
  updateCandyInlineVideoFullscreenOriginVisibility();
  if (origin.frame !== null) cancelAnimationFrame(origin.frame);
  const deadline = performance.now() + 5_000;
  const reconcile = () => {
    if (candyPictureInPicturePlayback.inlineFullscreenOrigin !== origin) return;
    origin.frame = null;
    reconcileCandyInlineVideoFullscreenOrigin();
    if (performance.now() < deadline &&
        candyPictureInPicturePlayback.inlineFullscreenOrigin === origin) {
      origin.frame = requestAnimationFrame(reconcile);
    }
  };
  reconcile();
}

function preserveCandyInlineVideoFullscreenOrigin(video, player) {
  if (!candyUsesBackgroundVideoVisibilityFix || !player?.isConnected ||
      candyPictureInPicturePlayback.expected || document.fullscreenElement) return;
  const previous = candyPictureInPicturePlayback.inlineFullscreenOrigin;
  if (previous?.player === player && previous.video === video &&
      previous.url === location.href && previous.parent === player.parentElement) {
    return;
  }
  clearCandyInlineVideoFullscreenOrigin();
  const stable = candyPictureInPicturePlayback.inlineStableOrigin;
  const snapshot = stable?.video === video && stable.player === player &&
      stable.parent === player.parentElement && stable.url === location.href ? stable : null;
  const style = getComputedStyle(player);
  const top = snapshot?.top ?? Number.parseFloat(style.top);
  const left = snapshot?.left ?? Number.parseFloat(style.left);
  const bounds = snapshot?.playerBounds || player.getBoundingClientRect();
  const videoBounds = snapshot?.videoBounds || video.getBoundingClientRect();
  if (bounds.width <= 0 || bounds.height <= 0 ||
      videoBounds.width <= 0 || videoBounds.height <= 0 ||
      !document.documentElement) return;
  if (Number.isFinite(top)) {
    player.style.setProperty(CANDY_INLINE_VIDEO_FULLSCREEN_ORIGIN_TOP, `${top}px`);
  }
  if (Number.isFinite(left)) {
    player.style.setProperty(CANDY_INLINE_VIDEO_FULLSCREEN_ORIGIN_LEFT, `${left}px`);
  }
  player.setAttribute(CANDY_INLINE_VIDEO_FULLSCREEN_ORIGIN_ATTRIBUTE, "");
  const originStyle = document.createElement("style");
  originStyle.setAttribute(CANDY_INLINE_VIDEO_FULLSCREEN_ORIGIN_STYLE_ATTRIBUTE, "");
  // Keep the visible player box in place even when YouTube moves its parent later.
  originStyle.textContent = `
[${CANDY_INLINE_VIDEO_FULLSCREEN_ORIGIN_ATTRIBUTE}]:not(:fullscreen) {
  top: var(${CANDY_INLINE_VIDEO_FULLSCREEN_ORIGIN_TOP}) !important;
  left: var(${CANDY_INLINE_VIDEO_FULLSCREEN_ORIGIN_LEFT}) !important;
}
video[${CANDY_INLINE_VIDEO_ORIGIN_ATTRIBUTE}] {
  translate: var(${CANDY_INLINE_VIDEO_ORIGIN_X}) var(${CANDY_INLINE_VIDEO_ORIGIN_Y}) !important;
  scale: var(${CANDY_INLINE_VIDEO_ORIGIN_SCALE_X}) var(${CANDY_INLINE_VIDEO_ORIGIN_SCALE_Y}) !important;
  transform-origin: top left !important;
}`;
  document.documentElement.appendChild(originStyle);
  candyPictureInPicturePlayback.inlineFullscreenOrigin = {
    video,
    player,
    style: originStyle,
    url: location.href,
    bounds: { left: bounds.left, top: bounds.top, width: bounds.width, height: bounds.height },
    videoBounds: {
      left: videoBounds.left, top: videoBounds.top,
      width: videoBounds.width, height: videoBounds.height,
    },
    parent: player.parentElement,
    parentBounds: snapshot?.parentBounds || player.parentElement?.getBoundingClientRect() || null,
    viewportWidth: snapshot?.viewportWidth ?? innerWidth,
    viewportHeight: snapshot?.viewportHeight ?? innerHeight,
    scrollX: snapshot?.scrollX ?? scrollX,
    scrollY: snapshot?.scrollY ?? scrollY,
    top: Number.isFinite(top) ? top : null,
    left: Number.isFinite(left) ? left : null,
    videoX: 0,
    videoY: 0,
    videoScaleX: 1,
    videoScaleY: 1,
    videoAdjusted: false,
    frame: null,
    mismatchTimer: null,
    resizeObserver: null,
  };
  if (typeof ResizeObserver === "function") {
    const origin = candyPictureInPicturePlayback.inlineFullscreenOrigin;
    origin.resizeObserver = new ResizeObserver(reconcileCandyInlineVideoFullscreenOrigin);
    origin.resizeObserver.observe(player);
    if (origin.parent) origin.resizeObserver.observe(origin.parent);
  }
}

function requestCandyInlineVideoFullscreen(video) {
  if (!video || document.fullscreenElement?.contains(video)) return null;
  const sitePlayer = candyInlineVideoSitePlayer(video);
  const target = sitePlayer || video.parentElement || video;
  if (typeof target.requestFullscreen !== "function") return null;
  if (sitePlayer) preserveCandyInlineVideoFullscreenOrigin(video, sitePlayer);
  try {
    return Promise.resolve(target.requestFullscreen()).catch((error) => {
      if (!document.fullscreenElement) clearCandyInlineVideoFullscreenOrigin();
      throw error;
    });
  } catch (error) {
    clearCandyInlineVideoFullscreenOrigin();
    throw error;
  }
}

function reportCandyInlineVideoGestureHaptic(video, phase) {
  if (
    !candyPictureInPicturePlayback.inlineMediaPlayerEnabled ||
    !candyPictureInPicturePlayback.inlinePresentationExpected ||
    candyPictureInPicturePlayback.presentedVideo !== video
  ) return;
  browser.runtime.sendMessage({
    type: "inline-video-gesture-haptic",
    phase,
    revision: candyPictureInPicturePlayback.inlineMediaPolicyRevision,
    navigationGeneration: candyPictureInPicturePlayback.inlineMediaNavigationGeneration,
    documentNonce: candyInlineVideoDocumentNonce,
    elementNonce: candyInlineVideoElementNonce(video),
  }).catch(() => {});
}

async function requestCandyInlineVideoOpen(video, enterFullscreen = false) {
  if (
    !candyPictureInPicturePlayback.inlineMediaPlayerEnabled ||
    candyVideoPresentationExpected() ||
    !isCandyInlineVideoCandidate(video)
  ) return false;
  const elementNonce = candyInlineVideoElementNonce(video);
  const requestKey = `${candyInlineVideoDocumentNonce}:${elementNonce}`;
  if (candyPictureInPicturePlayback.inlineOpenRequestKey === requestKey) return false;
  candyPictureInPicturePlayback.inlineOpenRequestKey = requestKey;
  try {
    if (enterFullscreen && !document.fullscreenElement) {
      const fullscreenRequest = requestCandyInlineVideoFullscreen(video);
      if (!fullscreenRequest) throw new Error("fullscreen unavailable");
      await fullscreenRequest;
    }
    await reportCandyInlineVideoState(video);
    const response = await browser.runtime.sendMessage({
      type: "inline-video-open-request",
      revision: candyPictureInPicturePlayback.inlineMediaPolicyRevision,
      navigationGeneration: candyPictureInPicturePlayback.inlineMediaNavigationGeneration,
      documentNonce: candyInlineVideoDocumentNonce,
      elementNonce,
    });
    if (response?.forwarded !== true) {
      clearCandyInlineVideoOpenRequest();
      return false;
    }
    candyPictureInPicturePlayback.inlineOpenRequestTimer = setTimeout(() => {
      candyPictureInPicturePlayback.inlineOpenRequestTimer = null;
      candyPictureInPicturePlayback.inlineOpenRequestKey = null;
    }, 3000);
    return true;
  } catch (_) {
    clearCandyInlineVideoOpenRequest();
    return false;
  }
}

async function requestCandyInlineVideoClose(video) {
  if (
    !candyPictureInPicturePlayback.inlinePresentationExpected ||
    candyPictureInPicturePlayback.presentedVideo !== video
  ) return false;
  try {
    const response = await browser.runtime.sendMessage({
      type: "inline-video-open-request",
      expected: false,
      revision: candyPictureInPicturePlayback.inlineMediaPolicyRevision,
      navigationGeneration: candyPictureInPicturePlayback.inlineMediaNavigationGeneration,
      documentNonce: candyInlineVideoDocumentNonce,
      elementNonce: candyInlineVideoElementNonce(video),
    });
    if (response?.forwarded !== true) return false;
    clearCandyInlineVideoPresentation();
    reportCandyInlineVideoState(video);
    return true;
  } catch (_) {
    return false;
  }
}

function setCandyInlineActionStyle(element, property, value) {
  element.style.setProperty(property, value, "important");
}

function createCandyInlineVideoAction(video) {
  removeCandyInlineVideoAction();
  if (!document.documentElement) return null;
  const host = document.createElement("div");
  host.setAttribute(CANDY_INLINE_VIDEO_ACTION_ATTRIBUTE, "");
  [
    ["all", "initial"],
    ["position", "fixed"],
    ["display", "block"],
    ["box-sizing", "border-box"],
    ["width", `${CANDY_INLINE_VIDEO_ACTION_SIZE_PX}px`],
    ["height", `${CANDY_INLINE_VIDEO_ACTION_SIZE_PX}px`],
    ["margin", "0"],
    ["padding", "0"],
    ["border", "0"],
    ["visibility", "visible"],
    ["opacity", "1"],
    ["transform", "none"],
    ["pointer-events", "auto"],
    ["isolation", "isolate"],
    ["z-index", "2147483647"],
  ].forEach(([property, value]) => setCandyInlineActionStyle(host, property, value));
  const shadow = host.attachShadow({ mode: "closed" });
  const style = document.createElement("style");
  style.textContent = `
:host { all: initial; overflow: visible; }
button {
  all: initial;
  box-sizing: border-box;
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  margin: 2px;
  border: 0;
  border-radius: 17px;
  outline: none;
  background:
    radial-gradient(circle at 34% 24%, rgba(255,255,255,.24), transparent 30%),
    linear-gradient(145deg, #ff397f 0 54%, #7148e7 55% 100%);
  color: white;
  box-shadow: 0 7px 16px rgba(25, 5, 52, 0.42);
  cursor: pointer;
  appearance: none;
  -webkit-appearance: none;
  -webkit-tap-highlight-color: transparent;
  transition: transform 180ms cubic-bezier(.2, 1.4, .4, 1), filter 180ms ease;
}
button::after {
  content: "";
  width: 0;
  height: 0;
  margin-left: 5px;
  border-top: 11px solid transparent;
  border-bottom: 11px solid transparent;
  border-left: 18px solid white;
  filter: drop-shadow(0 2px 2px rgba(42, 12, 72, 0.24));
}
button:active { transform: scale(0.84) rotate(-4deg); filter: saturate(1.2); }
button:focus-visible { outline: 3px solid white; outline-offset: 3px; }
button:disabled { cursor: wait; opacity: 0.72; }
button[data-loading="true"]::after {
  width: 18px;
  height: 18px;
  margin: 0;
  border: 3px solid rgba(255,255,255,.38);
  border-top-color: white;
  border-radius: 50%;
  animation: candy-action-spin .7s linear infinite;
}
@keyframes candy-action-spin { to { rotate: 360deg; } }
@media (prefers-reduced-motion: reduce) {
  button { animation: none; transition: none; }
}
`;
  const button = document.createElement("button");
  button.type = "button";
  button.title = candyPictureInPicturePlayback.inlineMediaPlayerActionLabel;
  button.setAttribute(
    "aria-label",
    candyPictureInPicturePlayback.inlineMediaPlayerActionLabel,
  );
  ["pointerdown", "pointerup"].forEach((eventName) => {
    button.addEventListener(eventName, (event) => event.stopPropagation());
  });
  button.addEventListener("click", async (event) => {
    event.stopPropagation();
    if (
      !event.isTrusted ||
      !candyPictureInPicturePlayback.inlineMediaPlayerEnabled ||
      candyVideoPresentationExpected() ||
      !isCandyInlineVideoActionClick(event, video, host)
    ) return;
    button.disabled = true;
    button.dataset.loading = "true";
    button.setAttribute("aria-busy", "true");
    if (video.paused) Promise.resolve(video.play()).catch(() => {});
    const opened = await requestCandyInlineVideoOpen(
      video,
      candyPictureInPicturePlayback.inlineMediaPlayerMode === "button_fullscreen",
    );
    if (opened) {
      setTimeout(() => {
        if (!host.isConnected) return;
        button.disabled = false;
        delete button.dataset.loading;
        button.removeAttribute("aria-busy");
      }, 3000);
      return;
    }
    button.disabled = false;
    delete button.dataset.loading;
    button.removeAttribute("aria-busy");
  });
  shadow.append(style, button);
  document.documentElement.appendChild(host);
  candyPictureInPicturePlayback.inlineActionHost = host;
  candyPictureInPicturePlayback.inlineActionVideo = video;
  candyPictureInPicturePlayback.inlineActionResizeObserver = new ResizeObserver(
    scheduleCandyInlineVideoStateReport,
  );
  candyPictureInPicturePlayback.inlineActionResizeObserver.observe(video);
  candyPictureInPicturePlayback.inlineActionLayoutObserver = new MutationObserver(
    scheduleCandyInlineVideoStateReport,
  );
  for (let element = video; element; element = element.parentElement) {
    candyPictureInPicturePlayback.inlineActionLayoutObserver.observe(element, {
      attributes: true,
      attributeFilter: ["class", "hidden", "style"],
    });
  }
  return host;
}

function candyInlineVideoActionPosition(video) {
  const bounds = video.getBoundingClientRect();
  return {
    left: Math.max(0, Math.min(
      innerWidth - CANDY_INLINE_VIDEO_ACTION_SIZE_PX,
      bounds.right - CANDY_INLINE_VIDEO_ACTION_SIZE_PX - CANDY_INLINE_VIDEO_ACTION_INSET_PX,
    )),
    top: Math.max(0, Math.min(
      innerHeight - CANDY_INLINE_VIDEO_ACTION_SIZE_PX,
      bounds.top + CANDY_INLINE_VIDEO_ACTION_INSET_PX,
    )),
  };
}

function isCandyInlineVideoActionClick(event, video, host) {
  if (!isCandyInlineVideoCandidate(video) || !host?.isConnected) return false;
  const position = candyInlineVideoActionPosition(video);
  const hostBounds = host.getBoundingClientRect();
  const tolerance = 2;
  return Math.abs(hostBounds.left - position.left) <= tolerance &&
    Math.abs(hostBounds.top - position.top) <= tolerance &&
    Math.abs(hostBounds.width - CANDY_INLINE_VIDEO_ACTION_SIZE_PX) <= tolerance &&
    Math.abs(hostBounds.height - CANDY_INLINE_VIDEO_ACTION_SIZE_PX) <= tolerance &&
    event.clientX >= hostBounds.left &&
    event.clientX <= hostBounds.right &&
    event.clientY >= hostBounds.top &&
    event.clientY <= hostBounds.bottom;
}

function updateCandyInlineVideoAction(video) {
  if (
    !video ||
    candyVideoPresentationExpected() ||
    !candyPictureInPicturePlayback.inlineMediaPlayerEnabled ||
    !candyInlineMediaPlayerShowsButton()
  ) {
    removeCandyInlineVideoAction();
    return;
  }
  let host = candyPictureInPicturePlayback.inlineActionHost;
  if (
    candyPictureInPicturePlayback.inlineActionVideo !== video ||
    !host?.isConnected
  ) {
    host = createCandyInlineVideoAction(video);
  }
  if (!host) return;
  const position = candyInlineVideoActionPosition(video);
  setCandyInlineActionStyle(host, "left", `${Math.round(position.left)}px`);
  setCandyInlineActionStyle(host, "top", `${Math.round(position.top)}px`);
}

function reportCandyInlineVideoState(preferredVideo = null) {
  if (!candyPictureInPicturePlayback.inlineMediaPlayerEnabled) {
    return Promise.resolve();
  }
  const presentedVideo = candyPictureInPicturePlayback.presentedVideo;
  const video = candyPictureInPicturePlayback.inlinePresentationExpected &&
    isCandyInlineVideoCandidate(presentedVideo) ?
    presentedVideo : isCandyInlineVideoCandidate(preferredVideo) ?
      preferredVideo : currentCandyPictureInPictureVideo();
  updateCandyInlineVideoAction(video);
  updateCandyInlineVideoControlsOverlay(video);
  const width = video ? Math.max(0, Math.round(video.videoWidth || video.clientWidth || 0)) : 0;
  const height = video ? Math.max(0, Math.round(video.videoHeight || video.clientHeight || 0)) : 0;
  const area = video ? Math.max(0, Math.round(video.clientWidth * video.clientHeight)) : 0;
  const report = browser.runtime.sendMessage({
    type: "inline-video-state",
    revision: candyPictureInPicturePlayback.inlineMediaPolicyRevision,
    navigationGeneration: candyPictureInPicturePlayback.inlineMediaNavigationGeneration,
    active: Boolean(video),
    playing: Boolean(video && !video.paused && !video.ended),
    presented: Boolean(
      video &&
      candyPictureInPicturePlayback.inlinePresentationExpected &&
      candyPictureInPicturePlayback.presentedVideo === video
    ),
    videoWidth: width,
    videoHeight: height,
    area,
    documentNonce: candyInlineVideoDocumentNonce,
    elementNonce: video ? candyInlineVideoElementNonce(video) : "",
  }).catch(() => {});
  if (
    video &&
    candyInlineMediaPlayerStartsAutomatically() &&
    !candyVideoPresentationExpected() &&
    candyPictureInPicturePlayback.inlineOpenRequestKey === null
  ) {
    return report.then(() => requestCandyInlineVideoOpen(video));
  }
  return report;
}

function clearCandyInlineVideoState() {
  browser.runtime.sendMessage({
    type: "inline-video-state",
    revision: candyPictureInPicturePlayback.inlineMediaPolicyRevision,
    navigationGeneration: candyPictureInPicturePlayback.inlineMediaNavigationGeneration,
    active: false,
    playing: false,
    presented: false,
    videoWidth: 0,
    videoHeight: 0,
    area: 0,
    documentNonce: "",
    elementNonce: "",
  }).catch(() => {});
}

function scheduleCandyInlineVideoStateReport() {
  if (
    !candyPictureInPicturePlayback.inlineMediaPlayerEnabled ||
    candyPictureInPicturePlayback.inlineStateFrame !== null
  ) return;
  candyPictureInPicturePlayback.inlineStateFrame = requestAnimationFrame(() => {
    candyPictureInPicturePlayback.inlineStateFrame = null;
    const origin = candyPictureInPicturePlayback.inlineFullscreenOrigin;
    if (origin && (!origin.player.isConnected || origin.url !== location.href)) {
      clearCandyInlineVideoFullscreenOrigin();
    }
    candyPictureInPicturePlayback.candidates.forEach((video) => {
      if (!video.isConnected || video.ended) {
        candyPictureInPicturePlayback.candidates.delete(video);
      }
    });
    if (
      candyPictureInPicturePlayback.inlinePresentationExpected &&
      (!candyPictureInPicturePlayback.presentedVideo?.isConnected ||
        candyPictureInPicturePlayback.presentedVideo.ended)
    ) {
      clearCandyInlineVideoPresentation();
    }
    rememberCandyPictureInPictureVideos();
    reportCandyInlineVideoState();
  });
}

function stopCandyInlineVideoStateObservation() {
  if (candyPictureInPicturePlayback.inlineStateFrame !== null) {
    cancelAnimationFrame(candyPictureInPicturePlayback.inlineStateFrame);
    candyPictureInPicturePlayback.inlineStateFrame = null;
  }
  candyPictureInPicturePlayback.inlineStateObserver?.disconnect();
  candyPictureInPicturePlayback.inlineStateObserver = null;
}

function startCandyInlineVideoStateObservation() {
  stopCandyInlineVideoStateObservation();
  if (!candyPictureInPicturePlayback.inlineMediaPlayerEnabled) return;
  candyPictureInPicturePlayback.inlineStateObserver = new MutationObserver(
    scheduleCandyInlineVideoStateReport,
  );
  candyPictureInPicturePlayback.inlineStateObserver.observe(document, {
    childList: true,
    subtree: true,
  });
}

function reconcileCandyInlineVideoState() {
  scheduleCandyInlineVideoStateReport();
  [100, 400].forEach((delayMillis) => {
    setTimeout(scheduleCandyInlineVideoStateReport, delayMillis);
  });
}

function updateCandyInlineMediaPlayerEnabled(
  enabled,
  mode,
  revision,
  navigationGeneration,
  actionLabel,
  playLabel,
  pauseLabel,
  seekLabel,
  enterFullscreenLabel,
  exitFullscreenLabel,
  closeLabel,
) {
  const normalized = enabled === true && self === top;
  const normalizedMode = CANDY_INLINE_MEDIA_PLAYER_MODES.has(mode) ?
    mode : "button_fullscreen";
  const normalizedActionLabel =
    typeof actionLabel === "string" && actionLabel.trim() ?
      actionLabel.trim().slice(0, 80) : "Open in Candy Player";
  const normalizedPlayLabel = typeof playLabel === "string" && playLabel.trim() ?
    playLabel.trim().slice(0, 80) : "Play";
  const normalizedPauseLabel = typeof pauseLabel === "string" && pauseLabel.trim() ?
    pauseLabel.trim().slice(0, 80) : "Pause";
  const normalizedSeekLabel = typeof seekLabel === "string" && seekLabel.trim() ?
    seekLabel.trim().slice(0, 80) : "Seek";
  const normalizedEnterFullscreenLabel =
    typeof enterFullscreenLabel === "string" && enterFullscreenLabel.trim() ?
      enterFullscreenLabel.trim().slice(0, 80) : "Enter fullscreen";
  const normalizedExitFullscreenLabel =
    typeof exitFullscreenLabel === "string" && exitFullscreenLabel.trim() ?
      exitFullscreenLabel.trim().slice(0, 80) : "Exit fullscreen";
  const normalizedCloseLabel = typeof closeLabel === "string" && closeLabel.trim() ?
    closeLabel.trim().slice(0, 80) : "Close Candy Player";
  if (
    candyPictureInPicturePlayback.inlineMediaPlayerMode !== normalizedMode ||
    candyPictureInPicturePlayback.inlineMediaPlayerActionLabel !== normalizedActionLabel ||
    candyPictureInPicturePlayback.inlineMediaPlayerPlayLabel !== normalizedPlayLabel ||
    candyPictureInPicturePlayback.inlineMediaPlayerPauseLabel !== normalizedPauseLabel ||
    candyPictureInPicturePlayback.inlineMediaPlayerSeekLabel !== normalizedSeekLabel ||
    candyPictureInPicturePlayback.inlineMediaPlayerEnterFullscreenLabel !==
      normalizedEnterFullscreenLabel ||
    candyPictureInPicturePlayback.inlineMediaPlayerExitFullscreenLabel !==
      normalizedExitFullscreenLabel ||
    candyPictureInPicturePlayback.inlineMediaPlayerCloseLabel !== normalizedCloseLabel
  ) {
    clearCandyInlineVideoOpenRequest();
    removeCandyInlineVideoAction();
    removeCandyInlineVideoControlsOverlay();
  }
  candyPictureInPicturePlayback.inlineMediaPlayerMode = normalizedMode;
  candyPictureInPicturePlayback.inlineMediaPlayerActionLabel = normalizedActionLabel;
  candyPictureInPicturePlayback.inlineMediaPlayerPlayLabel = normalizedPlayLabel;
  candyPictureInPicturePlayback.inlineMediaPlayerPauseLabel = normalizedPauseLabel;
  candyPictureInPicturePlayback.inlineMediaPlayerSeekLabel = normalizedSeekLabel;
  candyPictureInPicturePlayback.inlineMediaPlayerEnterFullscreenLabel =
    normalizedEnterFullscreenLabel;
  candyPictureInPicturePlayback.inlineMediaPlayerExitFullscreenLabel =
    normalizedExitFullscreenLabel;
  candyPictureInPicturePlayback.inlineMediaPlayerCloseLabel = normalizedCloseLabel;
  candyPictureInPicturePlayback.inlineMediaPolicyRevision =
    Number.isSafeInteger(revision) ? Math.max(0, revision) : 0;
  candyPictureInPicturePlayback.inlineMediaNavigationGeneration =
    Number.isSafeInteger(navigationGeneration) ? Math.max(0, navigationGeneration) : 0;
  if (candyPictureInPicturePlayback.inlineMediaPlayerEnabled === normalized) {
    if (normalized) {
      startCandyInlineVideoStateObservation();
      updateCandyInlineVideoControlsOverlay(
        candyPictureInPicturePlayback.presentedVideo,
      );
      reconcileCandyInlineVideoState();
    }
    return;
  }
  candyPictureInPicturePlayback.inlineMediaPlayerEnabled = normalized;
  if (!normalized) {
    clearCandyInlineVideoOpenRequest();
    stopCandyInlineVideoStateObservation();
    removeCandyInlineVideoAction();
    clearCandyInlineVideoPresentation();
    clearCandyInlineVideoFullscreenOrigin();
    candyPictureInPicturePlayback.inlineStableOrigin = null;
    if (!candyPictureInPicturePlayback.expected) {
      candyPictureInPicturePlayback.candidates.clear();
      clearCandyPictureInPicturePresentation();
    }
    clearCandyInlineVideoState();
    return;
  }
  startCandyInlineVideoStateObservation();
  rememberCandyPictureInPictureVideos();
  reportCandyInlineVideoState();
}

function alignCandyPictureInPictureVideo(video) {
  if (!candyPictureInPicturePlayback.expected || !video?.isConnected) return;
  if (candyPictureInPicturePlayback.presentedVideo !== video) return;
  video.style.setProperty(CANDY_PICTURE_IN_PICTURE_OFFSET_X, "0px");
  video.style.setProperty(CANDY_PICTURE_IN_PICTURE_OFFSET_Y, "0px");
  let offsetX = 0;
  let offsetY = 0;
  for (let pass = 0; pass < 2; pass += 1) {
    const bounds = video.getBoundingClientRect();
    const scaleX = bounds.width / video.offsetWidth;
    const scaleY = bounds.height / video.offsetHeight;
    if (!Number.isFinite(scaleX) || scaleX <= 0 ||
        !Number.isFinite(scaleY) || scaleY <= 0) return;
    offsetX -= bounds.left / scaleX;
    offsetY -= bounds.top / scaleY;
    video.style.setProperty(CANDY_PICTURE_IN_PICTURE_OFFSET_X, `${offsetX}px`);
    video.style.setProperty(CANDY_PICTURE_IN_PICTURE_OFFSET_Y, `${offsetY}px`);
  }
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
    alignCandyPictureInPictureVideo(video);
  });
}

function monitorCandyPictureInPictureAlignment(video) {
  if (candyPictureInPicturePlayback.alignmentMonitorFrame !== null) {
    cancelAnimationFrame(candyPictureInPicturePlayback.alignmentMonitorFrame);
  }
  const generation = candyPictureInPicturePlayback.generation;
  const deadline = performance.now() + 5_000;
  const check = () => {
    candyPictureInPicturePlayback.alignmentMonitorFrame = null;
    if (!candyPictureInPicturePlayback.expected ||
        candyPictureInPicturePlayback.generation !== generation ||
        candyPictureInPicturePlayback.presentedVideo !== video || !video?.isConnected) return;
    const bounds = video.getBoundingClientRect();
    if (Math.abs(bounds.left) >= 0.5 || Math.abs(bounds.top) >= 0.5) {
      alignCandyPictureInPictureVideo(video);
    }
    if (performance.now() < deadline) {
      candyPictureInPicturePlayback.alignmentMonitorFrame = requestAnimationFrame(check);
    }
  };
  candyPictureInPicturePlayback.alignmentMonitorFrame = requestAnimationFrame(check);
}

function candyPictureInPictureVideoToPresent(preferredVideo = null) {
  return isCandyInlineVideoCandidate(preferredVideo) ? preferredVideo :
    candyPictureInPicturePlayback.inlinePresentationExpected ?
      (isCandyInlineVideoCandidate(candyPictureInPicturePlayback.presentedVideo) ?
        candyPictureInPicturePlayback.presentedVideo : null) :
      currentCandyPictureInPictureVideo();
}

function presentCandyPictureInPictureVideo(preferredVideo = null) {
  if (
    !candyPictureInPicturePlayback.expected ||
    !document.documentElement ||
    !document.body
  ) return;
  const video = candyPictureInPictureVideoToPresent(preferredVideo);
  if (!video) {
    clearCandyPictureInPicturePresentation();
    return;
  }
  const videoChanged = candyPictureInPicturePlayback.presentedVideo !== video;
  if (videoChanged) {
    clearCandyPictureInPictureVideoPresentation(
      candyPictureInPicturePlayback.presentedVideo,
    );
    candyPictureInPicturePlayback.presentedVideo = video;
  }
  if (
    videoChanged ||
    !candyPictureInPicturePlayback.layoutObserver ||
    !candyPictureInPicturePlayback.resizeObserver
  ) {
    candyPictureInPicturePlayback.layoutObserver?.disconnect();
    candyPictureInPicturePlayback.resizeObserver?.disconnect();
    candyPictureInPicturePlayback.resizeObserver = new ResizeObserver(
      scheduleCandyPictureInPictureAlignment,
    );
    candyPictureInPicturePlayback.resizeObserver.observe(video);
    candyPictureInPicturePlayback.layoutObserver = new MutationObserver(() => {
      if (!video.isConnected) {
        candyPictureInPicturePlayback.candidates.delete(video);
        clearCandyInlineVideoPresentation();
        clearCandyPictureInPicturePresentation();
        reportCandyInlineVideoState();
        return;
      }
      updateCandyPictureInPictureVideoControls(video, true);
      scheduleCandyPictureInPictureAlignment();
    });
    candyPictureInPicturePlayback.layoutObserver.observe(video, {
      attributes: true,
      attributeFilter: ["controls"],
    });
    for (let ancestor = video.parentElement; ancestor; ancestor = ancestor.parentElement) {
      candyPictureInPicturePlayback.resizeObserver.observe(ancestor);
      candyPictureInPicturePlayback.layoutObserver.observe(ancestor, {
        attributes: true,
        childList: true,
        attributeFilter: ["class", "style"],
      });
    }
  }
  candyPictureInPicturePlayback.presentedVideo = video;
  updateCandyPictureInPictureVideoControls(video, true);
  if (candyUsesBackgroundVideoVisibilityFix) {
    const ancestors = [];
    for (let ancestor = video.parentElement;
      ancestor && ancestor !== document.body && ancestor !== document.documentElement;
      ancestor = ancestor.parentElement) {
      const style = getComputedStyle(ancestor);
      const clipsOrContainsVideo = [
        style.transform, style.translate, style.scale, style.filter,
        style.perspective, style.clipPath, style.mask, style.contain,
      ].some((value) => value && value !== "none") ||
        [style.overflowX, style.overflowY].some((value) => value && value !== "visible");
      if (clipsOrContainsVideo) ancestors.push(ancestor);
    }
    const previous = candyPictureInPicturePlayback.pictureInPictureAncestors;
    if (ancestors.length !== previous.length ||
        ancestors.some((ancestor, index) => ancestor !== previous[index])) {
      previous.forEach((element) => {
        element.removeAttribute(CANDY_PICTURE_IN_PICTURE_ANCESTOR_ATTRIBUTE);
      });
      ancestors.forEach((element) => {
        element.setAttribute(CANDY_PICTURE_IN_PICTURE_ANCESTOR_ATTRIBUTE, "");
      });
      candyPictureInPicturePlayback.pictureInPictureAncestors = ancestors;
    }
  }
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
html[${CANDY_PICTURE_IN_PICTURE_ROOT_ATTRIBUTE}] [${CANDY_INLINE_VIDEO_CONTROLS_ATTRIBUTE}] {
  display: none !important;
  visibility: hidden !important;
}
html[${CANDY_PICTURE_IN_PICTURE_ROOT_ATTRIBUTE}] [${CANDY_PICTURE_IN_PICTURE_ANCESTOR_ATTRIBUTE}] {
  overflow: visible !important;
  clip-path: none !important;
  contain: none !important;
  transform: none !important;
  translate: none !important;
  scale: none !important;
  filter: none !important;
  perspective: none !important;
  border-radius: 0 !important;
  mask: none !important;
  will-change: auto !important;
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
  alignCandyPictureInPictureVideo(video);
  scheduleCandyPictureInPictureAlignment();
}

function rememberCandyPictureInPictureVideos() {
  document.querySelectorAll("video").forEach((video) => {
    if (
      !video.ended &&
      (
        !video.paused ||
        (candyInlineMediaPlayerStartsAutomatically() && isCandyInlineVideoCandidate(video))
      )
    ) candyPictureInPicturePlayback.candidates.add(video);
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
  if (expected && !candyPictureInPicturePlayback.expected &&
      candyPictureInPicturePlayback.inlinePresentationExpected) {
    const video = candyPictureInPicturePlayback.presentedVideo;
    const sitePlayer = candyInlineVideoSitePlayer(video);
    if (sitePlayer) preserveCandyInlineVideoFullscreenOrigin(video, sitePlayer);
  }
  candyPictureInPicturePlayback.generation += 1;
  candyPictureInPicturePlayback.expected = expected;
  if (!expected) {
    clearCandyPictureInPicturePresentation();
    restoreCandyInlineVideoFullscreenOrigin();
    updateCandyInlineVideoControlsOverlay(
      candyPictureInPicturePlayback.presentedVideo,
    );
    return;
  }
  updateCandyInlineVideoFullscreenOriginVisibility();
  removeCandyInlineVideoControlsOverlay();
  rememberCandyPictureInPictureVideos();
  presentCandyPictureInPictureVideo();
  monitorCandyPictureInPictureAlignment(candyPictureInPicturePlayback.presentedVideo);
  scheduleCandyPictureInPicturePlayback();
}

function nextCandyAnimationFrame() {
  return new Promise((resolve) => requestAnimationFrame(resolve));
}

async function prepareCandyPictureInPicturePlayback(message) {
  if (message.expected !== true) {
    updateCandyPictureInPicturePlayback(false);
    await nextCandyAnimationFrame();
    await nextCandyAnimationFrame();
    updateCandyInlineVideoControlsOverlay(
      candyPictureInPicturePlayback.presentedVideo,
    );
    return { prepared: true };
  }
  const video = candyInlineVideoForIdentity(
    message.documentNonce,
    message.elementNonce,
  );
  if (!video) return { prepared: false };
  if (!document.fullscreenElement) {
    const sitePlayer = candyInlineVideoSitePlayer(video);
    if (sitePlayer) preserveCandyInlineVideoFullscreenOrigin(video, sitePlayer);
  }
  candyPictureInPicturePlayback.candidates.add(video);
  updateCandyPictureInPicturePlayback(true);
  presentCandyPictureInPictureVideo(video);
  alignCandyPictureInPictureVideo(video);
  const generation = candyPictureInPicturePlayback.generation;
  await nextCandyAnimationFrame();
  await nextCandyAnimationFrame();
  if (
    generation !== candyPictureInPicturePlayback.generation ||
    !candyPictureInPicturePlayback.expected ||
    candyPictureInPicturePlayback.presentedVideo !== video ||
    !isCandyInlineVideoCandidate(video) ||
    candyInlineVideoElementNonce(video) !== message.elementNonce
  ) return { prepared: false };
  alignCandyPictureInPictureVideo(video);
  const bounds = video.getBoundingClientRect();
  const viewportWidth = Math.max(1, innerWidth);
  const viewportHeight = Math.max(1, innerHeight);
  const videoLeft = Math.max(0, Math.min(viewportWidth, bounds.left));
  const videoTop = Math.max(0, Math.min(viewportHeight, bounds.top));
  const videoRight = Math.max(0, Math.min(viewportWidth, bounds.right));
  const videoBottom = Math.max(0, Math.min(viewportHeight, bounds.bottom));
  if (
    ![videoLeft, videoTop, videoRight, videoBottom, viewportWidth, viewportHeight]
      .every(Number.isFinite) ||
    videoRight <= videoLeft ||
    videoBottom <= videoTop
  ) return { prepared: false };
  return {
    prepared: true,
    documentNonce: candyInlineVideoDocumentNonce,
    elementNonce: candyInlineVideoElementNonce(video),
    videoLeft,
    videoTop,
    videoRight,
    videoBottom,
    viewportWidth,
    viewportHeight,
  };
}

function updateCandyInlineVideoPresentation(message) {
  if (message.expected !== true) {
    clearCandyInlineVideoPresentation();
    // The page can reflow after controls close, so retain its original video box.
    if (!candyPictureInPicturePlayback.expected) clearCandyPictureInPicturePresentation();
    reconcileCandyInlineVideoState();
    return { accepted: true };
  }
  removeCandyInlineVideoAction();
  const video = candyInlineVideoForIdentity(
    message.documentNonce,
    message.elementNonce,
  );
  if (
    !candyPictureInPicturePlayback.inlineMediaPlayerEnabled ||
    !video ||
    candyPictureInPicturePlayback.inlinePresentationExpected
  ) {
    return { accepted: false };
  }
  return { accepted: presentCandyInlineVideo(video) };
}

document.addEventListener("play", (event) => {
  if (!(event.target instanceof HTMLVideoElement)) return;
  if (!candyPictureInPicturePlayback.inlineMediaPlayerEnabled &&
      !candyVideoPresentationExpected()) return;
  candyPictureInPicturePlayback.candidates.add(event.target);
  reportCandyInlineVideoState();
  if (candyPictureInPicturePlayback.expected) {
    presentCandyPictureInPictureVideo();
  }
}, true);
document.addEventListener("pause", (event) => {
  if (!(event.target instanceof HTMLVideoElement)) return;
  if (candyPictureInPicturePlayback.candidates.has(event.target)) {
    reportCandyInlineVideoState();
  }
  if (
    candyPictureInPicturePlayback.expected &&
    candyPictureInPicturePlayback.candidates.has(event.target)
  ) {
    scheduleCandyPictureInPicturePlayback();
  }
}, true);
for (const eventName of ["loadedmetadata", "durationchange", "resize"]) {
  document.addEventListener(eventName, (event) => {
    if (
      event.target instanceof HTMLVideoElement &&
      candyPictureInPicturePlayback.candidates.has(event.target)
    ) {
      reportCandyInlineVideoState();
    }
  }, true);
}
document.addEventListener("ended", (event) => {
  if (!(event.target instanceof HTMLVideoElement)) return;
  if (candyPictureInPicturePlayback.inlineFullscreenOrigin?.video === event.target) {
    clearCandyInlineVideoFullscreenOrigin();
  }
  if (candyPictureInPicturePlayback.inlineStableOrigin?.video === event.target) {
    candyPictureInPicturePlayback.inlineStableOrigin = null;
  }
  if (
    candyPictureInPicturePlayback.inlinePresentationExpected &&
    candyPictureInPicturePlayback.presentedVideo === event.target
  ) {
    clearCandyInlineVideoPresentation();
    if (!candyPictureInPicturePlayback.expected) clearCandyPictureInPicturePresentation();
  }
  candyPictureInPicturePlayback.candidates.delete(event.target);
  reportCandyInlineVideoState();
}, true);
document.addEventListener("emptied", (event) => {
  if (
    !(event.target instanceof HTMLVideoElement) ||
    !candyPictureInPicturePlayback.candidates.has(event.target)
  ) return;
  reportCandyInlineVideoState(event.target);
}, true);
window.addEventListener("pagehide", () => {
  stopCandyInlineVideoStateObservation();
  removeCandyInlineVideoAction();
  clearCandyInlineVideoPresentation();
  clearCandyInlineVideoFullscreenOrigin();
  candyPictureInPicturePlayback.inlineStableOrigin = null;
  clearCandyPictureInPicturePresentation();
  candyPictureInPicturePlayback.candidates.clear();
  reportCandyInlineVideoState();
}, true);
window.addEventListener("pageshow", () => {
  startCandyInlineVideoStateObservation();
  scheduleCandyInlineVideoStateReport();
}, true);
window.addEventListener("scroll", scheduleCandyInlineVideoStateReport, true);
window.addEventListener("resize", scheduleCandyInlineVideoStateReport, true);
window.addEventListener("resize", reconcileCandyInlineVideoFullscreenOrigin, true);
document.addEventListener("visibilitychange", scheduleCandyPictureInPicturePlayback, true);
document.addEventListener("fullscreenchange", () => {
  updateCandyInlineVideoFullscreenOriginVisibility();
  if (!document.fullscreenElement) restoreCandyInlineVideoFullscreenOrigin();
  scheduleCandyPictureInPictureAlignment();
  if (
    !document.fullscreenElement &&
    candyPictureInPicturePlayback.inlinePresentationExpected &&
    candyPictureInPicturePlayback.presentedVideo?.isConnected
  ) {
    requestAnimationFrame(() => requestAnimationFrame(() => {
      window.dispatchEvent(new Event("resize"));
      updateCandyInlineVideoControlsOverlay(
        candyPictureInPicturePlayback.presentedVideo,
      );
    }));
  }
  if (
    !document.fullscreenElement &&
    candyPictureInPicturePlayback.inlinePresentationExpected &&
    candyInlineMediaPlayerIsFullscreenOnly()
  ) {
    clearCandyInlineVideoPresentation();
    reportCandyInlineVideoState();
  }
  if (
    candyPictureInPicturePlayback.inlineMediaPlayerEnabled &&
    candyInlineMediaPlayerReplacesFullscreen() &&
    document.fullscreenElement &&
    !candyVideoPresentationExpected()
  ) {
    const fullscreenVideo = document.fullscreenElement instanceof HTMLVideoElement ?
      document.fullscreenElement : document.fullscreenElement.querySelector("video");
    if (isCandyInlineVideoCandidate(fullscreenVideo)) {
      candyPictureInPicturePlayback.candidates.add(fullscreenVideo);
      reportCandyInlineVideoState(fullscreenVideo)
        .then(() => requestCandyInlineVideoOpen(fullscreenVideo))
        .catch(() => {});
    }
  }
  if (!candyPictureInPicturePlayback.inlinePresentationExpected) return;
  suppressCandyInlineVideoSiteControls(candyPictureInPicturePlayback.presentedVideo);
  updateCandyInlineVideoControlsOverlay(candyPictureInPicturePlayback.presentedVideo);
}, true);
document.addEventListener("transitionend", scheduleCandyPictureInPictureAlignment, true);
document.addEventListener("transitionend", scheduleCandyInlineVideoStateReport, true);
document.addEventListener("animationend", scheduleCandyInlineVideoStateReport, true);
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
  if (message.type === "content-policy") {
    updateCandyInlineMediaPlayerEnabled(
      message.inlineMediaPlayerEnabled,
      message.inlineMediaPlayerMode,
      message.revision,
      message.navigationGeneration,
      message.inlineMediaPlayerActionLabel,
      message.inlineMediaPlayerPlayLabel,
      message.inlineMediaPlayerPauseLabel,
      message.inlineMediaPlayerSeekLabel,
      message.inlineMediaPlayerEnterFullscreenLabel,
      message.inlineMediaPlayerExitFullscreenLabel,
      message.inlineMediaPlayerCloseLabel,
    );
    return undefined;
  }
  if (message.type === "picture-in-picture-playback" && typeof message.expected === "boolean") {
    if (Number.isSafeInteger(message.requestId)) {
      return prepareCandyPictureInPicturePlayback(message);
    }
    updateCandyPictureInPicturePlayback(message.expected);
    return undefined;
  }
  if (message.type === "inline-video-presentation" && typeof message.expected === "boolean") {
    return Promise.resolve(updateCandyInlineVideoPresentation(message));
  }
  if (self === top && message.type === "reader-extract") {
    return Promise.resolve(extractCandyReaderPayload());
  }
  if (self === top && message.type === "text-input-occlusion-probe") {
    return Promise.resolve(
      globalThis.CandyTextInputOcclusion?.probe(
        message.viewportRect,
        message.focusedOnly === true,
      ) || 0,
    );
  }
  if (self === top && message.type === "dom-probe" &&
      globalThis.CandyContentTopInset?.domDiagnosticsEnabled?.() === true &&
      globalThis.CandyContentTopInset.policyRevision() === message.revision &&
      globalThis.CandyContentTopInset.navigationGeneration() === message.navigationGeneration) {
    return Promise.resolve(globalThis.CandyDomProbe.sample());
  }
  return undefined;
});

if (self === top) {
  browser.runtime.sendMessage({ type: "content-policy-request" }).then((policy) => {
    updateCandyInlineMediaPlayerEnabled(
      policy?.inlineMediaPlayerEnabled,
      policy?.inlineMediaPlayerMode,
      policy?.revision,
      policy?.navigationGeneration,
      policy?.inlineMediaPlayerActionLabel,
      policy?.inlineMediaPlayerPlayLabel,
      policy?.inlineMediaPlayerPauseLabel,
      policy?.inlineMediaPlayerSeekLabel,
      policy?.inlineMediaPlayerEnterFullscreenLabel,
      policy?.inlineMediaPlayerExitFullscreenLabel,
      policy?.inlineMediaPlayerCloseLabel,
    );
  }).catch(() => {});
}
