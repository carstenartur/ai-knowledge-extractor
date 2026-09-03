#!/usr/bin/env bash

resolve_release_source_policy() {
  local source_branch=${1-}
  local dry_run=${2-false}

  RELEASE_SOURCE_LANE=""
  RELEASE_NEXT_PR_BASE=""
  RELEASE_LATEST_ARGUMENT=""

  case "$source_branch" in
    main)
      RELEASE_SOURCE_LANE="main"
      RELEASE_NEXT_PR_BASE="main"
      RELEASE_LATEST_ARGUMENT="--latest"
      ;;
    maintenance/0.1.x)
      RELEASE_SOURCE_LANE="maintenance-0.1.x"
      RELEASE_NEXT_PR_BASE="maintenance/0.1.x"
      RELEASE_LATEST_ARGUMENT="--latest=false"
      ;;
    *)
      if [[ "$dry_run" != "true" ]]; then
        echo "::error::Real releases must be dispatched from main or maintenance/0.1.x, not ${source_branch}" >&2
        return 1
      fi
      RELEASE_SOURCE_LANE="dry-run"
      RELEASE_NEXT_PR_BASE="$source_branch"
      RELEASE_LATEST_ARGUMENT="--latest=false"
      ;;
  esac

  export RELEASE_SOURCE_LANE
  export RELEASE_NEXT_PR_BASE
  export RELEASE_LATEST_ARGUMENT
}
