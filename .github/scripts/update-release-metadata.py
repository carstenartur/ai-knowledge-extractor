#!/usr/bin/env python3
"""Keep release metadata and documentation versions aligned."""

from __future__ import annotations

import argparse
import datetime as dt
import json
from pathlib import Path
import re


SITE_POM = Path("site/pom.xml")
MAVEN_EXAMPLE_POMS = [
    Path("examples/maven-consumer/pom.xml"),
    Path("examples/fixtures/maven-consumer/pom.xml"),
]


def set_cff_key(text: str, key: str, value: str) -> str:
    line = f'{key}: "{value}"'
    pattern = rf'^{re.escape(key)}: .*$'
    if re.search(pattern, text, flags=re.MULTILINE):
        return re.sub(pattern, line, text, flags=re.MULTILINE)
    if not text.endswith("\n"):
        text += "\n"
    return text + line + "\n"


def remove_cff_key(text: str, key: str) -> str:
    return re.sub(rf'^{re.escape(key)}: .*\n?', "", text, flags=re.MULTILINE)


def replace_one(path: Path, pattern: str, replacement: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    text, count = re.subn(pattern, replacement, text, count=1)
    if count != 1:
        raise SystemExit(f"Could not update {label} in {path}")
    path.write_text(text, encoding="utf-8")


def update_site_revision(version: str) -> None:
    replace_one(
        SITE_POM,
        r"(<revision>)[^<]+(</revision>)",
        rf"\g<1>{version}\g<2>",
        "site revision",
    )


def update_maven_example_versions(version: str) -> None:
    for path in MAVEN_EXAMPLE_POMS:
        replace_one(
            path,
            r"(<aiKnowledge\.version>)[^<]+(</aiKnowledge\.version>)",
            rf"\g<1>{version}\g<2>",
            "aiKnowledge.version",
        )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("version")
    parser.add_argument(
        "--release",
        action="store_true",
        help="Add release-date fields; otherwise remove release-only date fields.",
    )
    args = parser.parse_args()

    release_date = dt.date.today().isoformat()

    citation = Path("CITATION.cff")
    citation_text = citation.read_text(encoding="utf-8")
    citation_text = set_cff_key(citation_text, "version", args.version)
    if args.release:
        citation_text = set_cff_key(citation_text, "date-released", release_date)
    else:
        citation_text = remove_cff_key(citation_text, "date-released")
    citation.write_text(citation_text, encoding="utf-8")

    zenodo = Path(".zenodo.json")
    zenodo_data = json.loads(zenodo.read_text(encoding="utf-8"))
    zenodo_data["version"] = args.version
    if args.release:
        zenodo_data["publication_date"] = release_date
    else:
        zenodo_data.pop("publication_date", None)
    zenodo.write_text(
        json.dumps(zenodo_data, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )

    update_site_revision(args.version)
    update_maven_example_versions(args.version)


if __name__ == "__main__":
    main()
