"use strict";

// Experimental top-document policy: no iframe, containing-block or footprint proof.
(() => {
  if (globalThis.self !== globalThis.top) return;
  const owned = new Map();
  const ownWrites = new WeakMap();
  let configuration = null;
  let configurationKey = "";
  let inset = 0;
  let jobs = [];
  let cleanup = [];
  let timer = 0;
  let timerEpoch = 0;
  let observer = null;
  let interactionUntil = 0;
  let bodyPending = false;
  let semanticSeeded = false;

  function pixels(value) {
    if (typeof value !== "string" || !/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)px$/.test(value.trim())) return null;
    const number = Number(value.trim().slice(0, -2));
    return Number.isFinite(number) ? number : null;
  }

  function stillOwned(element, entry) {
    return element.style.getPropertyValue(entry.name) === entry.applied &&
      element.style.getPropertyPriority(entry.name) === "important";
  }

  function write(element, name, value, priority = "") {
    const before = element.getAttribute("style") || "";
    if (value) element.style.setProperty(name, value, priority);
    else element.style.removeProperty(name);
    let record = ownWrites.get(element);
    if (!record || record.after !== before) record = { before: new Set(), after: "" };
    record.before.add(before);
    record.after = element.getAttribute("style") || "";
    ownWrites.set(element, record);
  }

  function apply(element, name, value) {
    let entries = owned.get(element);
    const entry = entries?.find((current) => current.name === name);
    if (entry) {
      if (!stillOwned(element, entry)) {
        entries.splice(entries.indexOf(entry), 1);
        if (!entries.length) owned.delete(element);
      }
      return; // Do not remove/reapply retained protection on each mutation.
    }
    if (!entries) {
      if (owned.size >= configuration.maxInitialElements) return;
      entries = [];
      owned.set(element, entries);
    }
    const original = { name, value: element.style.getPropertyValue(name),
      priority: element.style.getPropertyPriority(name) };
    write(element, name, value, "important");
    const applied = element.style.getPropertyValue(name);
    if (element.style.getPropertyPriority(name) !== "important" ||
        (name === "--candy-safe-area-inset-top" ? applied !== value :
          pixels(applied) === null || Math.abs(pixels(applied) - pixels(value)) > 0.001)) {
      if (!entries.length) owned.delete(element);
      return;
    }
    entries.push({ ...original, applied });
  }

  function restore(element, entries) {
    for (const entry of entries) {
      if (stillOwned(element, entry)) write(element, entry.name, entry.value, entry.priority);
    }
  }

  function cancel() {
    if (timer) clearTimeout(timer);
    timer = 0;
    timerEpoch++;
    jobs = [];
    interactionUntil = 0;
  }

  function schedule(delay = 0) {
    if (timer || (!cleanup.length && !bodyPending && !jobs.length)) return;
    const epoch = timerEpoch;
    timer = setTimeout(() => {
      if (epoch !== timerEpoch) return;
      timer = 0;
      work();
    }, delay);
  }

  function enqueue(root, initial = false, shallow = false) {
    if (!(root instanceof Element) || !root.isConnected ||
        jobs.some((job) => job.root === root && job.shallow === shallow)) return;
    if (jobs.length >= 16) return;
    const job = { root, next: root, remaining: shallow ? 1 : configuration.maxInitialElements, initial, shallow };
    if (shallow) jobs.unshift(job);
    else jobs.push(job);
    return job;
  }

  function seedSemanticHeader() {
    if (semanticSeeded || !configuration?.active || !document.body || document.readyState !== "complete") return;
    semanticSeeded = true;
    let current = document.querySelector("header") ?? document.querySelector("nav") ?? document.querySelector('[role="banner"]');
    for (let depth = 0; current && current !== document.body &&
        current !== document.documentElement && depth < 8; depth++, current = current.parentElement) {
      enqueue(current, true, true);
    }
  }

  function nextElement(element, root) {
    if (element.firstElementChild) return element.firstElementChild;
    for (let current = element; current && current !== root; current = current.parentElement) {
      if (current.nextElementSibling) return current.nextElementSibling;
    }
    return null;
  }

  function classify(element, style) {
    const entry = owned.get(element)?.find((current) => current.name === "top");
    if (entry && stillOwned(element, entry)) return;
    style ??= getComputedStyle(element);
    if (style.position !== "fixed" && style.position !== "sticky") return;
    const top = pixels(style.top);
    if (top !== null && top <= inset + 0.001) apply(element, "top", `${top + inset}px`);
  }

  function work() {
    const started = performance.now();
    let count = 0;
    while (count < configuration.maxElementsPerBatch &&
        performance.now() - started < configuration.maxBatchDurationMillis) {
      if (cleanup.length) {
        const [element, entries] = cleanup.shift();
        restore(element, entries);
        count++;
        continue;
      }
      if (!configuration.active) break;
      if (bodyPending) {
        bodyPending = false;
        if (document.body) {
          apply(document.documentElement, "--candy-safe-area-inset-top", `${inset}px`);
          const style = getComputedStyle(document.body);
          const padding = pixels(style.paddingTop);
          if (padding !== null && padding < inset) apply(document.body, "padding-top", `${inset}px`);
          classify(document.body, style);
          seedSemanticHeader();
          const job = enqueue(document.body, true);
          // Body was already classified above; keep the initial node cap shared.
          if (job) {
            job.next = nextElement(document.body, document.body);
            job.remaining -= 9; // Body plus eight reserved shallow initial checks share the cap.
          }
          count++;
          continue;
        }
      }
      const job = jobs[0];
      if (!job) break;
      if (!job.next || !job.remaining || !job.root.isConnected ||
          (!job.initial && configuration.requireInteractionForUpdates &&
            (interactionUntil <= 0 || performance.now() > interactionUntil))) {
        jobs.shift();
        continue;
      }
      const element = job.next;
      job.next = job.shallow ? null : nextElement(element, job.root);
      job.remaining--;
      count++;
      if (element.isConnected && job.root.contains(element)) classify(element);
    }
    schedule();
  }

  function mutations(records) {
    if (!configuration.active || (configuration.requireInteractionForUpdates &&
        (interactionUntil <= 0 || performance.now() > interactionUntil))) return;
    for (let index = 0; index < Math.min(records.length, 128); index++) {
      const record = records[index];
      if (record.type === "attributes" && configuration.recheckChangedElements) {
        const own = ownWrites.get(record.target);
        if (record.attributeName === "style" && own &&
            own.after === (record.target.getAttribute("style") || "") && own.before.has(record.oldValue || "")) continue;
        enqueue(record.target);
      } else if (record.type === "childList" && configuration.recheckAddedElements) {
        for (let child = 0; child < Math.min(record.addedNodes.length, 16); child++) enqueue(record.addedNodes[child]);
      }
    }
    schedule(configuration.mutationDebounceMillis);
  }

  function observe() {
    if (!configuration?.active || observer || !document.documentElement) return;
    observer = new MutationObserver(mutations);
    observer.observe(document.documentElement, { childList: true, subtree: true,
      attributes: true, attributeOldValue: true, attributeFilter: ["class", "style", "hidden"] });
  }

  function configure(resize = false) {
    const incoming = globalThis.CandyContentTopInset?.cssSafeAreaConfiguration?.();
    if (!incoming) return;
    const scale = Number.isFinite(globalThis.devicePixelRatio) && globalThis.devicePixelRatio > 0 ? globalThis.devicePixelRatio : 1;
    const nextInset = Number.isFinite(incoming.cssSafeAreaTopInsetPx) ? Math.min(10000, Math.max(0, incoming.cssSafeAreaTopInsetPx)) / scale : 0;
    const bounded = (value, minimum, maximum, fallback) => Number.isSafeInteger(value) ? Math.min(maximum, Math.max(minimum, value)) : fallback;
    const next = { active: incoming.ready === true && incoming.enabled === true && nextInset > 0,
      recheckAddedElements: incoming.recheckAddedElements === true,
      recheckChangedElements: incoming.recheckChangedElements === true,
      requireInteractionForUpdates: incoming.requireInteractionForUpdates !== false,
      recheckOnResize: incoming.recheckOnResize === true,
      interactionWindowMillis: bounded(incoming.interactionWindowMillis, 100, 5000, 1000),
      mutationDebounceMillis: bounded(incoming.mutationDebounceMillis, 50, 1000, 150),
      maxElementsPerBatch: bounded(incoming.maxElementsPerBatch, 4, 64, 16),
      maxBatchDurationMillis: bounded(incoming.maxBatchDurationMillis, 1, 8, 4),
      maxInitialElements: bounded(incoming.maxInitialElements, 64, 2048, 512) };
    const key = JSON.stringify([next, nextInset, incoming.navigationGeneration]);
    if (key === configurationKey) {
      if (resize && next.active) { enqueue(document.body, true); schedule(); }
      return;
    }
    cancel();
    observer?.disconnect();
    observer = null;
    cleanup.push(...owned);
    owned.clear();
    configuration = next;
    configurationKey = key;
    inset = nextInset;
    bodyPending = next.active && !!document.body;
    semanticSeeded = false;
    observe();
    schedule();
  }

  function interaction(event) {
    if (!event.isTrusted || !configuration?.active) return;
    interactionUntil = performance.now() + configuration.interactionWindowMillis;
    if ((configuration.recheckAddedElements || configuration.recheckChangedElements) &&
        event.target !== document.body && event.target !== document.documentElement) {
      enqueue(event.target);
      schedule(configuration.mutationDebounceMillis);
    }
  }

  function scroll() {
    cancel();
    bodyPending = false;
    if (cleanup.length) schedule();
  }

  globalThis.__candyConfigureCssSafeArea = configure;
  document.addEventListener("DOMContentLoaded", () => {
    observe();
    seedSemanticHeader();
    if (configuration?.active && !bodyPending && !owned.size) { bodyPending = true; schedule(); }
    else schedule();
  }, { once: true });
  document.addEventListener("click", interaction, true);
  document.addEventListener("drop", interaction, true);
  globalThis.addEventListener("load", () => { seedSemanticHeader(); schedule(); }, { once: true });
  document.addEventListener("scroll", scroll, { capture: true, passive: true });
  globalThis.addEventListener("scroll", scroll, { passive: true });
  globalThis.addEventListener("resize", () => { if (configuration?.recheckOnResize) configure(true); }, { passive: true });
  configure();
})();
