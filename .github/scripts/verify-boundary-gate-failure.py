#!/usr/bin/env python3
"""Reject unrelated build failures: the configured boundary metric must cause the failure."""
import json
from pathlib import Path
import sys
root = Path(sys.argv[1])
check = json.loads((root / "check.json").read_text())
gate = check["boundaryQualityGate"]
assert check["passed"] is False and gate["passed"] is False
assert gate["thresholds"]["maxBoundaryDynamicCalls"] == 0
assert any(v["metric"] == "boundaryDynamicCalls" and v["measured"] == 1
           and v["calls"] and v["links"][0]["status"] == "unresolved-dynamic" for v in gate["violations"])
assert "boundaryDynamicCalls" in (root / "check.html").read_text()
print("Published consumer enforced dynamic-call limit with actionable evidence")
