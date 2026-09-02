export class EndpointError extends Error {}

const LOOPBACK_HOSTS = new Set(["localhost", "127.0.0.1"]);

export function normalizeEndpoint(input: string): string {
  let url: URL;
  try {
    url = new URL(input.trim());
  } catch {
    throw new EndpointError("Endpoint must be a valid URL.");
  }
  const secure = url.protocol === "https:";
  const loopbackHttp = url.protocol === "http:" && LOOPBACK_HOSTS.has(url.hostname);
  if (!secure && !loopbackHttp) {
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
  return `${new URL(normalizeEndpoint(endpoint)).origin}/*`;
}
