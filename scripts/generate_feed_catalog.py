#!/usr/bin/env python3
"""Build Candy's pinned CC0 Plenary catalog. No feed content is downloaded."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import urllib.request
from urllib.parse import urlsplit
import xml.etree.ElementTree as ET
import zipfile

REVISION = "9ab2482cae9031a5a39635c887fcfc8ece7e130a"
ARCHIVE_SHA256 = "cc72e5d0bb79bf5e4045746f98cf9395ce4909915ccfd69806c3f685c6eacccd"
UPSTREAM = f"https://codeload.github.com/plenaryapp/awesome-rss-feeds/zip/{REVISION}"
ROOT = Path(__file__).resolve().parents[1]
TOPICS = {
    "technology": {"Tech", "Android", "Apple", "Programming", "Android Development", "Web Development", "iOS Development", "Cyber security", "Gaming", "Cryptocurrency", "Startups"},
    "science": {"Science", "Space"},
    "design": {"UI - UX", "Architecture", "Interior design", "Photography", "Books", "Music", "Movies", "Fashion"},
    "food": {"Food"},
    "travel": {"Travel"},
    "environment": {"Nature", "Environment", "Animal & Wildlife"},
}

def compile_catalog(archive):
    if hashlib.sha256(archive).hexdigest() != ARCHIVE_SHA256:
        raise ValueError("Pinned upstream archive checksum mismatch")
    sources = {}
    with zipfile.ZipFile(io.BytesIO(archive)) as package:
        paths = sorted(p for p in package.namelist() if "/with_category/" in p and p.endswith(".opml"))
        if len(paths) != 66:
            raise ValueError(f"Unexpected upstream file count: {len(paths)}")
        for path in paths:
            # Pinned upstream has raw ampersands and one unescaped quoted Russian title.
            text = package.read(path).decode("utf-8-sig")
            text = re.sub(r"&(?!#\d+;|#x[\da-fA-F]+;|amp;|lt;|gt;|quot;|apos;)", "&amp;", text)
            text = text.replace('Газета "Коммерсантъ". Главное', "Газета &quot;Коммерсантъ&quot;. Главное")
            category = Path(path).stem
            for node in ET.fromstring(text).iter("outline"):
                address = node.get("xmlUrl", "").strip()
                parsed = urlsplit(address)
                if not address or parsed.scheme not in {"https", "http"} or not parsed.hostname or parsed.username:
                    continue
                record = sources.setdefault(address, {
                    "title": node.get("title", node.get("text", "")).strip()[:500],
                    "feedUrl": address,
                    "siteUrl": node.get("htmlUrl", "").strip(),
                    "description": re.sub(r"\s+", " ", node.get("description", "")).strip()[:1000],
                    "language": "", "topics": [], "tags": [],
                })
                record["tags"] = sorted(set(record["tags"] + [category]))
                record["topics"] = sorted(set(record["topics"] + [topic for topic, names in TOPICS.items() if category in names]))
        records = sorted(sources.values(), key=lambda r: (r["title"].casefold(), r["feedUrl"]))
        for record in records:
            record["slug"] = "curated-" + hashlib.sha256(record["feedUrl"].encode()).hexdigest()[:20]
        if not 800 <= len(records) <= 1000:
            raise ValueError(f"Unexpected catalog size: {len(records)}")
        license_path = next(p for p in package.namelist() if p.endswith("/LICENSE"))
        return records, package.read(license_path)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--archive", type=Path, help="Use an already downloaded pinned archive")
    args = parser.parse_args()
    archive = args.archive.read_bytes() if args.archive else urllib.request.urlopen(UPSTREAM, timeout=30).read()
    records, license_text = compile_catalog(archive)
    destination = ROOT / "app/src/main/assets/feed_catalog"
    destination.mkdir(parents=True, exist_ok=True)
    (destination / "plenary.json").write_text(json.dumps({"revision": REVISION, "sources": records}, ensure_ascii=False, indent=2) + "\n")
    (destination / "LICENSE.txt").write_bytes(license_text)
    print(f"Generated {len(records)} catalog sources from {REVISION}")

if __name__ == "__main__":
    main()
