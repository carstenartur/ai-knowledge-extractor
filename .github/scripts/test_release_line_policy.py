#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("release_line_policy.py")
SPEC = importlib.util.spec_from_file_location("release_line_policy", SCRIPT)
assert SPEC and SPEC.loader
policy_module = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = policy_module
SPEC.loader.exec_module(policy_module)

PolicyError = policy_module.PolicyError
load_policy = policy_module.load_policy
load_release_request = policy_module.load_release_request
resolve_line = policy_module.resolve_line
validate_next_version = policy_module.validate_next_version
request_env_values = policy_module.request_env_values
verify_branch_version = policy_module.verify_branch_version


def valid_document() -> dict:
    return {
        "schemaVersion": 1,
        "releaseLines": [
            {
                "series": "0.2",
                "branch": "main",
                "status": "active",
                "releaseLatest": True,
                "nextVersionPolicy": "same-series",
                "artifactContract": "schema-v2",
                "supportPolicy": "All current work",
            },
            {
                "series": "0.1",
                "branch": "maintenance/0.1.x",
                "status": "maintenance",
                "releaseLatest": False,
                "nextVersionPolicy": "same-series",
                "artifactContract": "schema-v1",
                "supportPolicy": "Critical compatibility fixes",
            },
        ],
    }


def valid_request() -> dict:
    return {
        "schemaVersion": 1,
        "releaseVersion": "0.1.10",
        "nextDevelopmentVersion": "0.1.11-SNAPSHOT",
        "skipTests": False,
        "dryRun": True,
        "requestId": "maintenance-0.1.10-dry-run",
        "qualifiedCommit": "a" * 40,
    }


