export class EndpointError extends Error {}

const LOOPBACK_HOSTS = new Set(["localhost", "127.0.0.1", "[::1]", "::1"]);

export function normalizeEndpoint(input: string, allowRemoteHttp = false): string {
  let url: URL;
  try {
    url = new URL(input.trim());
  } catch {
    throw new EndpointError("Endpoint must be a valid URL.");
  }
  const secure = url.protocol === "https:";
  const allowedHttp = url.protocol === "http:" && (allowRemoteHttp || LOOPBACK_HOSTS.has(url.hostname));
  if (!secure && !allowedHttp) {
    throw new EndpointError("HTTPS is required; HTTP is allowed only for localhost.");
  }
  if (url.username || url.password || url.search || url.hash) {
    throw new EndpointError("Endpoint must not contain credentials, a query, or a fragment.");
  }
  if (url.pathname !== "/") {
    throw new EndpointError("Protocol v1 requires an endpoint without a subpath.");
  }
  return `${url.origin}/`;
}

export function endpointPermissionOrigin(endpoint: string): string {
  const url = new URL(normalizeEndpoint(endpoint, true));
  // Firefox rejects ports in extension match patterns. Chromium accepts them,
  // but Arc can persist a port-specific optional permission without activating
  // it for cross-origin fetches. A portless pattern is valid in both engines
  // and still limits access to the configured host and scheme.
  return `${url.protocol}//${url.hostname}/*`;
}

export function requiresRemoteHttpApproval(endpoint: string): boolean {
  const url = new URL(endpoint);
  return url.protocol === "http:" && !LOOPBACK_HOSTS.has(url.hostname);
}

export function endpointTransportWarning(input: string): string | null {
  const value = input.trim();
  if (!value.toLowerCase().startsWith("http:")) return null;

  let url: URL;
  try {
    url = new URL(value);
  } catch {
    return "HTTP is not encrypted. Complete the address to review the connection risk.";
  }
  if (url.protocol !== "http:") return null;
  if (LOOPBACK_HOSTS.has(url.hostname)) {
    return "Local HTTP is unencrypted. Use it only for development on this device.";
  }
  return "Remote HTTP is blocked because it can expose your server username, password, device token, and connection metadata. Use an HTTPS endpoint.";
}
