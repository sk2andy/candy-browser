"""Local design preview with a read-only, fixed-host RSS Amplifier relay.

Run: python3 docs/design-concepts/rss/preview_server.py
No app backend, credentials, writes, or arbitrary URL forwarding.
"""

import json
import re
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, urlencode, urlsplit
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[3]
ORIGINS = {f"http://{host}:{port}" for host in ("127.0.0.1", "localhost") for port in (8769, 8770)}


class PreviewHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ROOT), **kwargs)

    def log_message(self, format, *args):
        # Search terms and feed paths do not belong in a local preview log.
        pass

    def do_GET(self):
        parsed = urlsplit(self.path)
        if not parsed.path.startswith("/catalog-api/"):
            return super().do_GET()
        origin = self.headers.get("Origin")
        if origin and origin not in ORIGINS:
            return self.api_response(403, {"error": "Origin not allowed"})
        path = parsed.path.removeprefix("/catalog-api")
        if not re.fullmatch(r"/api/(search|topics/[a-z0-9-]{1,120}|feeds/[A-Za-z0-9%_.~-]{1,500})", path):
            return self.api_response(400, {"error": "Unsupported preview endpoint"})
        query = parse_qs(parsed.query)
        if set(query) - {"q", "kind", "limit", "offset"}:
            return self.api_response(400, {"error": "Unsupported parameters"})
        try:
            limit = max(1, min(int(query.get("limit", ["12"])[0]), 16))
            offset = max(0, min(int(query.get("offset", ["0"])[0]), 10000))
        except ValueError:
            return self.api_response(400, {"error": "Invalid page"})
        params = {"limit": limit, "offset": offset}
        if "q" in query:
            params["q"] = query["q"][0][:200]
        if "kind" in query:
            if query["kind"][0] not in {"blogs", "news"}:
                return self.api_response(400, {"error": "Unsupported source type"})
            params["kind"] = query["kind"][0]
        request = Request("https://rssamplifier.com" + path + "?" + urlencode(params), headers={"Accept": "application/json", "User-Agent": "Candy-Design-Preview/1.0"})
        try:
            with urlopen(request, timeout=15) as response:
                payload = response.read(8 * 1024 * 1024 + 1)
            if len(payload) > 8 * 1024 * 1024:
                return self.api_response(502, {"error": "Preview response too large"})
            return self.api_response(200, json.loads(payload))
        except (HTTPError, URLError, TimeoutError, ValueError):
            return self.api_response(502, {"error": "Directory unavailable"})

    def api_response(self, status, value):
        payload = json.dumps(value, ensure_ascii=False).encode()
        self.send_response(status)
        origin = self.headers.get("Origin")
        if origin in ORIGINS:
            self.send_header("Access-Control-Allow-Origin", origin)
        self.send_header("Vary", "Origin")
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)


if __name__ == "__main__":
    print("Candy design: http://127.0.0.1:8770/docs/design-concepts/rss/entwurf.html#catalog", flush=True)
    ThreadingHTTPServer(("127.0.0.1", 8770), PreviewHandler).serve_forever()