class ReleaseLinePolicyTest(unittest.TestCase):
    def write_json(self, document: dict, name: str = "document.json") -> tuple[tempfile.TemporaryDirectory, Path]:
        directory = tempfile.TemporaryDirectory()
        path = Path(directory.name) / name
        path.write_text(json.dumps(document), encoding="utf-8")
        return directory, path

    def load_valid_policy(self):
        directory, path = self.write_json(valid_document(), "release-lines.json")
        self.addCleanup(directory.cleanup)
        return load_policy(path)

    def test_resolves_active_and_maintenance_lines(self) -> None:
        policy = self.load_valid_policy()
        active = resolve_line(
            policy, branch="main", release_version="0.2.1", dry_run=False
        )
        maintenance = resolve_line(
            policy,
            branch="maintenance/0.1.x",
            release_version="0.1.10",
            dry_run=False,
        )
        self.assertEqual("active", active.status)
        self.assertTrue(active.release_latest)
        self.assertEqual("maintenance", maintenance.status)
        self.assertFalse(maintenance.release_latest)

    def test_unknown_real_branch_is_rejected_but_dry_run_is_allowed(self) -> None:
        policy = self.load_valid_policy()
        with self.assertRaises(PolicyError):
            resolve_line(
                policy,
                branch="feature/example",
                release_version="0.2.1",
                dry_run=False,
            )
        self.assertIsNone(
            resolve_line(
                policy,
                branch="feature/example",
                release_version="0.2.1",
                dry_run=True,
            )
        )

    def test_branch_and_version_series_must_match(self) -> None:
        policy = self.load_valid_policy()
        with self.assertRaises(PolicyError):
            resolve_line(
                policy,
                branch="maintenance/0.1.x",
                release_version="0.2.1",
                dry_run=False,
            )

    def test_maintenance_line_cannot_advance_to_a_new_series(self) -> None:
        policy = self.load_valid_policy()
        line = policy.by_branch("maintenance/0.1.x")
        with self.assertRaises(PolicyError):
            validate_next_version(
                line, release_version="0.1.10", next_version="0.2.0-SNAPSHOT"
            )
        validate_next_version(
            line, release_version="0.1.10", next_version="0.1.11-SNAPSHOT"
        )

    def test_active_line_requires_an_explicit_policy_change_for_a_new_series(self) -> None:
        policy = self.load_valid_policy()
        line = policy.by_branch("main")
        with self.assertRaises(PolicyError):
            validate_next_version(
                line, release_version="0.2.1", next_version="0.3.0-SNAPSHOT"
            )
        validate_next_version(
            line, release_version="0.2.1", next_version="0.2.2-SNAPSHOT"
        )

    def test_duplicate_latest_line_is_rejected(self) -> None:
        document = valid_document()
        document["releaseLines"][1]["releaseLatest"] = True
        directory, path = self.write_json(document)
        self.addCleanup(directory.cleanup)
        with self.assertRaises(PolicyError):
            load_policy(path)

    def test_maintenance_branch_name_is_derived_from_series(self) -> None:
        document = valid_document()
        document["releaseLines"][1]["branch"] = "maintenance/legacy"
        directory, path = self.write_json(document)
        self.addCleanup(directory.cleanup)
        with self.assertRaises(PolicyError):
            load_policy(path)

    def test_duplicate_json_field_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "release-lines.json"
            path.write_text(
                '{"schemaVersion":1,"schemaVersion":1,"releaseLines":[]}',
                encoding="utf-8",
            )
            with self.assertRaisesRegex(PolicyError, "Duplicate JSON field"):
                load_policy(path)

    def test_end_of_life_line_is_not_releasable(self) -> None:
        document = valid_document()
        document["releaseLines"].append(
            {
                "series": "0.0",
                "branch": "archive/0.0.x",
                "status": "end-of-life",
                "releaseLatest": False,
                "nextVersionPolicy": "none",
                "artifactContract": "legacy",
                "supportPolicy": "No updates",
            }
        )
        directory, path = self.write_json(document)
        self.addCleanup(directory.cleanup)
        policy = load_policy(path)
        with self.assertRaises(PolicyError):
            resolve_line(
                policy,
                branch="archive/0.0.x",
                release_version="0.0.9",
                dry_run=False,
            )

    def test_end_of_life_branch_can_still_validate_its_frozen_version(self) -> None:
        document = valid_document()
        document["releaseLines"].append(
            {
                "series": "0.0",
                "branch": "archive/0.0.x",
                "status": "end-of-life",
                "releaseLatest": False,
                "nextVersionPolicy": "none",
                "artifactContract": "legacy",
                "supportPolicy": "No updates",
            }
        )
        directory, path = self.write_json(document)
        self.addCleanup(directory.cleanup)
        policy = load_policy(path)
        line = verify_branch_version(
            policy, branch="archive/0.0.x", project_version="0.0.10-SNAPSHOT"
        )
        self.assertEqual("end-of-life", line.status)

    def test_reviewed_request_is_strict_and_resolves_maintenance_policy(self) -> None:
        policy = self.load_valid_policy()
        directory, path = self.write_json(valid_request(), "release-request.json")
        self.addCleanup(directory.cleanup)
        request = load_release_request(path)
        line = resolve_line(
            policy,
            branch="maintenance/0.1.x",
            release_version=request.release_version,
            dry_run=request.dry_run,
        )
        validate_next_version(
            line,
            release_version=request.release_version,
            next_version=request.next_development_version,
        )
        values = request_env_values(request, line, "maintenance/0.1.x")
        self.assertEqual("0.1.x", values["RELEASE_SOURCE_LINE"])
        self.assertEqual("false", values["REQUEST_SKIP_TESTS"])
        self.assertEqual("true", values["REQUEST_DRY_RUN"])
        self.assertEqual("--latest=false", values["RELEASE_LATEST_ARGUMENT"])

    def test_reviewed_request_rejects_unknown_fields(self) -> None:
        request = valid_request()
        request["unexpected"] = "value"
        directory, path = self.write_json(request)
        self.addCleanup(directory.cleanup)
        with self.assertRaisesRegex(PolicyError, "unknown fields"):
            load_release_request(path)

    def test_reviewed_request_rejects_invalid_commit_and_request_id(self) -> None:
        request = valid_request()
        request["qualifiedCommit"] = "ABC"
        request["requestId"] = "Not Valid"
        directory, path = self.write_json(request)
        self.addCleanup(directory.cleanup)
        with self.assertRaises(PolicyError):
            load_release_request(path)

    def test_reviewed_request_cannot_cross_its_release_line(self) -> None:
        policy = self.load_valid_policy()
        request = valid_request()
        request["nextDevelopmentVersion"] = "0.2.0-SNAPSHOT"
        directory, path = self.write_json(request)
        self.addCleanup(directory.cleanup)
        parsed = load_release_request(path)
        line = resolve_line(
            policy,
            branch="maintenance/0.1.x",
            release_version=parsed.release_version,
            dry_run=parsed.dry_run,
        )
        with self.assertRaises(PolicyError):
            validate_next_version(
                line,
                release_version=parsed.release_version,
                next_version=parsed.next_development_version,
            )


if __name__ == "__main__":
    unittest.main()
