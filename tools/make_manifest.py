"""Builds companion.json for a release: one entry per jar in the dist/ folder.

File name: phase-companion-<loader>-<game version>-<mod version>.jar.
Phase reads this manifest and installs the mod for the host,
checking the size and BLAKE3.
"""
import json
import re
import sys
from pathlib import Path

import blake3

repo, tag, dist = sys.argv[1], sys.argv[2], Path(sys.argv[3])
pattern = re.compile(r"^phase-companion-(fabric|forge|neoforge)-(.+)-(\d+\.\d+\.\d+)\.jar$")
mods = []
for jar in sorted(dist.glob("*.jar")):
    match = pattern.match(jar.name)
    if not match:
        sys.exit(f"unexpected file name: {jar.name}")
    loader, mc, version = match.groups()
    data = jar.read_bytes()
    mods.append({
        "loader": loader,
        "mc": [mc],
        "version": version,
        "file": jar.name,
        "url": f"https://github.com/{repo}/releases/download/{tag}/{jar.name}",
        "size": len(data),
        "blake3": blake3.blake3(data).hexdigest(),
    })
(dist / "companion.json").write_text(
    json.dumps({"schema": 1, "mods": mods}, indent=2, ensure_ascii=False) + "\n",
    encoding="utf-8",
)
print(f"companion.json: {len(mods)} jar(s)")
