#!/usr/bin/env bash
# Fully executable rehearsal; never uploads to a public repository.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
cd "$ROOT"
VERSION=${VERSION:-$(sed -n 's/^projectVersion=//p' gradle.properties | sed 's/-SNAPSHOT$//')}
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || exit 1
GRADLE=${GRADLE:-gradle}
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
export GNUPGHOME="$WORK/gpg"
mkdir -m 700 "$GNUPGHOME"
# Ephemeral CI-only signing key. The private key is never printed or retained.
gpg --batch --pinentry-mode loopback --passphrase '' \
  --quick-generate-key 'Publication rehearsal <ci@example.invalid>' rsa2048 sign 1d \
  > "$WORK/key-generation.log" 2>&1
export ORG_GRADLE_PROJECT_signingInMemoryKey
ORG_GRADLE_PROJECT_signingInMemoryKey=$(gpg --batch --armor --export-secret-keys)
export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=''
mkdir -p build/reports/public-distribution
rm -rf build/public-validation-repository
"$GRADLE" --no-daemon -PpublicDistribution=true -PreleaseVersion="$VERSION" \
  publishAllPublicationsToPublicValidationRepository \
  :gradle-plugin:publishPlugins --validate-only \
  2>&1 | tee build/reports/public-distribution/rehearsal.log
python3 .github/scripts/verify-publication-metadata.py \
  build/public-validation-repository "$VERSION" \
  > build/reports/public-distribution/signed-manifest.json
unset ORG_GRADLE_PROJECT_signingInMemoryKey ORG_GRADLE_PROJECT_signingInMemoryKeyPassword
VERSION="$VERSION" bash .github/scripts/verify-public-consumers.sh \
  --repository "$ROOT/build/public-validation-repository"
