type BrowserGlobal = typeof globalThis & { browser?: typeof chrome };

export function extensionApi(): typeof chrome {
  const candidate = (globalThis as BrowserGlobal).browser ?? globalThis.chrome;
  if (!candidate) throw new Error("WebExtension API is unavailable");
  return candidate;
}
