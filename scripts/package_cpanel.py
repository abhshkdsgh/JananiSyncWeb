#!/usr/bin/env python3
"""Package the completed Kotlin/JS production distribution for cPanel."""

import hashlib
import json
from pathlib import Path
import re
import shutil
from urllib.parse import unquote
from zipfile import ZIP_DEFLATED, ZipFile


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "webApp/build/dist/js/productionExecutable"
DESTINATION = ROOT / "cpanel-package"
ARCHIVE = ROOT / "jananisync-cpanel.zip"

HTACCESS = r"""# Serve JananiSync from this domain/subdomain's document root.
DirectoryIndex index.html

<IfModule mod_mime.c>
  AddType application/wasm .wasm
  AddType text/javascript .js .mjs
  AddType application/manifest+json .webmanifest
</IfModule>

# Bundle filenames are not all fingerprinted. Revalidate to discover updates.
# The service worker separately manages the versioned offline application cache.
<IfModule mod_headers.c>
  <FilesMatch "\.(html|js|mjs|wasm|json|webmanifest|png)$">
    Header set Cache-Control "no-cache"
  </FilesMatch>
</IfModule>
"""


def main():
    for name in ("index.html", "webApp.js", "sw.js", "pwa-assets.json",
                 "manifest.webmanifest", "pwa.js", "chart-export.js", "user-manual.html"):
        if not (SOURCE / name).is_file():
            raise SystemExit(f"Missing {name}. Run ./gradlew :webApp:jsBrowserDistribution first.")

    worker = (SOURCE / "sw.js").read_text()
    version_match = re.search(r"const VERSION = '([0-9a-f]{64})';", worker)
    if not version_match:
        raise SystemExit("Service worker has no production build version; rebuild the distribution.")

    # Check the build's offline asset list before replacing a previous package.
    assets = json.loads((SOURCE / "pwa-assets.json").read_text())
    if not isinstance(assets, list) or not {"index.html", "webApp.js", "user-manual.html"}.issubset(assets):
        raise SystemExit("Invalid production asset list.")
    deployable_assets = []
    for asset in assets:
        relative = Path(unquote(asset))
        if relative.is_absolute() or ".." in relative.parts:
            raise SystemExit(f"Invalid asset path: {asset}")
        # Finder can add metadata inside the distribution before Gradle generates
        # its asset list. Never publish it or leave it as a precache dependency.
        if any(part == ".DS_Store" or part.startswith("._") for part in relative.parts):
            continue
        if (not (SOURCE / relative).is_file() or relative.suffix == ".map"
                or any(part.startswith(".") for part in relative.parts)):
            raise SystemExit(f"Missing or non-deployable cached asset: {asset}")
        deployable_assets.append(asset)

    if DESTINATION.exists():
        shutil.rmtree(DESTINATION)
    shutil.copytree(SOURCE, DESTINATION,
                    ignore=shutil.ignore_patterns("*.map", ".DS_Store", "._*"))
    (DESTINATION / ".htaccess").write_text(HTACCESS)
    asset_manifest = json.dumps(deployable_assets, ensure_ascii=False, indent=2) + "\n"
    (DESTINATION / "pwa-assets.json").write_text(asset_manifest)
    # Derive a release cache version from the original content-hashed build plus
    # the sanitised manifest, so the worker and its exact precache set stay paired.
    release_version = hashlib.sha256(
        (version_match.group(1) + "\n" + asset_manifest).encode("utf-8")
    ).hexdigest()
    worker = worker.replace(version_match.group(0), f"const VERSION = '{release_version}';", 1)
    (DESTINATION / "sw.js").write_text(worker)

    files = sorted(path for path in DESTINATION.rglob("*") if path.is_file())
    with ZipFile(ARCHIVE, "w", compression=ZIP_DEFLATED, compresslevel=9) as archive:
        for path in files:
            path.chmod(0o644)
            # No enclosing folder: index.html extracts directly into the document root.
            archive.write(path, path.relative_to(DESTINATION).as_posix())
    for directory in [DESTINATION, *(p for p in DESTINATION.rglob("*") if p.is_dir())]:
        directory.chmod(0o755)

    checksum = hashlib.sha256(ARCHIVE.read_bytes()).hexdigest()
    checksum_file = ROOT / "jananisync-cpanel.zip.sha256"
    checksum_file.write_text(f"{checksum}  {ARCHIVE.name}\n")
    with ZipFile(ARCHIVE) as archive:
        if archive.testzip() is not None:
            raise SystemExit("ZIP integrity check failed.")
    print(f"Created {ARCHIVE.name}: {len(files)} files, {ARCHIVE.stat().st_size:,} bytes")
    print(f"Upload directory: {DESTINATION}")
    print(f"SHA-256: {checksum}")


if __name__ == "__main__":
    main()
