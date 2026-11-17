#!/usr/bin/env python3
"""Pin an existing supported GitHub release to its CI-qualified source parent."""
import json
import os
import subprocess
import sys
import time
from pathlib import Path

from release_line_policy import VERSION_RE, load_policy, resolve_line

METADATA = {
    "gradle.properties", "release.properties", "CITATION.cff", ".zenodo.json",
    "maven/src/main/resources/META-INF/maven/plugin.xml", "site/pom.xml",
    "examples/maven-consumer/pom.xml", "examples/fixtures/maven-consumer/pom.xml",
}


CI_DISCOVERY_TIMEOUT = 5 * 60
CI_COMPLETION_TIMEOUT = 90 * 60
CI_POLL_INTERVAL = 30


def run(*args, timeout=None):
    return subprocess.check_output(args, text=True, timeout=timeout).strip()


def qualified_ci_runs(runs, parent):
    # Verify response fields as well as the request filter. Another workflow's
    # success or another commit's green CI must never qualify this source.
    return [item for item in runs if item.get("head_sha") == parent
            and item.get("path") == ".github/workflows/ci.yml"]


def validate_release_metadata(version, release, changed):
    if not VERSION_RE.fullmatch(version):
        raise ValueError("version must be canonical X.Y.Z")
    if release.get("tag_name") != f"v{version}" or release.get("draft") is not False \
            or release.get("prerelease") is not False:
        raise ValueError("an existing non-draft, stable GitHub release is required")
    if "SHA256SUMS" not in {asset["name"] for asset in release.get("assets", [])}:
        raise ValueError("the GitHub release must contain SHA256SUMS")
    if not changed or set(changed) - METADATA:
        raise ValueError("release commit must change only version metadata after qualified source")


def wait_for_successful_ci(repo, parent):
    start = time.monotonic()
    deadline = start + CI_COMPLETION_TIMEOUT
    endpoint = f"repos/{repo}/actions/workflows/ci.yml/runs?head_sha={parent}&per_page=100"
    previous_status = None
    while True:
        result = json.loads(run("gh", "api", endpoint, timeout=60))
        runs = qualified_ci_runs(result["workflow_runs"], parent)
        latest = max(runs, key=lambda item: item["id"], default=None)
        if latest and (latest.get("conclusion") or latest.get("status") == "completed"):
            if latest.get("conclusion") != "success":
                raise ValueError(f"CI run {latest['id']} for {parent} concluded {latest.get('conclusion')}")
            return
        if latest:
            status = f"CI run {latest['id']} for {parent} is {latest.get('status', 'pending')}"
            remaining = deadline - time.monotonic()
        else:
            status = f"Waiting for CI on {parent} to appear"
            remaining = start + CI_DISCOVERY_TIMEOUT - time.monotonic()
        if remaining <= 0:
            raise ValueError(f"CI qualification timed out: {status}")
        if status != previous_status:
            print(status, file=sys.stderr, flush=True)
            previous_status = status
        time.sleep(min(CI_POLL_INTERVAL, remaining))


def main():
    version = os.environ["VERSION"]
    if not VERSION_RE.fullmatch(version):
        raise ValueError("version must be canonical X.Y.Z")
    policy = load_policy(Path(".github/release-lines.json"))
    series = version.rsplit(".", 1)[0]
    line = next((item for item in policy.lines if item.series == series), None)
    if line is None:
        raise ValueError("unknown release line")
    resolve_line(policy, branch=line.branch, release_version=version, dry_run=False)
    if line.artifact_contract != "schema-v2":
        raise ValueError("public publishing must first be backported and qualified for this artifact contract")
    sha = run("git", "rev-parse", f"refs/tags/v{version}^{{commit}}")
    if sha != run("git", "rev-parse", f"refs/remotes/origin/release/v{version}^{{commit}}"):
        raise ValueError("tag and release branch disagree")
    parents = run("git", "show", "-s", "--format=%P", sha).split()
    if len(parents) != 1:
        raise ValueError("expected one release source parent")
    parent = parents[0]
    subprocess.run(["git", "merge-base", "--is-ancestor", parent,
                    f"refs/remotes/origin/{line.branch}"], check=True)
    repo = os.environ["GITHUB_REPOSITORY"]
    release = json.loads(run("gh", "api", f"repos/{repo}/releases/tags/v{version}"))
    changed = run("git", "diff", "--name-only", parent, sha).splitlines()
    validate_release_metadata(version, release, changed)
    properties = run("git", "show", f"{sha}:gradle.properties").splitlines()
    if f"projectVersion={version}" not in properties:
        raise ValueError("tag version metadata disagrees")
    subprocess.run(["git", "cat-file", "-e", f"{sha}:.github/scripts/verify-public-distribution.sh"], check=True)
    wait_for_successful_ci(repo, parent)
    with open(os.environ["GITHUB_OUTPUT"], "a") as output:
        output.write(f"sha={sha}\n")
    print(json.dumps({"version": version, "sha": sha, "qualifiedSource": parent,
                      "releaseLine": line.branch, "publicAvailabilityVerified": False}, indent=2))


if __name__ == "__main__":
    main()
