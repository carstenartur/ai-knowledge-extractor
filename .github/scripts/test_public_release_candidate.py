#!/usr/bin/env python3
import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("candidate", Path(__file__).with_name("verify-public-release-candidate.py"))
candidate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(candidate)


class CandidateEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.release = {"tag_name": "v0.2.1", "draft": False, "prerelease": False,
                        "assets": [{"name": "SHA256SUMS"}]}
        self.changes = ["gradle.properties", "CITATION.cff"]

    def verify(self, version="0.2.1"):
        candidate.validate_release_metadata(version, self.release, self.changes)

    def test_accepts_qualified_release_metadata(self):
        self.verify()

    def test_rejects_code_changes_after_qualification(self):
        self.changes.append("build.gradle")
        with self.assertRaises(ValueError): self.verify()

    def test_rejects_unpublished_and_wrong_tag(self):
        for key, value in [("draft", True), ("prerelease", True), ("tag_name", "v0.2.0"), ("assets", [])]:
            with self.subTest(key=key):
                original = self.release[key]
                self.release[key] = value
                with self.assertRaises(ValueError): self.verify()
                self.release[key] = original

    def test_rejects_snapshot_and_shell_input(self):
        for version in ["0.2.1-SNAPSHOT", "01.2.1", "0.2.1; touch x", "0.2.1\n"]:
            with self.assertRaises(ValueError): self.verify(version)


class CandidateCiTest(unittest.TestCase):
    def setUp(self):
        self.parent = "a" * 40
        self.run = {"id": 5, "head_sha": self.parent, "path": ".github/workflows/ci.yml",
                    "status": "completed", "conclusion": "success"}
        self.elapsed = 0
        self.history = []

    def wait(self, *polls):
        def fetch(*args, timeout):
            self.assertEqual(args, ("gh", "api", f"repos/owner/repo/actions/workflows/ci.yml/runs?head_sha={self.parent}&per_page=100"))
            self.history.append(self.elapsed)
            return json.dumps({"workflow_runs": polls[min(len(self.history), len(polls)) - 1]})

        def sleep(seconds):
            self.elapsed += seconds

        with patch.object(candidate, "run", side_effect=fetch), \
                patch.object(candidate.time, "monotonic", side_effect=lambda: self.elapsed), \
                patch.object(candidate.time, "sleep", side_effect=sleep):
            candidate.wait_for_successful_ci("owner/repo", self.parent)

    def test_waits_for_discovery_and_pending_run(self):
        other_head = dict(self.run, head_sha="b" * 40)
        other_workflow = dict(self.run, path=".github/workflows/publish.yml")
        pending = dict(self.run, conclusion=None, status="in_progress")
        self.wait([other_head, other_workflow], [dict(self.run, id=4), pending], [self.run])
        self.assertEqual(len(self.history), 3)

    def test_latest_failed_ci_is_terminal_even_after_older_success(self):
        with self.assertRaisesRegex(ValueError, "concluded failure"):
            self.wait([self.run, dict(self.run, id=8, conclusion="failure")])
        self.assertEqual(self.elapsed, 0)

    def test_times_out_if_no_exact_run_appears(self):
        with self.assertRaisesRegex(ValueError, "timed out"):
            self.wait([])
        self.assertEqual(self.elapsed, candidate.CI_DISCOVERY_TIMEOUT)

    def test_times_out_if_newest_run_stays_pending(self):
        with self.assertRaisesRegex(ValueError, "timed out"):
            self.wait([self.run, dict(self.run, id=8, conclusion=None, status="queued")])
        self.assertEqual(self.elapsed, candidate.CI_COMPLETION_TIMEOUT)


if __name__ == "__main__":
    unittest.main()
