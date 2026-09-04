#!/usr/bin/env python3
"""Verify repository version metadata and the supported release-line contract."""

from __future__ import annotations

import json
import os
import re
from pathlib import Path

from release_line_policy import PolicyError, load_policy, resolve_line

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
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+-SNAPSHOT", project_version):
        raise SystemExit(
            "Development metadata must use projectVersion=X.Y.Z-SNAPSHOT, "
            f"found {project_version!r}"
        )
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
        "Maven plugin descriptor",
        first_match(
            "maven/src/main/resources/META-INF/maven/plugin.xml",
            r"<version>([^<]+)</version>",
        ),
        project_version,
    )

    require_equal(
        "release.properties next.release.version",
        first_match("release.properties", r"^next\.release\.version=(.+)$"),
        expected_release,
    )

    try:
        policy = load_policy(ROOT / ".github/release-lines.json")
        target_branch = os.environ.get("AI_KNOWLEDGE_TARGET_BRANCH", "").strip()
        if target_branch:
            resolve_line(
                policy,
                branch=target_branch,
                release_version=expected_release,
                dry_run=False,
            )
    except PolicyError as exc:
        raise SystemExit(f"Supported release-line contract is invalid: {exc}") from exc

    if not target_branch or target_branch == "main":
        readme = read("README.md")
        if project_version in readme:
            raise SystemExit(
                "README.md contains the concrete development version; use <version>, a release "
                "line such as 0.2.x, or link to the release documentation instead."
            )
        if "docs/version-support.md" not in readme:
            raise SystemExit("README.md must link to docs/version-support.md")
        for line in policy.lines:
            if line.releasable and line.branch not in readme:
                raise SystemExit(
                    f"README.md must name supported branch {line.branch!r} from release-lines.json"
                )


if __name__ == "__main__":
    main()
