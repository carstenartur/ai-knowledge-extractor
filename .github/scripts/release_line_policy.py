#!/usr/bin/env python3
"""Validate supported release lines and reviewed release requests."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import re
import shlex
from typing import Any

SERIES_RE = re.compile(r"^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$")
VERSION_RE = re.compile(
    r"^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$"
)
SNAPSHOT_RE = re.compile(
    r"^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)-SNAPSHOT$"
)
BRANCH_RE = re.compile(r"^[A-Za-z0-9._/-]+$")
REQUEST_ID_RE = re.compile(r"^[a-z0-9][a-z0-9._-]{2,63}$")
COMMIT_RE = re.compile(r"^[0-9a-f]{40}$")

POLICY_TOP_LEVEL_KEYS = {"schemaVersion", "releaseLines"}
POLICY_LINE_KEYS = {
    "series",
    "branch",
    "status",
    "releaseLatest",
    "nextVersionPolicy",
    "artifactContract",
    "supportPolicy",
}
REQUEST_KEYS = {
    "schemaVersion",
    "releaseVersion",
    "nextDevelopmentVersion",
    "skipTests",
    "dryRun",
    "requestId",
    "qualifiedCommit",
}
SUPPORTED_STATUSES = {"active", "maintenance", "end-of-life"}
SUPPORTED_NEXT_POLICIES = {"same-series", "none"}


class PolicyError(ValueError):
    """Raised when a release policy or request violates the repository contract."""


@dataclass(frozen=True)
class ReleaseLine:
    series: str
    branch: str
    status: str
    release_latest: bool
    next_version_policy: str
    artifact_contract: str
    support_policy: str

    @property
    def name(self) -> str:
        return f"{self.series}.x"

    @property
    def releasable(self) -> bool:
        return self.status in {"active", "maintenance"}


@dataclass(frozen=True)
class ReleasePolicy:
    path: Path
    lines: tuple[ReleaseLine, ...]

    def by_branch(self, branch: str) -> ReleaseLine | None:
        return next((line for line in self.lines if line.branch == branch), None)


@dataclass(frozen=True)
class ReleaseRequest:
    release_version: str
    next_development_version: str
    skip_tests: bool
    dry_run: bool
    request_id: str
    qualified_commit: str


def _reject_duplicate_fields(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise PolicyError(f"Duplicate JSON field: {key!r}")
        result[key] = value
    return result


def _load_strict_json(path: Path, label: str) -> Any:
    try:
        return json.loads(
            path.read_text(encoding="utf-8"),
            object_pairs_hook=_reject_duplicate_fields,
        )
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise PolicyError(f"Could not read {label} {path}: {exc}") from exc


def _require_exact_fields(
    value: Any, *, allowed: set[str], label: str
) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise PolicyError(f"{label} must be one JSON object")
    unknown = set(value) - allowed
    missing = allowed - set(value)
    if unknown:
        raise PolicyError(f"{label} has unknown fields: {sorted(unknown)}")
    if missing:
        raise PolicyError(f"{label} is missing fields: {sorted(missing)}")
    return value


def _require_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise PolicyError(f"{label} must be a non-empty string")
    if value != value.strip():
        raise PolicyError(f"{label} must not contain surrounding whitespace")
    return value


def _safe_branch(branch: str, label: str = "branch") -> str:
    branch = _require_string(branch, label)
    if (
        not BRANCH_RE.fullmatch(branch)
        or branch.startswith("/")
        or branch.endswith("/")
        or "//" in branch
        or ".." in branch
        or "@{" in branch
        or branch.endswith(".lock")
    ):
        raise PolicyError(f"{label} is not a safe branch name: {branch!r}")
    return branch


def version_series(version: str) -> str:
    normalized = version.removesuffix("-SNAPSHOT")
    match = VERSION_RE.fullmatch(normalized)
    if not match:
        raise PolicyError(f"Version must use X.Y.Z or X.Y.Z-SNAPSHOT, got {version!r}")
    return f"{match.group(1)}.{match.group(2)}"


def version_tuple(version: str) -> tuple[int, int, int]:
    normalized = version.removesuffix("-SNAPSHOT")
    match = VERSION_RE.fullmatch(normalized)
    if not match:
        raise PolicyError(f"Version must use X.Y.Z or X.Y.Z-SNAPSHOT, got {version!r}")
    return tuple(int(match.group(index)) for index in range(1, 4))


def load_policy(path: Path) -> ReleasePolicy:
    raw = _load_strict_json(path, "release-line policy")
    policy = _require_exact_fields(
        raw, allowed=POLICY_TOP_LEVEL_KEYS, label="Release-line policy"
    )
    if policy["schemaVersion"] != 1:
        raise PolicyError("release-lines.json schemaVersion must be 1")
    raw_lines = policy["releaseLines"]
    if not isinstance(raw_lines, list) or not raw_lines:
        raise PolicyError("releaseLines must be a non-empty array")

    lines: list[ReleaseLine] = []
    for index, raw_line in enumerate(raw_lines):
        label = f"releaseLines[{index}]"
        item = _require_exact_fields(
            raw_line, allowed=POLICY_LINE_KEYS, label=label
        )
        series = _require_string(item["series"], f"{label}.series")
        branch = _safe_branch(item["branch"], f"{label}.branch")
        status = _require_string(item["status"], f"{label}.status")
        next_policy = _require_string(
            item["nextVersionPolicy"], f"{label}.nextVersionPolicy"
        )
        artifact_contract = _require_string(
            item["artifactContract"], f"{label}.artifactContract"
        )
        support_policy = _require_string(
            item["supportPolicy"], f"{label}.supportPolicy"
        )
        release_latest = item["releaseLatest"]

        if not SERIES_RE.fullmatch(series):
            raise PolicyError(f"{label}.series must use X.Y, got {series!r}")
        if status not in SUPPORTED_STATUSES:
            raise PolicyError(
                f"{label}.status must be one of {sorted(SUPPORTED_STATUSES)}"
            )
        if not isinstance(release_latest, bool):
            raise PolicyError(f"{label}.releaseLatest must be boolean")
        if next_policy not in SUPPORTED_NEXT_POLICIES:
            raise PolicyError(
                f"{label}.nextVersionPolicy must be one of "
                f"{sorted(SUPPORTED_NEXT_POLICIES)}"
            )

        lines.append(
            ReleaseLine(
                series=series,
                branch=branch,
                status=status,
                release_latest=release_latest,
                next_version_policy=next_policy,
                artifact_contract=artifact_contract,
                support_policy=support_policy,
            )
        )

    branches = [line.branch for line in lines]
    series_values = [line.series for line in lines]
    if len(branches) != len(set(branches)):
        raise PolicyError("Every release line must use a unique branch")
    if len(series_values) != len(set(series_values)):
        raise PolicyError("Every release line must use a unique X.Y series")

    active = [line for line in lines if line.status == "active"]
    latest = [line for line in lines if line.release_latest]
    if len(active) != 1:
        raise PolicyError("Exactly one release line must have status 'active'")
    if len(latest) != 1:
        raise PolicyError("Exactly one release line must set releaseLatest=true")
    if active[0] != latest[0]:
        raise PolicyError("The active line must be the only latest release line")
    if active[0].branch != "main":
        raise PolicyError("The active release line must use branch 'main'")
    if active[0].next_version_policy != "same-series":
        raise PolicyError("The active line must use nextVersionPolicy='same-series'")

    for line in lines:
        if line.status == "maintenance":
            expected_branch = f"maintenance/{line.series}.x"
            if line.branch != expected_branch:
                raise PolicyError(
                    f"Maintenance line {line.name} must use branch {expected_branch!r}"
                )
            if line.release_latest:
                raise PolicyError("Maintenance lines cannot be marked latest")
            if line.next_version_policy != "same-series":
                raise PolicyError(
                    "Maintenance lines must use nextVersionPolicy='same-series'"
                )
        elif line.status == "end-of-life":
            if line.release_latest:
                raise PolicyError("End-of-life lines cannot be marked latest")
            if line.next_version_policy != "none":
                raise PolicyError(
                    "End-of-life lines must use nextVersionPolicy='none'"
                )

    return ReleasePolicy(path=path, lines=tuple(lines))


def resolve_line(
    policy: ReleasePolicy,
    *,
    branch: str,
    release_version: str,
    dry_run: bool,
) -> ReleaseLine | None:
    branch = _safe_branch(branch, "source branch")
    actual_series = version_series(release_version)
    line = policy.by_branch(branch)
    if line is None:
        if dry_run:
            return None
        supported = ", ".join(
            sorted(item.branch for item in policy.lines if item.releasable)
        )
        raise PolicyError(
            "Real releases are allowed only from configured supported branches: "
            f"{supported}; got {branch!r}"
        )
    if not line.releasable:
        raise PolicyError(f"Release line {line.name} is end-of-life and cannot publish")
    if actual_series != line.series:
        raise PolicyError(
            f"Branch {branch!r} is configured for {line.name}, but release "
            f"{release_version} belongs to {actual_series}.x"
        )
    return line


def verify_branch_version(
    policy: ReleasePolicy, *, branch: str, project_version: str
) -> ReleaseLine:
    branch = _safe_branch(branch, "target branch")
    if not SNAPSHOT_RE.fullmatch(project_version):
        raise PolicyError("Branch development version must use X.Y.Z-SNAPSHOT")
    line = policy.by_branch(branch)
    if line is None:
        raise PolicyError(f"Target branch {branch!r} is not a configured release line")
    actual_series = version_series(project_version)
    if actual_series != line.series:
        raise PolicyError(
            f"Branch {branch!r} is configured for {line.name}, but development version "
            f"{project_version} belongs to {actual_series}.x"
        )
    return line


def validate_next_version(
    line: ReleaseLine | None,
    *,
    release_version: str,
    next_version: str,
) -> None:
    if not SNAPSHOT_RE.fullmatch(next_version):
        raise PolicyError(
            f"Next development version must use X.Y.Z-SNAPSHOT, got {next_version!r}"
        )
    if version_tuple(next_version) <= version_tuple(release_version):
        raise PolicyError(
            f"Next development version {next_version} must be newer than {release_version}"
        )
    if line is None:
        return
    next_series = version_series(next_version)
    if line.next_version_policy == "same-series" and next_series != line.series:
        raise PolicyError(
            f"Release line {line.name} must remain in series {line.series}; "
            f"got {next_version}"
        )
    if line.next_version_policy == "none":
        raise PolicyError(f"Release line {line.name} does not accept a next version")


def load_release_request(path: Path) -> ReleaseRequest:
    raw = _load_strict_json(path, "release request")
    item = _require_exact_fields(raw, allowed=REQUEST_KEYS, label="Release request")
    if item["schemaVersion"] != 1:
        raise PolicyError("Release request schemaVersion must be 1")

    release_version = _require_string(item["releaseVersion"], "releaseVersion")
    next_version = _require_string(
        item["nextDevelopmentVersion"], "nextDevelopmentVersion"
    )
    request_id = _require_string(item["requestId"], "requestId")
    qualified_commit = _require_string(item["qualifiedCommit"], "qualifiedCommit")
    if not VERSION_RE.fullmatch(release_version):
        raise PolicyError("releaseVersion must use X.Y.Z without a leading v")
    if not SNAPSHOT_RE.fullmatch(next_version):
        raise PolicyError("nextDevelopmentVersion must use X.Y.Z-SNAPSHOT")
    if not REQUEST_ID_RE.fullmatch(request_id):
        raise PolicyError(
            "requestId must contain 3-64 lowercase letters, digits, dots, underscores or dashes"
        )
    if not COMMIT_RE.fullmatch(qualified_commit):
        raise PolicyError("qualifiedCommit must be a lowercase 40-character commit SHA")
    if not isinstance(item["skipTests"], bool):
        raise PolicyError("skipTests must be boolean")
    if not isinstance(item["dryRun"], bool):
        raise PolicyError("dryRun must be boolean")

    return ReleaseRequest(
        release_version=release_version,
        next_development_version=next_version,
        skip_tests=item["skipTests"],
        dry_run=item["dryRun"],
        request_id=request_id,
        qualified_commit=qualified_commit,
    )


def env_values(line: ReleaseLine | None, branch: str) -> dict[str, str]:
    if line is None:
        return {
            "RELEASE_SOURCE_LINE": "unconfigured-dry-run",
            "RELEASE_SOURCE_SERIES": "",
            "RELEASE_SOURCE_STATUS": "dry-run",
            "RELEASE_NEXT_PR_BASE": branch,
            "RELEASE_LATEST_ARGUMENT": "--latest=false",
            "RELEASE_NEXT_VERSION_POLICY": "same-series",
            "RELEASE_ARTIFACT_CONTRACT": "unconfigured",
        }
    return {
        "RELEASE_SOURCE_LINE": line.name,
        "RELEASE_SOURCE_SERIES": line.series,
        "RELEASE_SOURCE_STATUS": line.status,
        "RELEASE_NEXT_PR_BASE": line.branch,
        "RELEASE_LATEST_ARGUMENT": (
            "--latest" if line.release_latest else "--latest=false"
        ),
        "RELEASE_NEXT_VERSION_POLICY": line.next_version_policy,
        "RELEASE_ARTIFACT_CONTRACT": line.artifact_contract,
    }


def request_env_values(
    request: ReleaseRequest, line: ReleaseLine | None, branch: str
) -> dict[str, str]:
    values = env_values(line, branch)
    values.update(
        {
            "REQUEST_RELEASE_VERSION": request.release_version,
            "REQUEST_NEXT_DEVELOPMENT_VERSION": request.next_development_version,
            "REQUEST_SKIP_TESTS": str(request.skip_tests).lower(),
            "REQUEST_DRY_RUN": str(request.dry_run).lower(),
            "REQUEST_ID": request.request_id,
            "REQUEST_QUALIFIED_COMMIT": request.qualified_commit,
        }
    )
    return values


def write_env_file(path: Path, values: dict[str, str]) -> None:
    path.write_text(
        "".join(f"{key}={shlex.quote(value)}\n" for key, value in values.items()),
        encoding="utf-8",
    )


def parse_bool(value: str) -> bool:
    if value == "true":
        return True
    if value == "false":
        return False
    raise PolicyError(f"Boolean value must be 'true' or 'false', got {value!r}")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--policy", type=Path, default=Path(".github/release-lines.json")
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    subparsers.add_parser("validate")

    resolve = subparsers.add_parser("resolve")
    resolve.add_argument("--branch", required=True)
    resolve.add_argument("--release-version", required=True)
    resolve.add_argument("--dry-run", choices=("true", "false"), default="false")
    resolve.add_argument("--env-file", type=Path, required=True)

    validate_next = subparsers.add_parser("validate-next")
    validate_next.add_argument("--branch", required=True)
    validate_next.add_argument("--release-version", required=True)
    validate_next.add_argument("--next-version", required=True)
    validate_next.add_argument("--dry-run", choices=("true", "false"), default="false")

    verify = subparsers.add_parser("verify-branch-version")
    verify.add_argument("--branch", required=True)
    verify.add_argument("--project-version", required=True)

    request = subparsers.add_parser("resolve-request")
    request.add_argument("--request", type=Path, required=True)
    request.add_argument("--branch", required=True)
    request.add_argument("--current-version", required=True)
    request.add_argument("--env-file", type=Path, required=True)

    return parser


def main() -> None:
    args = build_parser().parse_args()
    try:
        policy = load_policy(args.policy)
        if args.command == "validate":
            print("release-lines=VALID")
        elif args.command == "resolve":
            line = resolve_line(
                policy,
                branch=args.branch,
                release_version=args.release_version,
                dry_run=parse_bool(args.dry_run),
            )
            write_env_file(args.env_file, env_values(line, args.branch))
        elif args.command == "validate-next":
            line = resolve_line(
                policy,
                branch=args.branch,
                release_version=args.release_version,
                dry_run=parse_bool(args.dry_run),
            )
            validate_next_version(
                line,
                release_version=args.release_version,
                next_version=args.next_version,
            )
        elif args.command == "verify-branch-version":
            verify_branch_version(
                policy, branch=args.branch, project_version=args.project_version
            )
        elif args.command == "resolve-request":
            request = load_release_request(args.request)
            expected_current = f"{request.release_version}-SNAPSHOT"
            if args.current_version != expected_current:
                raise PolicyError(
                    f"Release request expects {expected_current}, but the selected branch "
                    f"contains {args.current_version}"
                )
            line = resolve_line(
                policy,
                branch=args.branch,
                release_version=request.release_version,
                dry_run=request.dry_run,
            )
            validate_next_version(
                line,
                release_version=request.release_version,
                next_version=request.next_development_version,
            )
            write_env_file(
                args.env_file, request_env_values(request, line, args.branch)
            )
        else:  # pragma: no cover
            raise AssertionError(args.command)
    except PolicyError as exc:
        raise SystemExit(f"release-line policy error: {exc}") from exc


if __name__ == "__main__":
    main()
