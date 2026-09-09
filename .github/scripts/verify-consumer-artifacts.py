#!/usr/bin/env python3
"""Mixed-language contract assertions shared by every binary consumer."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys


def verify(directory, core_jar):
    directory = Path(directory)
    def read(name):
        return json.loads((directory / name).read_text())
    for name in ("source-units.json", "symbols.json", "relations.json", "boundaries.json",
                 "warnings.json", "boundary-analysis.json", "boundary-analysis.html"):
        assert (directory / name).stat().st_size, name
    boundary = read("boundary-analysis.json")
    assert boundary == read("complexity.json")["boundaryAnalysis"]
    assert boundary == read("check.json")["boundaryAnalysis"]
    assert boundary["versionControlHistoryUsed"] is False
    assert boundary["changeCouplingIncluded"] is False
    assert any(x["status"] == "linked" and x["operation"] == "GET /api/items" for x in boundary["links"])
    assert any(x["status"] == "unresolved-dynamic" for x in boundary["links"])
    dependencies = read("dependencies.json")["dependencies"]
    assert any(x.get("ecosystem") == "npm" and x.get("scope") == "dependencies" and x.get("artifact") == "axios" for x in dependencies)
    assert any(x.get("scope") == "devDependencies" and x.get("artifact") == "typescript" for x in dependencies)
    assert "axios" in boundary["dependencySurface"]["boundaryRuntimeImportedPackages"]
    units = read("source-units.json")["sourceUnits"]
    assert any(x.get("language") == "typescript" for x in units)
    assert any(x.get("provider") == "example-text" and x.get("exampleAdditive") is True for x in units)
    assert any(x.get("kind") == "EXAMPLE_REFERENCES_TEXT" for x in read("relations.json")["relations"])
    def fingerprint():
        return {str(p.relative_to(directory)): (p.stat().st_mtime_ns, hashlib.sha256(p.read_bytes()).hexdigest())
                for p in directory.rglob("*") if p.is_file()}
    before = fingerprint()
    source = Path(__file__).resolve().parents[1] / "fixtures/VerifyRetainedArtifacts.java"
    subprocess.run(["java", "-cp", str(core_jar), str(source), str(directory)], check=True)
    assert fingerprint() == before, "Retained artifact verification modified output"
    print(f"Verified mixed-language, external-provider and retained artifacts: {directory}")

if __name__ == "__main__":
    verify(sys.argv[1], sys.argv[2])
