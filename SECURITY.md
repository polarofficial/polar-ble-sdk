# Security

## Release integrity

Every tagged release is built in GitHub Actions (`.github/workflows/release.yml`) and ships:

- `polar-ble-sdk-<v>.aar` (Android) and `polar-ble-sdk-<v>-source.zip` (iOS / SPM source)
- CycloneDX SBOMs: `-android-sbom.cdx.json`, `-ios-sbom.cdx.json`
- `release-commit.txt` (attested SHA of the tagged commit)
- `SHA256SUMS.txt` with a Sigstore keyless signature (`.sig`, `.pem`)
- GitHub artifact attestations (provenance + SBOM)

Tags with a `-java17` suffix (Java 17 branch) are Android-only and have no iOS source archive or iOS SBOM.

The release pipeline holds no long-lived signing keys or secrets.
`gh attestation verify` is the sole source of truth for authenticity.

> JitPack rebuilds from source and SwiftPM resolves source from the git tag, so neither can carry
> or enforce attestations. SwiftPM only enforces checksums for binary targets. Verify the GitHub
> Release artifacts in your own CI as shown below.

## Verifying a release

Requires `gh` >= 2.49 (and optionally `cosign`).

### 1. Download the release artifacts
```bash
gh release download 8.2.0 --repo polarofficial/polar-ble-sdk
```

### 2. Verify provenance and SBOM attestations
Android:
```bash
gh attestation verify polar-ble-sdk-8.2.0.aar --repo polarofficial/polar-ble-sdk
gh attestation verify polar-ble-sdk-8.2.0.aar --repo polarofficial/polar-ble-sdk \
  --predicate-type https://cyclonedx.org/bom
```
iOS (SPM source):
```bash
gh attestation verify polar-ble-sdk-8.2.0-source.zip --repo polarofficial/polar-ble-sdk
gh attestation verify polar-ble-sdk-8.2.0-source.zip --repo polarofficial/polar-ble-sdk \
  --predicate-type https://cyclonedx.org/bom
```
This confirms the artifact was built by `release-android.yml` / `release-ios.yml` in
`polarofficial/polar-ble-sdk` from the tagged commit, and that the SBOM describes that exact artifact.

### 3. Verify the checksum bundle (keyless / Sigstore)
```bash
cosign verify-blob SHA256SUMS.txt \
  --signature SHA256SUMS.txt.sig \
  --certificate SHA256SUMS.txt.pem \
  --certificate-identity-regexp "https://github.com/polarofficial/polar-ble-sdk/.github/workflows/release-finalize.yml@.*" \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com

sha256sum --check SHA256SUMS.txt
```

### 4. Confirm the tagged commit (iOS / SPM)
```bash
gh attestation verify release-commit.txt --repo polarofficial/polar-ble-sdk
swift package resolve
git -C .build/checkouts/polar-ble-sdk rev-parse HEAD   # must equal the contents of release-commit.txt
```

### 5. Inspect the SBOM
```bash
jq '.components[] | {name, version, licenses}' polar-ble-sdk-8.2.0-android-sbom.cdx.json
grype sbom:polar-ble-sdk-8.2.0-ios-sbom.cdx.json
```

### 6. Enforce verification in your build

Android — pin the hash with Gradle dependency verification:
```bash
./gradlew --write-verification-metadata sha256
```

Both platforms — CI policy gate:
```bash
gh attestation verify polar-ble-sdk-*.aar --repo polarofficial/polar-ble-sdk \
  || { echo "Attestation verification failed — refusing to build"; exit 1; }
```

The whole release can also be checked with `scripts/verify_release.sh <version>`.
