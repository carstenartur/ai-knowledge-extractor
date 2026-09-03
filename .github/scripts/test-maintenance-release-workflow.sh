#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
WORKFLOW="$ROOT_DIR/.github/workflows/publish.yml"

fail() {
  echo "maintenance release workflow test failed: $*" >&2
  exit 1
}

require_text() {
  local expected=$1
  grep -F -- "$expected" "$WORKFLOW" >/dev/null \
    || fail "missing workflow contract: ${expected}"
}

require_text "branches: [ 'maintenance/0.1.x' ]"
require_text "- '.github/release-requests/0.1.x.json'"
require_text "cancel-in-progress: false"
require_text "resolve-maintenance-release-request.py"
require_text 'git merge-base --is-ancestor'
require_text 'git diff --name-only "$qualified_commit" "$GITHUB_SHA"'
require_text "Only the maintenance release request may differ from qualifiedCommit"
require_text 'RELEASE_VERSION: ${{ steps.request.outputs.release_version }}'
require_text 'NEXT_VERSION_INPUT: ${{ steps.request.outputs.next_version }}'
require_text 'SKIP_TESTS: ${{ steps.request.outputs.skip_tests }}'
require_text 'DRY_RUN: ${{ steps.request.outputs.dry_run }}'
require_text 'SOURCE_BRANCH: ${{ github.ref_name }}'

if grep -F -- 'cancel-in-progress: true' "$WORKFLOW" >/dev/null; then
  fail "release requests may not cancel an in-flight release"
fi

echo "maintenance-release-workflow=VERIFIED"
