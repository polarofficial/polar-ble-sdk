#!/bin/sh
set -e
V="${1:?usage: verify_release.sh <version>}"
REPO=polarofficial/polar-ble-sdk
TMP=$(mktemp -d); cd "$TMP"

gh release download "$V" --repo "$REPO"

gh attestation verify release-commit.txt --repo "$REPO"

found=0
for f in *.aar *-source.zip; do
  [ -e "$f" ] || continue
  found=$((found + 1))
  gh attestation verify "$f" --repo "$REPO"
  gh attestation verify "$f" --repo "$REPO" --predicate-type https://cyclonedx.org/bom
done
# -java17 releases are Android-only
case "$V" in
  *-java17) expected=1 ;;
  *) expected=2 ;;
esac
[ "$found" -eq "$expected" ] || { echo "Expected $expected artifact(s) (AAR, plus source zip for non-java17), found $found"; exit 1; }

cosign verify-blob SHA256SUMS.txt \
  --signature SHA256SUMS.txt.sig \
  --certificate SHA256SUMS.txt.pem \
  --certificate-identity-regexp "https://github.com/$REPO/.github/workflows/release-finalize.yml@.*" \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com

shasum -a 256 --check SHA256SUMS.txt

echo "Release $V verified (provenance + SBOM attestations + checksum bundle) for $([ "$expected" -eq 2 ] && echo "Android and iOS" || echo Android)."
