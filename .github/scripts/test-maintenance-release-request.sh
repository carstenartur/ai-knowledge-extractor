#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
PARSER="$ROOT_DIR/.github/scripts/resolve-maintenance-release-request.py"
TEMP_DIR=$(mktemp -d)
trap 'rm -rf "$TEMP_DIR"' EXIT

fail() {
  echo "maintenance release request test failed: $*" >&2
  exit 1
}

write_valid_request() {
  cat > "$1" <<'JSON'
{
  "schema": "ai-knowledge-maintenance-release-request/v1",
  "releaseVersion": "0.1.9",
  "nextDevelopmentVersion": "0.1.10-SNAPSHOT",
  "skipTests": false,
  "dryRun": true,
  "requestId": "0.1.9-inventory-pruning-dry-run",
  "qualifiedCommit": "0123456789abcdef0123456789abcdef01234567"
}
JSON
}

REQUEST="$TEMP_DIR/request.json"
OUTPUT="$TEMP_DIR/output.txt"
write_valid_request "$REQUEST"
python3 "$PARSER" --request "$REQUEST" --output "$OUTPUT"
grep -Fx 'release_version=0.1.9' "$OUTPUT" >/dev/null
grep -Fx 'next_version=0.1.10-SNAPSHOT' "$OUTPUT" >/dev/null
grep -Fx 'skip_tests=false' "$OUTPUT" >/dev/null
grep -Fx 'dry_run=true' "$OUTPUT" >/dev/null
grep -Fx 'request_id=0.1.9-inventory-pruning-dry-run' "$OUTPUT" >/dev/null
grep -Fx 'qualified_commit=0123456789abcdef0123456789abcdef01234567' "$OUTPUT" >/dev/null

cat > "$TEMP_DIR/extra-field.json" <<'JSON'
{
  "schema": "ai-knowledge-maintenance-release-request/v1",
  "releaseVersion": "0.1.9",
  "nextDevelopmentVersion": "0.1.10-SNAPSHOT",
  "skipTests": false,
  "dryRun": true,
  "requestId": "0.1.9-inventory-pruning-dry-run",
  "qualifiedCommit": "0123456789abcdef0123456789abcdef01234567",
  "unexpected": true
}
JSON
if python3 "$PARSER" \
    --request "$TEMP_DIR/extra-field.json" \
    --output "$TEMP_DIR/extra-output.txt"; then
  fail "accepted an unexpected request field"
fi

cat > "$TEMP_DIR/duplicate-field.json" <<'JSON'
{
  "schema": "ai-knowledge-maintenance-release-request/v1",
  "releaseVersion": "0.1.9",
  "releaseVersion": "0.1.9",
  "nextDevelopmentVersion": "0.1.10-SNAPSHOT",
  "skipTests": false,
  "dryRun": true,
  "requestId": "0.1.9-inventory-pruning-dry-run",
  "qualifiedCommit": "0123456789abcdef0123456789abcdef01234567"
}
JSON
if python3 "$PARSER" \
    --request "$TEMP_DIR/duplicate-field.json" \
    --output "$TEMP_DIR/duplicate-output.txt"; then
  fail "accepted a duplicate request field"
fi

python3 - "$REQUEST" "$TEMP_DIR/skip-tests.json" <<'PY'
import json
import sys
from pathlib import Path

source = Path(sys.argv[1])
target = Path(sys.argv[2])
data = json.loads(source.read_text(encoding="utf-8"))
data["skipTests"] = True
target.write_text(json.dumps(data) + "\n", encoding="utf-8")
PY
if python3 "$PARSER" \
    --request "$TEMP_DIR/skip-tests.json" \
    --output "$TEMP_DIR/skip-output.txt"; then
  fail "accepted skipTests=true"
fi

python3 - "$REQUEST" "$TEMP_DIR/wrong-next.json" <<'PY'
import json
import sys
from pathlib import Path

source = Path(sys.argv[1])
target = Path(sys.argv[2])
data = json.loads(source.read_text(encoding="utf-8"))
data["nextDevelopmentVersion"] = "0.1.11-SNAPSHOT"
target.write_text(json.dumps(data) + "\n", encoding="utf-8")
PY
if python3 "$PARSER" \
    --request "$TEMP_DIR/wrong-next.json" \
    --output "$TEMP_DIR/next-output.txt"; then
  fail "accepted a non-consecutive next patch"
fi

python3 - "$REQUEST" "$TEMP_DIR/wrong-lane.json" <<'PY'
import json
import sys
from pathlib import Path

source = Path(sys.argv[1])
target = Path(sys.argv[2])
data = json.loads(source.read_text(encoding="utf-8"))
data["releaseVersion"] = "0.2.1"
target.write_text(json.dumps(data) + "\n", encoding="utf-8")
PY
if python3 "$PARSER" \
    --request "$TEMP_DIR/wrong-lane.json" \
    --output "$TEMP_DIR/lane-output.txt"; then
  fail "accepted a non-0.1.x release"
fi

echo "maintenance-release-request=VERIFIED"
