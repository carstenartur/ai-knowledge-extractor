#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
# shellcheck source=.github/scripts/release-source-policy.sh
source "$ROOT_DIR/.github/scripts/release-source-policy.sh"

fail() {
  echo "release source policy test failed: $*" >&2
  exit 1
}

assert_policy() {
  local source_branch=$1
  local dry_run=$2
  local expected_lane=$3
  local expected_base=$4
  local expected_latest=$5

  resolve_release_source_policy "$source_branch" "$dry_run"
  [[ "$RELEASE_SOURCE_LANE" == "$expected_lane" ]] \
    || fail "lane for ${source_branch}: ${RELEASE_SOURCE_LANE}"
  [[ "$RELEASE_NEXT_PR_BASE" == "$expected_base" ]] \
    || fail "next PR base for ${source_branch}: ${RELEASE_NEXT_PR_BASE}"
  [[ "$RELEASE_LATEST_ARGUMENT" == "$expected_latest" ]] \
    || fail "latest flag for ${source_branch}: ${RELEASE_LATEST_ARGUMENT}"
}

assert_policy \
  "main" \
  "false" \
  "main" \
  "main" \
  "--latest"

assert_policy \
  "maintenance/0.1.x" \
  "false" \
  "maintenance-0.1.x" \
  "maintenance/0.1.x" \
  "--latest=false"

assert_policy \
  "feature/local-dry-run" \
  "true" \
  "dry-run" \
  "feature/local-dry-run" \
  "--latest=false"

if resolve_release_source_policy "feature/not-authorized" "false"; then
  fail "an arbitrary branch was authorized for a real release"
fi

echo "release-source-policy=VERIFIED"
