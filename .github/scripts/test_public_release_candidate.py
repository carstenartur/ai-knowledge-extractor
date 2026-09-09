#!/usr/bin/env python3
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("candidate", Path(__file__).with_name("verify-public-release-candidate.py"))
candidate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(candidate)


class CandidateEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.release = {"tag_name": "v0.2.1", "draft": False, "prerelease": False,
                        "assets": [{"name": "SHA256SUMS"}]}
        self.changes = ["gradle.properties", "CITATION.cff"]
        self.runs = [{"id": 4, "conclusion": "success"}]

    def verify(self, version="0.2.1"):
        candidate.validate_release_evidence(version, self.release, self.changes, self.runs)

    def test_accepts_qualified_release_metadata(self):
        self.verify()

    def test_rejects_code_changes_after_qualification(self):
        self.changes.append("build.gradle")
        with self.assertRaises(ValueError): self.verify()

    def test_rejects_later_failed_or_pending_ci(self):
        for conclusion in ["failure", None]:
            self.runs = [{"id": 4, "conclusion": "success"}, {"id": 5, "conclusion": conclusion}]
            with self.assertRaises(ValueError): self.verify()

    def test_rejects_unqualified_candidate(self):
        self.runs = []
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


if __name__ == "__main__":
    unittest.main()
