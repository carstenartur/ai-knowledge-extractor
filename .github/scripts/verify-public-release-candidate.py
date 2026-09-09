#!/usr/bin/env python3
"""Pin an existing supported GitHub release to its CI-qualified source parent."""
import json
import os
import subprocess
from pathlib import Path

from release_line_policy import VERSION_RE, load_policy, resolve_line

METADATA = {
    "gradle.properties", "release.properties", "CITATION.cff", ".zenodo.json",
    "maven/src/main/resources/META-INF/maven/plugin.xml", "site/pom.xml",
    "examples/maven-consumer/pom.xml", "examples/fixtures/maven-consumer/pom.xml",
}


def run(*args):
    return subprocess.check_output(args, text=True).strip()


def qualified_ci_runs(runs, parent):
    # Verify response fields as well as the request filter. Another workflow's
    # success or another commit's green CI must never qualify this source.
    return [item for item in runs if item.get("head_sha") == parent
            and item.get("path") == ".github/workflows/ci.yml"]


def validate_release_evidence(version, release, changed, runs):
    if not VERSION_RE.fullmatch(version):
        raise ValueError("version must be canonical X.Y.Z")
    if release.get("tag_name") != f"v{version}" or release.get("draft") is not False \
            or release.get("prerelease") is not False:
        raise ValueError("an existing non-draft, stable GitHub release is required")
    if "SHA256SUMS" not in {asset["name"] for asset in release.get("assets", [])}:
        raise ValueError("the GitHub release must contain SHA256SUMS")
    if not changed or set(changed) - METADATA:
        raise ValueError("release commit must change only version metadata after qualified source")
    if not runs or max(runs, key=lambda item: item["id"]).get("conclusion") != "success":
        raise ValueError("the latest CI run for the exact source parent must have succeeded")


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
    runs = json.loads(run("gh", "api", f"repos/{repo}/actions/runs?head_sha={parent}&per_page=100"))
    changed = run("git", "diff", "--name-only", parent, sha).splitlines()
    validate_release_evidence(version, release, changed,
                              qualified_ci_runs(runs["workflow_runs"], parent))
    properties = run("git", "show", f"{sha}:gradle.properties").splitlines()
    if f"projectVersion={version}" not in properties:
        raise ValueError("tag version metadata disagrees")
    subprocess.run(["git", "cat-file", "-e", f"{sha}:.github/scripts/verify-public-distribution.sh"], check=True)
    with open(os.environ["GITHUB_OUTPUT"], "a") as output:
        output.write(f"sha={sha}\n")
    print(json.dumps({"version": version, "sha": sha, "qualifiedSource": parent,
                      "releaseLine": line.branch, "publicAvailabilityVerified": False}, indent=2))


if __name__ == "__main__":
    main()
