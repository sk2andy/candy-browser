"""Package verified browser builds and reviewer sources without local credentials."""

import hashlib
import json
from pathlib import Path
import shutil
import zipfile


ROOT = Path(__file__).resolve().parent.parent
REPOSITORY = ROOT.parent.parent


def files(directory):
    return sorted(
        path for path in directory.rglob("*")
        if path.is_file() and "__pycache__" not in path.parts and path.suffix != ".pyc"
    )


def archive(target, entries):
    with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as output:
        for name, source in sorted(entries):
            if source.is_symlink():
                raise ValueError(f"Symlinks are not allowed in store packages: {source}")
            info = zipfile.ZipInfo(name, date_time=(2020, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            output.writestr(info, source.read_bytes(), compresslevel=9)


def main():
    version = json.loads((ROOT / "package.json").read_text())["version"]
    for browser in ("chromium", "firefox"):
        manifest = json.loads((ROOT / "dist" / browser / "manifest.json").read_text())
        if manifest["version"] != version:
            raise ValueError(f"Stale {browser} build; run npm run verify before packaging")

    release = ROOT / "release"
    shutil.rmtree(release, ignore_errors=True)
    release.mkdir()
    for browser in ("chromium", "firefox"):
        directory = ROOT / "dist" / browser
        archive(release / f"candy-sync-{browser}-{version}.zip", [
            (str(source.relative_to(directory)), source) for source in files(directory)
        ])

    sources = []
    for directory in ("src", "scripts", "manifests", "assets", "tests", "store"):
        sources.extend(files(ROOT / directory))
    sources.extend(ROOT / name for name in (
        "package.json", "package-lock.json", "tsconfig.json", "README.md", "SECURITY.md",
    ))
    sources.extend(files(ROOT.parent / "protocol"))
    sources.append(REPOSITORY / "LICENSE")
    entries = [(str(source.relative_to(REPOSITORY)), source) for source in sources]
    entries.append(("BUILD.md", ROOT / "store/SOURCE_BUILD.md"))
    archive(release / f"candy-sync-source-{version}.zip", entries)

    checksums = "".join(
        f"{hashlib.sha256(package.read_bytes()).hexdigest()}  {package.name}\n"
        for package in sorted(release.glob("*.zip"))
    )
    (release / "SHA256SUMS").write_text(checksums)
    print(f"Store packages and reviewer sources: {release}")


if __name__ == "__main__":
    main()
