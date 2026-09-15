"use strict";

function candyTextInputOccludes(viewportRect) {
  const values = [viewportRect?.left, viewportRect?.top, viewportRect?.right, viewportRect?.bottom];
  if (!values.every(Number.isFinite) || viewportRect.left < 0 || viewportRect.top < 0 ||
      viewportRect.right > 1 || viewportRect.bottom > 1 ||
      viewportRect.right <= viewportRect.left || viewportRect.bottom <= viewportRect.top) return false;
  const root = document.scrollingElement || document.documentElement;
  if (!root) return false;
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
  if (!scrollingDisabled && scrollHeight - viewportPageTop - viewportHeight > 1) return false;
  const blocked = {
    left: viewportLeft + viewportRect.left * viewportWidth,
    top: viewportTop + viewportRect.top * viewportHeight,
    right: viewportLeft + viewportRect.right * viewportWidth,
    bottom: viewportTop + viewportRect.bottom * viewportHeight,
  };
  const textInputTypes = new Set([
    "", "email", "number", "password", "search", "tel", "text", "url",
  ]);
  const parentOrHost = (element) =>
    element.assignedSlot || element.parentElement || element.getRootNode?.().host || null;
  const isEditable = (element) => {
    if (!(element instanceof Element) || element.matches(":disabled") || element.readOnly ||
        element.getAttribute("aria-disabled") === "true") return false;
    const tag = element.tagName;
    if (tag === "TEXTAREA") return true;
    if (tag === "INPUT") return textInputTypes.has((element.type || "").toLowerCase());
    return element.isContentEditable || ["", "true", "plaintext-only"].includes(
      element.getAttribute("contenteditable"),
    );
  };
  const isHidden = (element) => {
    let current = element;
    for (let depth = 0; current && depth < 24; depth += 1) {
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
    if (!isEditable(element) || isHidden(element)) return false;
    const style = globalThis.getComputedStyle(element);
    if (
      style.pointerEvents === "none" || style.visibility === "hidden" ||
      style.visibility === "collapse"
    ) return false;
    const rect = element.getBoundingClientRect();
    const intersection = {
      left: Math.max(rect.left, blocked.left),
      top: Math.max(rect.top, blocked.top),
      right: Math.min(rect.right, blocked.right),
      bottom: Math.min(rect.bottom, blocked.bottom),
    };
    if (rect.width <= 0 || rect.height <= 0 ||
        intersection.right <= intersection.left || intersection.bottom <= intersection.top) return false;
    const points = [
      [0.5, 0.5], [0.15, 0.5], [0.85, 0.5], [0.5, 0.2], [0.5, 0.8],
    ];
    return points.some(([xFraction, yFraction]) => {
      const x = intersection.left + (intersection.right - intersection.left) * xFraction;
      const y = intersection.top + (intersection.bottom - intersection.top) * yFraction;
      return composedContains(element, topElementAtPoint(x, y));
    });
  };
  const selector = "textarea,input,[contenteditable]";
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
      if (element.matches(selector) && visitedCandidates < 512) {
        visitedCandidates += 1;
        if (overlaps(element)) return true;
      }
      if (element.shadowRoot) roots.push(element.shadowRoot);
      if (roots.length >= 64) break;
    }
  }
  const points = [
    [0.1, 0.25], [0.5, 0.25], [0.9, 0.25],
    [0.1, 0.5], [0.5, 0.5], [0.9, 0.5],
    [0.1, 0.75], [0.5, 0.75], [0.9, 0.75],
  ];
  return points.some(([xFraction, yFraction]) => {
    const x = blocked.left + (blocked.right - blocked.left) * xFraction;
    const y = blocked.top + (blocked.bottom - blocked.top) * yFraction;
    const hit = topElementAtPoint(x, y);
    return [hit].some((element) => {
      let current = element;
      for (let depth = 0; current && depth < 12; depth += 1) {
        if (overlaps(current)) return true;
        current = parentOrHost(current);
      }
      return false;
    });
  });
}

globalThis.CandyTextInputOcclusion = Object.freeze({ probe: candyTextInputOccludes });
