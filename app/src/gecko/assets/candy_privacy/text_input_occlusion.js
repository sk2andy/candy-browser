"use strict";

function candyTextInputOccludes(viewportRect, focusedOnly = false) {
  const values = [viewportRect?.left, viewportRect?.top, viewportRect?.right, viewportRect?.bottom];
  if (!values.every(Number.isFinite) || viewportRect.left < 0 || viewportRect.top < 0 ||
      viewportRect.right > 1 || viewportRect.bottom > 1 ||
      viewportRect.right <= viewportRect.left || viewportRect.bottom <= viewportRect.top) return 0;
  const root = document.scrollingElement || document.documentElement;
  if (!root) return 0;
  const visualViewport = globalThis.visualViewport;
  const viewportHeight = Math.max(0, visualViewport?.height || globalThis.innerHeight || 0);
  const viewportWidth = Math.max(0, visualViewport?.width || globalThis.innerWidth || 0);
  const viewportLeft = Number.isFinite(visualViewport?.offsetLeft) ? visualViewport.offsetLeft : 0;
  const viewportTop = Number.isFinite(visualViewport?.offsetTop) ? visualViewport.offsetTop : 0;
  const scrollTop = Math.max(0, root.scrollTop || globalThis.scrollY || 0);
  const scrollHeight = Math.max(
    root.scrollHeight || 0,
    document.documentElement?.scrollHeight || 0,
    document.body?.scrollHeight || 0,
  );
  const viewportPageTop = Number.isFinite(visualViewport?.pageTop) ?
    visualViewport.pageTop : scrollTop;
  const scrollingDisabled = [root, document.body].some((element) => {
    if (!(element instanceof Element)) return false;
    const overflowY = globalThis.getComputedStyle(element).overflowY;
    return overflowY === "hidden" || overflowY === "clip";
  });
  const textInputTypes = new Set([
    "", "email", "number", "password", "search", "tel", "text", "url",
  ]);
  const isTextInput = (element) => {
    if (!(element instanceof Element) || element.matches(":disabled") || element.readOnly ||
        element.getAttribute("aria-disabled") === "true") return false;
    if (element.tagName === "TEXTAREA") return true;
    if (element.tagName === "INPUT") {
      return textInputTypes.has((element.type || "").toLowerCase());
    }
    return element.isContentEditable || ["", "true", "plaintext-only"].includes(
      element.getAttribute("contenteditable"),
    );
  };
  let activeElement = document.activeElement;
  for (let depth = 0; activeElement?.shadowRoot && depth < 12; depth += 1) {
    const nested = activeElement.shadowRoot.activeElement;
    if (!nested || nested === activeElement) break;
    activeElement = nested;
  }
  const focusedTextInput = isTextInput(activeElement) ? activeElement : null;
  const atDocumentBottom = scrollingDisabled ||
    scrollHeight - viewportPageTop - viewportHeight <= 1;
  const blocked = {
    left: viewportLeft + viewportRect.left * viewportWidth,
    top: viewportTop + viewportRect.top * viewportHeight,
    right: viewportLeft + viewportRect.right * viewportWidth,
    bottom: viewportTop + viewportRect.bottom * viewportHeight,
  };
  const parentOrHost = (element) =>
    element.assignedSlot || element.parentElement || element.getRootNode?.().host || null;
  const controlSelector = 'button,select,a[href],summary,iframe,' +
    '[role="button"],[role="link"],[role="tab"],[role="menuitem"],[role="checkbox"],' +
    '[role="radio"],[role="switch"],[role="combobox"],[role="slider"]';
  const interactiveSelector = 'textarea,input,[contenteditable],' + controlSelector + ',[tabindex]';
  const isInteractive = (element) => {
    if (!(element instanceof Element) || element.matches(":disabled") ||
        element.getAttribute("aria-disabled") === "true" || element.readOnly) return false;
    if (isTextInput(element)) return true;
    if (element.tagName === "INPUT") return element.type !== "hidden" && !element.readOnly;
    if (element.hasAttribute("contenteditable")) return false;
    return element.matches(interactiveSelector) &&
      (!element.hasAttribute("tabindex") || element.tabIndex >= 0 ||
        element.matches(controlSelector));
  };
  const hasBottomAnchor = (element) => {
    let current = element;
    const bottomBand = Math.max(96, viewportTop + viewportHeight - blocked.top);
    for (let depth = 0; current && depth < 64; depth += 1) {
      const style = globalThis.getComputedStyle(current);
      if (style.position === "fixed" || style.position === "sticky") {
        const rect = current.getBoundingClientRect();
        const bottom = Number.parseFloat(style.bottom);
        const nearViewportBottom = viewportTop + viewportHeight - rect.bottom <= bottomBand;
        if (rect.top >= viewportTop + viewportHeight / 2 &&
            rect.bottom > blocked.top &&
            ((Number.isFinite(bottom) && bottom <= bottomBand) || nearViewportBottom)) return true;
      }
      current = parentOrHost(current);
    }
    return false;
  };
  const isHidden = (element, maximumDepth = 64) => {
    let current = element;
    for (let depth = 0; current && depth < maximumDepth; depth += 1) {
      if (current.matches('[hidden],[inert],[aria-hidden="true"]')) return true;
      const style = globalThis.getComputedStyle(current);
      const opacity = Number.parseFloat(style.opacity);
      if (
        style.display === "none" || style.contentVisibility === "hidden" ||
        (Number.isFinite(opacity) && opacity <= 0.01)
      ) return true;
      current = parentOrHost(current);
    }
    return current !== null;
  };
  const topElementAtPoint = (x, y) => {
    let pointRoot = document;
    let hit = pointRoot.elementFromPoint?.(x, y) || null;
    for (let depth = 0; hit?.shadowRoot && depth < 12; depth += 1) {
      const nested = hit.shadowRoot.elementFromPoint?.(x, y);
      if (!nested || nested === hit) break;
      hit = nested;
    }
    return hit;
  };
  const composedContains = (container, node) => {
    let current = node;
    for (let depth = 0; current && depth < 24; depth += 1) {
      if (current === container) return true;
      current = parentOrHost(current);
    }
    return false;
  };
  const overlaps = (element) => {
    if (!isInteractive(element)) return false;
    const rect = element.getBoundingClientRect();
    const intersection = {
      left: Math.max(rect.left, blocked.left),
      top: Math.max(rect.top, blocked.top),
      right: Math.min(rect.right, blocked.right),
      bottom: Math.min(rect.bottom, blocked.bottom),
    };
    if (rect.width <= 0 || rect.height <= 0 ||
        intersection.right <= intersection.left || intersection.bottom <= intersection.top) return false;
    if (isHidden(element)) return false;
    const style = globalThis.getComputedStyle(element);
    if (
      style.pointerEvents === "none" || style.visibility === "hidden" ||
      style.visibility === "collapse"
    ) return false;
    if (element.tagName === "IFRAME") {
      if (!hasBottomAnchor(element)) return false;
    } else if (!isTextInput(element) && !atDocumentBottom && !hasBottomAnchor(element)) return false;
    const points = [
      [0.5, 0.5], [0.15, 0.5], [0.85, 0.5], [0.5, 0.2], [0.5, 0.8],
    ];
    return points.some(([xFraction, yFraction]) => {
      const x = intersection.left + (intersection.right - intersection.left) * xFraction;
      const y = intersection.top + (intersection.bottom - intersection.top) * yFraction;
      return composedContains(element, topElementAtPoint(x, y));
    });
  };
  if (focusedOnly) {
    if (!focusedTextInput) return 0;
    if (isHidden(focusedTextInput, 64)) return 0;
    const style = globalThis.getComputedStyle(focusedTextInput);
    if (
      style.pointerEvents === "none" || style.visibility === "hidden" ||
      style.visibility === "collapse"
    ) return 0;
    const rect = focusedTextInput.getBoundingClientRect();
    const horizontallyAligned = rect.right > blocked.left && rect.left < blocked.right;
    const reachesOrFallsBelowChrome = rect.bottom > blocked.top;
    return rect.width > 0 && rect.height > 0 && horizontallyAligned && reachesOrFallsBelowChrome ? 2 : 1;
  }
  const points = [
    [0.1, 0.25], [0.5, 0.25], [0.9, 0.25],
    [0.1, 0.5], [0.5, 0.5], [0.9, 0.5],
    [0.1, 0.75], [0.5, 0.75], [0.9, 0.75],
  ];
  const occluded = points.some(([xFraction, yFraction]) => {
    const x = blocked.left + (blocked.right - blocked.left) * xFraction;
    const y = blocked.top + (blocked.bottom - blocked.top) * yFraction;
    const hit = topElementAtPoint(x, y);
    return [hit].some((element) => {
      let current = element;
      for (let depth = 0; current && depth < 64; depth += 1) {
        if (overlaps(current)) return true;
        current = parentOrHost(current);
      }
      return false;
    });
  });
  if (occluded) return 2;
  const roots = [document];
  let visitedElements = 0;
  let visitedCandidates = 0;
  for (let rootIndex = 0;
    rootIndex < roots.length && rootIndex < 64 && visitedElements < 4096;
    rootIndex += 1) {
    const currentRoot = roots[rootIndex];
    const walker = document.createTreeWalker(currentRoot, NodeFilter.SHOW_ELEMENT);
    while (visitedElements < 4096) {
      const element = walker.nextNode();
      if (!element) break;
      visitedElements += 1;
      if (element.matches(interactiveSelector) && visitedCandidates < 512) {
        visitedCandidates += 1;
        if (overlaps(element)) return 2;
      }
      if (element.shadowRoot) roots.push(element.shadowRoot);
      if (roots.length >= 64) break;
    }
  }
  return focusedTextInput ? 1 : 0;
}

globalThis.CandyTextInputOcclusion = Object.freeze({ probe: candyTextInputOccludes });
