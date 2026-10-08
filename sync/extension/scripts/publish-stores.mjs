import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

export function releaseVersion(environment, packageVersion) {
  const version = environment.GITHUB_EVENT_NAME === "push"
    ? environment.GITHUB_REF?.replace(/^refs\/tags\/sync-extension-v/u, "")
    : environment.RELEASE_VERSION;
  if (environment.GITHUB_EVENT_NAME === "push") {
    if (environment.GITHUB_REF !== `refs/tags/sync-extension-v${version}`) {
      throw new Error("Automatic publishing requires a sync-extension-vX.Y.Z tag.");
    }
  } else if (environment.GITHUB_EVENT_NAME !== "workflow_dispatch"
    || environment.GITHUB_REF !== "refs/heads/main") {
    throw new Error("Manual publishing must run from main.");
  }
  if (!/^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/u.test(version ?? "")
    || version.split(".").some((part) => Number(part) > 65535)
    || version === "0.0.0") {
    throw new Error("Release version must be X.Y.Z with components between 0 and 65535.");
  }
  if (version !== packageVersion) throw new Error("Release version does not match package.json.");
  return version;
}

export function requiredConfiguration(environment, names) {
  const missing = names.filter((name) => !environment[name]?.trim());
  if (missing.length) throw new Error(`Missing store configuration: ${missing.join(", ")}`);
  return Object.fromEntries(names.map((name) => [name, environment[name]]));
}

export async function publishChrome(archive, environment, {
  fetchImpl = fetch,
  sleep = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds)),
  maxPolls = 30,
} = {}) {
  const config = requiredConfiguration(environment, [
    "CWS_PUBLISHER_ID", "CWS_EXTENSION_ID", "CWS_CLIENT_ID", "CWS_CLIENT_SECRET", "CWS_REFRESH_TOKEN",
  ]);
  if (!/^[a-z]{32}$/u.test(config.CWS_EXTENSION_ID)
    || !/^[A-Za-z0-9_-]+$/u.test(config.CWS_PUBLISHER_ID)) {
    throw new Error("Invalid Chrome publisher or extension ID.");
  }
  async function request(url, options, stage) {
    let response;
    try {
      response = await fetchImpl(url, { ...options, signal: AbortSignal.timeout(120_000) });
    } catch {
      throw new Error(`Chrome ${stage} network request failed.`);
    }
    if (!response.ok) throw new Error(`Chrome ${stage} failed (HTTP ${response.status}). Check Developer Dashboard.`);
    try {
      return await response.json();
    } catch {
      throw new Error(`Chrome ${stage} returned invalid JSON.`);
    }
  }
  const token = await request("https://oauth2.googleapis.com/token", {
    method: "POST",
    body: new URLSearchParams({
      client_id: config.CWS_CLIENT_ID,
      client_secret: config.CWS_CLIENT_SECRET,
      refresh_token: config.CWS_REFRESH_TOKEN,
      grant_type: "refresh_token",
    }),
  }, "authentication");
  if (typeof token.access_token !== "string" || !token.access_token) {
    throw new Error("Chrome authentication did not return an access token.");
  }
  const headers = { Authorization: `Bearer ${token.access_token}` };
  const resource = `publishers/${config.CWS_PUBLISHER_ID}/items/${config.CWS_EXTENSION_ID}`;
  const base = "https://chromewebstore.googleapis.com";
  const upload = await request(`${base}/upload/v2/${resource}:upload`, {
    method: "POST",
    headers: { ...headers, "Content-Type": "application/zip" },
    body: archive,
  }, "upload");
  let state = upload.uploadState;
  for (let attempt = 0; state === "IN_PROGRESS" && attempt < maxPolls; attempt += 1) {
    await sleep(10_000);
    const status = await request(`${base}/v2/${resource}:fetchStatus`, { headers }, "upload status");
    state = status.lastAsyncUploadState;
  }
  if (state !== "SUCCEEDED") throw new Error("Chrome upload did not succeed. Check Developer Dashboard.");
  const published = await request(`${base}/v2/${resource}:publish`, {
    method: "POST",
    headers: { ...headers, "Content-Type": "application/json" },
    body: JSON.stringify({ publishType: "DEFAULT_PUBLISH", blockOnWarnings: true, skipReview: false }),
  }, "submission");
  if (!["PENDING_REVIEW", "PUBLISHED"].includes(published.state)) {
    throw new Error("Chrome submission was not accepted for public publishing. Check Developer Dashboard.");
  }
  return published.state;
}

function main() {
  const command = process.argv[2];
  const packageVersion = JSON.parse(fs.readFileSync(path.join(root, "package.json"), "utf8")).version;
  const version = releaseVersion(process.env, packageVersion);
  if (command === "validate") {
    if (process.env.GITHUB_OUTPUT) fs.appendFileSync(process.env.GITHUB_OUTPUT, `version=${version}\n`);
    process.stdout.write(`Validated extension release ${version}.\n`);
    return;
  }
  if (command === "check-firefox") {
    requiredConfiguration(process.env, ["AMO_JWT_ISSUER", "AMO_JWT_SECRET"]);
    return;
  }
  if (command !== "chrome") throw new Error("Use validate, chrome, or check-firefox.");
  const archive = fs.readFileSync(path.join(root, "release", `candy-sync-chromium-${version}.zip`));
  return publishChrome(archive, process.env).then((state) => {
    process.stdout.write(`Chrome submission: ${state}. Store approval may still be pending.\n`);
  });
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    await main();
  } catch (error) {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  }
}
