#!/usr/bin/env python3
"""Validate one push-addressable 0.1.x maintenance release request."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any

SCHEMA = "ai-knowledge-maintenance-release-request/v1"
EXPECTED_KEYS = {
    "schema",
    "releaseVersion",
    "nextDevelopmentVersion",
    "skipTests",
    "dryRun",
    "requestId",
    "qualifiedCommit",
}
VERSION = re.compile(r"0\.1\.([0-9]+)")
NEXT_VERSION = re.compile(r"0\.1\.([0-9]+)-SNAPSHOT")
REQUEST_ID = re.compile(r"[a-z0-9][a-z0-9._-]{2,127}")
COMMIT = re.compile(r"[0-9a-f]{40}")
MAX_REQUEST_BYTES = 4_096


class RequestError(ValueError):
    """The request is not a canonical maintenance release request."""


def _unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise RequestError(f"duplicate request field: {key}")
        result[key] = value
    return result


def load_request(path: Path) -> dict[str, Any]:
    raw = path.read_bytes()
    if len(raw) > MAX_REQUEST_BYTES:
        raise RequestError("maintenance release request exceeds byte limit")
    try:
        text = raw.decode("utf-8", errors="strict")
    except UnicodeDecodeError as exception:
        raise RequestError("maintenance release request is not strict UTF-8") from exception
    if text.startswith("\ufeff"):
        raise RequestError("maintenance release request must not contain a BOM")
    try:
        value = json.loads(text, object_pairs_hook=_unique_object)
    except (json.JSONDecodeError, RequestError) as exception:
        raise RequestError("maintenance release request is not canonical JSON") from exception
    if not isinstance(value, dict):
        raise RequestError("maintenance release request must be one JSON object")
    if set(value) != EXPECTED_KEYS:
        raise RequestError(
            "maintenance release request fields must be exactly: "
            + ", ".join(sorted(EXPECTED_KEYS)))
    return value


def validate_request(value: dict[str, Any]) -> dict[str, str]:
    if value["schema"] != SCHEMA:
        raise RequestError("unsupported maintenance release request schema")

    release = value["releaseVersion"]
    next_version = value["nextDevelopmentVersion"]
    request_id = value["requestId"]
    qualified_commit = value["qualifiedCommit"]
    skip_tests = value["skipTests"]
    dry_run = value["dryRun"]

    if not isinstance(release, str) or not (release_match := VERSION.fullmatch(release)):
        raise RequestError("releaseVersion must use 0.1.X")
    if not isinstance(next_version, str) or not (
            next_match := NEXT_VERSION.fullmatch(next_version)):
        raise RequestError("nextDevelopmentVersion must use 0.1.X-SNAPSHOT")
    if int(next_match.group(1)) != int(release_match.group(1)) + 1:
        raise RequestError("nextDevelopmentVersion must be the next 0.1.x patch")
    if type(skip_tests) is not bool or type(dry_run) is not bool:
        raise RequestError("skipTests and dryRun must be JSON booleans")
    if skip_tests:
        raise RequestError("push-addressable maintenance releases may not skip tests")
    if not isinstance(request_id, str) or not REQUEST_ID.fullmatch(request_id):
        raise RequestError("requestId is not a canonical request token")
    if not isinstance(qualified_commit, str) or not COMMIT.fullmatch(qualified_commit):
        raise RequestError("qualifiedCommit must be a lowercase full commit SHA")

    return {
        "release_version": release,
        "next_version": next_version,
        "skip_tests": "false",
        "dry_run": str(dry_run).lower(),
        "request_id": request_id,
        "qualified_commit": qualified_commit,
    }


def write_outputs(values: dict[str, str], output: Path) -> None:
    with output.open("a", encoding="utf-8", newline="\n") as handle:
        for key, value in values.items():
            handle.write(f"{key}={value}\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--request", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()
    write_outputs(validate_request(load_request(arguments.request)), arguments.output)


if __name__ == "__main__":
    try:
        main()
    except (OSError, RequestError) as exception:
        raise SystemExit(str(exception)) from exception
