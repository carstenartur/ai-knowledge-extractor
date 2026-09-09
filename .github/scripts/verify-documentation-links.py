#!/usr/bin/env python3
"""Check local targets in the provider and schema migration documentation."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
for name in ("README.md", "CHANGELOG.md", "docs/release.md", "docs/releases/0.2.0.md",
             "docs/provider-spi.md", "docs/schema-v2-migration.md"):
    source = ROOT / name
    for target in re.findall(r"\[[^\]]*\]\(([^)]+)\)", source.read_text()):
        if target.startswith(("https:", "http:", "mailto:", "#")):
            continue
        path = target.split("#", 1)[0]
        if not (source.parent / path).exists():
            raise SystemExit(f"Broken documentation link: {name}: {target}")
print("Provider and migration documentation links passed")
