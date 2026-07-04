#!/usr/bin/env python3
"""Verify that repository version metadata stays aligned.

Development snapshots intentionally use X.Y.Z-SNAPSHOT in project metadata while
release.properties stores the next release version without the -SNAPSHOT suffix.
"""

from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def first_match(path: str, pattern: str) -> str:
    text = read(path)
    match = re.search(pattern, text, re.MULTILINE)
    if not match:
        raise SystemExit(f"Could not find version pattern in {path}: {pattern}")
    return match.group(1).strip()


def require_equal(label: str, actual: str, expected: str) -> None:
    if actual != expected:
        raise SystemExit(f"{label} version {actual!r} != expected {expected!r}")


def main() -> None:
    project_version = first_match("gradle.properties", r"^projectVersion=(.+)$")
    expected_release = project_version.removesuffix("-SNAPSHOT")

    require_equal(
        "CITATION.cff",
        first_match("CITATION.cff", r'^version:\s*"([^"]+)"$'),
        project_version,
    )

    zenodo = json.loads(read(".zenodo.json"))
    require_equal(".zenodo.json", str(zenodo.get("version")), project_version)

    require_equal(
        "Maven site revision",
        first_match("site/pom.xml", r"<revision>([^<]+)</revision>"),
        project_version,
    )

    for path in (
        "examples/maven-consumer/pom.xml",
        "examples/fixtures/maven-consumer/pom.xml",
    ):
        require_equal(
            path,
            first_match(path, r"<aiKnowledge\.version>([^<]+)</aiKnowledge\.version>"),
            project_version,
        )

    require_equal(
        "release.properties next.release.version",
        first_match("release.properties", r"^next\.release\.version=(.+)$"),
        expected_release,
    )

    readme = read("README.md")
    if project_version in readme:
        raise SystemExit(
            "README.md contains the concrete development version; use <version> "
            "or link to the release documentation instead."
        )


if __name__ == "__main__":
    main()
