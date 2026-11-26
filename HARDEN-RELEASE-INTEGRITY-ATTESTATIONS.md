# Hardening Polar BLE SDK Release Integrity (Android + iOS) — Attestation-Based

Step-by-step, automation-first instructions for giving Android (JitPack / Gradle) and iOS (Swift
Package Manager) consumers a cryptographically verifiable SDK release on **every** tag, using
**GitHub artifact attestations** (keyless, OIDC-based, Sigstore-backed) instead of long-lived GPG
key management, and generating a **Software Bill of Materials (SBOM)** per platform.

> This document merges `HARDEN-RELEASE-INTEGRITY-ANDROID-ATTESTATIONS.md` and
> `ios-communications/HARDEN-RELEASE-INTEGRITY-IOS-ATTESTATIONS.md`. Both targeted the same public
> monorepo (`polar-ble-sdk`), the same tag and the same workflow/file names, so they could not be
> applied independently. This is the single source of truth; the two platform documents are
> superseded.
>
> CocoaPods support is being removed and is **out of scope** (no podspec, no `pod trunk push`).
> SPM resolves the package as **source** from the git tag, so there is no XCFramework,
> `.binaryTarget` or SPM `checksum:`.

## Design decisions (what changed versus the two source documents)

| # | Issue in the separate documents | Resolution here |
|---|---|---|
| 1 | Both created `release.yml`, `attest-tag.yml`, `release-finalize.yml`, `SECURITY.md`, `verify_release.sh` with different contents | One of each, covering both platforms |
| 2 | Same tag triggered two release runs → two GitHub Releases, split `SHA256SUMS.txt` | One `release.yml`: `attest-tag` → `android` ∥ `ios` → `finalize` (single Release, single signed checksum file) |
| 3 | Both produced `polar-ble-sdk-<v>-sbom.cdx.json` (collision on merge) | `…-android-sbom.cdx.json` and `…-ios-sbom.cdx.json` |
| 4 | Finalize re-attested subjects already attested by the platform jobs; its `attest-sbom` was hardcoded per platform | Platform jobs own provenance + SBOM attestations; finalize only checksums, signs and releases |
| 5 | Tag ruleset patterns differed (`v*.*.*` vs `[0-9]+…`) and the Android one did not match real (unprefixed) tags | One ruleset with a pattern matching unprefixed tags |
| 6 | Two orchestrators each told the operator to push master and tag the same monorepo | One shared instruction block, executed once after **both** repos are mirrored |
| 7 | `attest-tag.yml` lacked `workflow_call`, also fired standalone, and its attested `release-commit.txt` was never published | Callable-only; the file is uploaded and shipped in the release |
| 8 | Android published to Maven Central (irreversible) before attesting / before iOS finished; Android doc contradicted itself on whether Central publishing exists | Central publish is an **optional, last, disabled-by-default** job (Phase 4); default pipeline holds **zero secrets** |
| 9 | iOS source zip is `git archive` of the whole monorepo tag (includes Android sources) | Kept intentionally as a platform-neutral source artifact; named `-source.zip` |

> Platform releases are **version-coupled**: one tag triggers both pipelines. If independent
> cadences are ever needed, introduce platform-specific tag prefixes (`android-8.2.0`,
> `ios-8.2.0`) and split `release.yml` triggers accordingly.

This document is written against the release tooling that exists today:

| Existing asset | Location |
|---|---|
| Release orchestrator (duplicated across repos) | `android-communications/scripts/orchestrate_releases.sh`, `ios-communications/scripts/orchestrate_releases.sh` |
| Per-repo release prep | `*/scripts/prepare_sdk_release.sh` |
| Android AAR build | `android-communications/scripts/build_sdk.sh` |
| Artifactory publish | `android-communications/scripts/deploy_sdk_to_artifactory.sh` |
| Public monorepo manifests | `polar-ble-sdk/jitpack.yml`, `polar-ble-sdk/Package.swift` |

**Current gap:** every channel resolves from the *git tag*. Nothing is signed, nothing is hashed,
nothing documents what's inside. The whole trust chain today is "GitHub returned the right tag".

---

## Why attestations instead of GPG

| | GPG-based (classic) | GitHub artifact attestations (this document) |
|---|---|---|
| Key management | Generate, store as CI secret, rotate, revoke on leak | None — identity is the GitHub Actions OIDC token of that workflow run |
| Trust root | "Whoever holds the private key" | "This exact workflow, in this exact repo, at this exact commit, produced this artifact" |
| Revocation | Manual revocation cert | N/A — a compromised run cannot forge history in the transparency log |
| Verification | `gpg --verify` + public key distribution problem | `gh attestation verify` (no keys) or `cosign verify-blob` against Sigstore/Rekor |
| Transparency | None by default | Public, append-only log (Rekor) |
| Offline signing | Possible | Not supported (needs Fulcio/Rekor) |

---

## Target end state

| Channel | Integrity mechanism | Enforced by |
|---|---|---|
| Android / JitPack & Gradle | Provenance + SBOM attestation on the release AAR; SHA-256 in signed `SHA256SUMS.txt` | `gh attestation verify`; consumer `verification-metadata.xml` (hash only — JitPack rebuilds from source, so it cannot carry the attestation) |
| iOS / SPM (source) | Provenance + SBOM attestation on the tagged source archive | `gh attestation verify` in consumer CI (SPM has no enforcement hook for source dependencies) |
| All | Tag-protected releases, Sigstore keyless-signed `SHA256SUMS.txt`, attested release commit | `cosign verify-blob`, `gh attestation verify` |

> **SPM caveat:** SPM only hard-enforces `checksum:` for `.binaryTarget`. For a source `.target`
> resolved via git tag, nothing is checked at resolve time. The attestations are a verifiable
> transparency layer a consumer checks in their own CI — state this in `SECURITY.md`.

> **Maven Central caveat (optional channel):** Sonatype Central Portal mandates a PGP `.asc`
> signature on direct uploads and does not accept keyless attestations. Today `com.polar`
> coordinates resolve *from JitPack*; there is no direct Central upload. Phase 4 therefore ships
> Central publishing as an optional, off-by-default job. Until it is enabled, the pipeline has no
> secrets at all.

Roll out in the phase order below. Each phase is independently shippable.

---

## Phase 0 — One-time repository setup (no keys to generate)

### 0.1 Enable attestations

No feature flag is needed; attestations are on by default for GitHub-hosted repos. Do **not**
grant global "Read and write permissions"; each workflow requests only what it needs
(`id-token: write`, `attestations: write`).

### 0.2 Protect the tag namespace (one ruleset for both platforms)

GitHub → `polar-ble-sdk` → Settings → Rules → Rulesets → New tag ruleset:
- Target (fnmatch): `[0-9]*.[0-9]*.[0-9]*` (matches unprefixed tags such as `8.2.0`)
- Restrict creation/deletion/update to the release-automation user/App and repo admins
- Require the tag to point at a commit on `master`/`main`

### 0.3 Verification tooling (release engineers, optional)

```bash
gh --version          # attestation verification requires gh >= 2.49
brew install cosign   # optional second check for keyless verify-blob
```

---

## Phase 1 — Tag hygiene (replaces signed git tags)

Attestations bind to *artifacts produced by a workflow run*, not to the tag object, so signed tags
are replaced with **tag protection + provenance-on-build**.

### 1.1 Patch both prep scripts

In `android-communications/scripts/prepare_sdk_release.sh` **and**
`ios-communications/scripts/prepare_sdk_release.sh`, inside `setSDKVersion()`:

```diff
-      ERROR=$({ git tag -m "Release $SDK_VERSION" $SDK_VERSION && git push origin $SDK_VERSION && git checkout $SDK_VERSION; } 2>&1 1>/dev/null)
+      ERROR=$({ git tag -a -m "Release $SDK_VERSION" $SDK_VERSION && git push origin $SDK_VERSION && git checkout $SDK_VERSION; } 2>&1 1>/dev/null)
       if [ $? -ne 0 ]; then
         die "Error occurred while pushing SDK version tag, $SDK_VERSION, to git. Error: $ERROR"
       fi
+      git tag -v "$SDK_VERSION" 2>/dev/null || true   # annotated, not signed — informational only
```

In `ios-communications/scripts/prepare_sdk_release.sh`, also remove the `pod lib lint` /
CocoaPods-cache steps once CocoaPods support is dropped (separate cleanup).

### 1.2 Patch the orchestrators

Replace the per-platform "tag polar-ble-sdk" step in both `orchestrate_releases.sh` files with the
shared block in Phase 7.1, and drop the iOS `pod trunk push` instruction.

### 1.3 CI provenance for the release commit

`polar-ble-sdk/.github/workflows/attest-tag.yml` — callable only (not triggered on its own) so it
runs exactly once per release. The attested file is uploaded so it ships with the release.

```yaml
name: Attest release commit
on: { workflow_call: {} }

jobs:
  attest-commit:
    runs-on: ubuntu-latest
    permissions: { contents: read, id-token: write, attestations: write }
    steps:
      - uses: actions/checkout@v4
      - name: Record commit metadata as an attestable artifact
        run: |
          mkdir -p dist
          printf '%s' "${GITHUB_SHA}" > dist/release-commit.txt
      - uses: actions/attest-build-provenance@v1
        with: { subject-path: dist/release-commit.txt }
      - uses: actions/upload-artifact@v4
        with: { name: commit-dist, path: dist/ }
```

---

## Phase 2 — A single tag-triggered release workflow

Everything hangs off one workflow so artifacts are always built in CI from the tagged commit —
never from a developer laptop. Android and iOS run in parallel; the GitHub Release is created once,
only after both succeed.

`polar-ble-sdk/.github/workflows/release.yml`:

```yaml
name: Release
on:
  push:
    tags: ['[0-9]*.[0-9]*.[0-9]*']

permissions:
  contents: write        # create the GitHub Release
  id-token: write        # OIDC for keyless attestations
  attestations: write

jobs:
  attest-tag:
    uses: ./.github/workflows/attest-tag.yml

  android:
    needs: attest-tag
    uses: ./.github/workflows/release-android.yml
    secrets: inherit

  ios:
    needs: attest-tag
    uses: ./.github/workflows/release-ios.yml
    secrets: inherit

  finalize:
    needs: [android, ios]
    uses: ./.github/workflows/release-finalize.yml
    secrets: inherit

  # Optional, irreversible, runs last — see Phase 4. Remove if not publishing to Central.
  publish-central:
    needs: finalize
    if: ${{ vars.PUBLISH_TO_CENTRAL == 'true' }}
    uses: ./.github/workflows/publish-central.yml
    secrets: inherit
```

> The orchestrators keep their role: version bumping, source mirroring into `polar-ble-sdk`, doc
> generation and pushing the tag. **Artifact building, SBOM generation and publishing move into
> CI.**

---

## Phase 3 — Platform builds, SBOMs and attestations

Each platform job attests its own subject (provenance + SBOM) and uploads its `dist/`. Artifact
names are platform-specific so merging in Phase 5 cannot collide.

### 3A — Android

JitPack rebuilds from source and cannot carry attestations, so it is a convenience channel; the
GitHub Release is the verifiable one.

#### 3A.1 Generate an SBOM for the AAR

In `android-communications/library/build.gradle`:

```groovy
plugins {
    id 'maven-publish'
    id 'org.cyclonedx.bom' version '1.10.0'
}

cyclonedxBom {
    includeConfigs = ["releaseRuntimeClasspath"]
    projectType = "library"
    schemaVersion = "1.5"
    destination = file("$buildDir/reports/sbom")
    outputName = "polar-ble-sdk-sbom"
    outputFormat = "json"
    includeBomSerialNumber = true
}
```

`./gradlew cyclonedxBom` produces `build/reports/sbom/polar-ble-sdk-sbom.json` (CycloneDX 1.5 with
PURLs and licenses). The `publishing { … }` block needed only for Maven Central is in Phase 4.

#### 3A.2 Android release workflow

`polar-ble-sdk/.github/workflows/release-android.yml`:

```yaml
name: Release Android
on: { workflow_call: {} }
jobs:
  build-and-attest:
    runs-on: ubuntu-latest
    permissions: { contents: read, id-token: write, attestations: write }
    defaults: { run: { working-directory: sources/Android/android-communications } }
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }

      - name: Run SDK unit tests
        run: ./gradlew :library:testSdkDebugUnitTest

      - name: Build release AAR
        run: ./gradlew :library:assembleSdkRelease -PsdkVersion=${{ github.ref_name }}

      - name: Generate SBOM
        run: ./gradlew :library:cyclonedxBom -PsdkVersion=${{ github.ref_name }}

      - name: Stage AAR + SBOM
        run: |
          mkdir -p "$GITHUB_WORKSPACE/dist"
          cp library/build/outputs/aar/library-sdk-release.aar \
             "$GITHUB_WORKSPACE/dist/polar-ble-sdk-${{ github.ref_name }}.aar"
          cp library/build/reports/sbom/polar-ble-sdk-sbom.json \
             "$GITHUB_WORKSPACE/dist/polar-ble-sdk-${{ github.ref_name }}-android-sbom.cdx.json"

      - name: Attest build provenance for the AAR
        uses: actions/attest-build-provenance@v1
        with: { subject-path: 'dist/*.aar' }

      - name: Attest the SBOM against the AAR
        uses: actions/attest-sbom@v1
        with:
          subject-path: 'dist/*.aar'
          sbom-path: 'dist/*-android-sbom.cdx.json'

      - uses: actions/upload-artifact@v4
        with: { name: android-dist, path: dist/ }
```

> `working-directory` applies to `run:` steps only; `subject-path`/`sbom-path` are resolved from
> the workspace root, which is why files are staged into `$GITHUB_WORKSPACE/dist`.

#### 3A.3 Local scripts

`build_sdk.sh` stays the *local* AAR producer for PSDC (attestations can only be minted by a
GitHub Actions OIDC token). `deploy_sdk_to_artifactory.sh` publishes to the internal repo; document
Artifactory as an internal channel without attestations and recommend verifiable GitHub Release
artifacts for internal consumers who need provenance.

### 3B — iOS

CI produces and attests a **source archive** of the tagged commit plus an SBOM of the Swift package
dependencies (`SwiftProtobuf`, `Zip`).

`polar-ble-sdk/.github/workflows/release-ios.yml`:

```yaml
name: Release iOS
on: { workflow_call: {} }
jobs:
  archive-and-attest:
    runs-on: macos-14
    permissions: { contents: read, id-token: write, attestations: write }
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 0 }

      - name: Run SDK tests
        working-directory: sources/iOS/ios-communications
        run: |
          SIM=$(xcrun simctl list devices available | grep -m1 -oE '[0-9A-F-]{36}')
          xcodebuild -workspace iOSCommunications.xcworkspace -scheme iOSCommunications \
            -destination "platform=iOS Simulator,id=$SIM" test

      - name: Archive the tagged source tree
        run: |
          V="${{ github.ref_name }}"
          mkdir -p dist
          git archive --format=zip --prefix="polar-ble-sdk-$V/" "$V" -o "dist/polar-ble-sdk-$V-source.zip"

      - name: Generate SBOM
        run: |
          V="${{ github.ref_name }}"
          brew install cyclonedx/cyclonedx/cyclonedx-swift 2>/dev/null || true
          cyclonedx-swift make \
            --output-format json \
            --output-file "dist/polar-ble-sdk-$V-ios-sbom.cdx.json" \
            --path Package.swift

      - name: Attest build provenance for the source archive
        uses: actions/attest-build-provenance@v1
        with: { subject-path: 'dist/*-source.zip' }

      - name: Attest the SBOM against the source archive
        uses: actions/attest-sbom@v1
        with:
          subject-path: 'dist/*-source.zip'
          sbom-path: 'dist/*-ios-sbom.cdx.json'

      - uses: actions/upload-artifact@v4
        with: { name: ios-dist, path: dist/ }
```

`ios-communications/scripts/prepare_sdk_release.sh` stays the *local* pre-flight check
(`swift package resolve`, build/test) before tagging; it does not produce or sign the archive.

---

## Phase 4 — Optional: direct Maven Central publishing (disabled by default)

**Skip this phase while Central coordinates are served via JitPack.** Enable it by setting the
repo variable `PUBLISH_TO_CENTRAL=true`. It runs *after* `finalize`, so an irreversible Central
upload never happens for a release whose Android/iOS build, attestation or GitHub Release failed.

This is the **only** place any secret or key exists in the whole pipeline. Sonatype demands a PGP
signature; scope it to a CI-only key used **exclusively** for that precondition, never as proof of
authenticity to consumers (`gh attestation verify` is).

### 4.1 Publishing config

In `android-communications/library/build.gradle`:

```groovy
plugins { id 'signing' }

android {
    publishing {
        singleVariant('sdkRelease') {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

afterEvaluate {
    publishing {
        publications {
            release(MavenPublication) {
                from components.sdkRelease
                groupId    = 'com.polar'
                artifactId = 'polar-ble-sdk'
                version    = project.findProperty('sdkVersion') ?: '0.0.0-SNAPSHOT'

                pom {
                    name = 'Polar BLE SDK'
                    description = 'SDK for Polar sensors'
                    url = 'https://github.com/polarofficial/polar-ble-sdk'
                    licenses { license { name = 'Polar SDK License'; url = 'https://github.com/polarofficial/polar-ble-sdk/blob/master/Polar_SDK_License.txt' } }
                    developers { developer { name = 'Polar Electro Oy' } }
                    scm {
                        url = 'https://github.com/polarofficial/polar-ble-sdk'
                        connection = 'scm:git:https://github.com/polarofficial/polar-ble-sdk.git'
                    }
                }
            }
        }
        repositories {
            maven {
                name = 'central'
                url  = 'https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/'
                credentials {
                    username = System.getenv('MAVEN_CENTRAL_USERNAME')
                    password = System.getenv('MAVEN_CENTRAL_PASSWORD')
                }
            }
        }
    }
    signing {
        useGpgCmd()
        sign publishing.publications.release
    }
}
```

### 4.2 Workflow

`polar-ble-sdk/.github/workflows/publish-central.yml`:

```yaml
name: Publish to Maven Central
on: { workflow_call: {} }
jobs:
  publish:
    runs-on: ubuntu-latest
    permissions: { contents: read }
    defaults: { run: { working-directory: sources/Android/android-communications } }
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }

      - name: Import Central-compliance key (Sonatype policy only)
        env:
          KEY: ${{ secrets.CENTRAL_COMPLIANCE_GPG_PRIVATE_KEY }}
        run: printf '%s' "$KEY" | base64 -d | gpg --batch --import

      - name: Publish signed artifacts
        env:
          MAVEN_CENTRAL_USERNAME: ${{ secrets.MAVEN_CENTRAL_USERNAME }}
          MAVEN_CENTRAL_PASSWORD: ${{ secrets.MAVEN_CENTRAL_PASSWORD }}
          GPG_PASSPHRASE: ${{ secrets.CENTRAL_COMPLIANCE_GPG_PASSPHRASE }}
        run: |
          ./gradlew publishReleasePublicationToCentralRepository \
            -Psigning.gnupg.passphrase="$GPG_PASSPHRASE" \
            -PsdkVersion=${{ github.ref_name }}
```

Secrets are passed through `env:` and never interpolated into shell text. Rotate the key yearly
(Phase 7.4) and document in `SECURITY.md` that it exists only for Sonatype compliance.

---

## Phase 5 — Finalize: `SHA256SUMS.txt`, keyless signature, GitHub Release

One finalize job for both platforms. It does **not** re-attest artifacts; the platform jobs already
did (re-attesting would duplicate provenance and require per-platform SBOM paths).

`polar-ble-sdk/.github/workflows/release-finalize.yml`:

```yaml
name: Finalize release
on: { workflow_call: {} }
jobs:
  finalize:
    runs-on: ubuntu-latest
    permissions: { contents: write, id-token: write }
    steps:
      - uses: actions/download-artifact@v4
        with: { path: dist, merge-multiple: true }

      - name: Generate SHA256SUMS.txt
        working-directory: dist
        run: sha256sum * > SHA256SUMS.txt

      - uses: sigstore/cosign-installer@v3

      - name: Keyless-sign SHA256SUMS.txt (Sigstore OIDC, no private key)
        working-directory: dist
        run: |
          cosign sign-blob --yes \
            --output-signature SHA256SUMS.txt.sig \
            --output-certificate SHA256SUMS.txt.pem \
            SHA256SUMS.txt

      - name: Create the GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          files: dist/*
          generate_release_notes: true
```

Every release ships:

```
polar-ble-sdk-<v>.aar                     # Android
polar-ble-sdk-<v>-android-sbom.cdx.json   # CycloneDX SBOM (Android)
polar-ble-sdk-<v>-source.zip              # iOS / SPM source archive
polar-ble-sdk-<v>-ios-sbom.cdx.json       # CycloneDX SBOM (iOS)
release-commit.txt                        # attested tagged commit SHA
SHA256SUMS.txt
SHA256SUMS.txt.sig + .pem                 # cosign keyless (Sigstore) signature
# + GitHub attestations (provenance + SBOM) per AAR / source zip, and provenance for release-commit.txt
```

> SPM and JitPack keep resolving straight from the git tag as before — this bundle is an
> additional, independently verifiable artifact, not a replacement for how they fetch the SDK.

---

## Phase 6 — Consumer-facing verification documentation

Add `polar-ble-sdk/SECURITY.md` with a "Verifying a release" section:

````markdown
## Verifying a release

### 1. Download the release artifacts
```bash
gh release download 8.2.0 --repo polarofficial/polar-ble-sdk
```

### 2. Verify provenance and SBOM attestations (no keys to fetch)
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
`polarofficial/polar-ble-sdk`, from the tagged commit, with no human in the loop — and that the
attached SBOM describes that exact artifact.

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

Both platforms — CI policy gate (SPM has no native hook for source dependencies):
```bash
gh attestation verify polar-ble-sdk-*.aar --repo polarofficial/polar-ble-sdk \
  || { echo "Attestation verification failed — refusing to build"; exit 1; }
```
````

Link `SECURITY.md` from `README.md` and `AGENTS.md`. State clearly that the pipeline holds **no
secrets** (unless Phase 4 is enabled, in which case that key exists only for Sonatype compliance)
and that `gh attestation verify` is the sole source of truth for authenticity.

---

## Phase 7 — Make it recurring

### 7.1 One shared orchestrator closing block

Use the same text in both `orchestrate_releases.sh` files, but it is performed **once**, after
*both* Android and iOS repos have been mirrored into `polar-ble-sdk`:

```sh
echo "INSTRUCTIONS FOR MASTER BRANCH RELEASE (run ONCE, after BOTH android and ios sources are mirrored):\n
  1. Push all changes to polar-ble-sdk master (git checkout master && git add -A && git commit -m \"Release $SDK_VERSION\" && git push)\n
  2. Create the release tag (git tag -a -m \"Release $SDK_VERSION\" $SDK_VERSION && git push origin $SDK_VERSION)\n
     Tag creation is restricted to the release-automation identity via a tag-protection ruleset.\n
  3. CI takes over: .github/workflows/release.yml builds the AAR and the iOS source archive,\n
     generates CycloneDX SBOMs, mints provenance + SBOM attestations, and creates ONE GitHub\n
     Release. Watch it at https://github.com/polarofficial/polar-ble-sdk/actions\n
  4. Run the post-release verification (scripts/verify_release.sh $SDK_VERSION)\n
  5. Confirm JitPack still resolves: https://jitpack.io/#polarofficial/polar-ble-sdk\n
  6. Confirm SPM resolves the new tag (swift package resolve in a sample consumer project)\n
  7. Edit and publish the auto-generated release notes"
```

### 7.2 Post-release verification script

`polar-ble-sdk/scripts/verify_release.sh` — run after every release, and monthly via
`workflow_dispatch`/`schedule` to detect tampering after the fact:

```sh
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
[ "$found" -eq 2 ] || { echo "Expected 1 AAR + 1 source zip, found $found artifacts"; exit 1; }

cosign verify-blob SHA256SUMS.txt \
  --signature SHA256SUMS.txt.sig \
  --certificate SHA256SUMS.txt.pem \
  --certificate-identity-regexp "https://github.com/$REPO/.github/workflows/release-finalize.yml@.*" \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com

shasum -a 256 --check SHA256SUMS.txt

echo "Release $V verified (provenance + SBOM attestations + checksum bundle) for Android and iOS."
```

### 7.3 Release checklist (add to the runbook)

- [ ] Both `orchestrate_releases.sh` runs complete; version bumped and sources mirrored for Android **and** iOS
- [ ] Single tag pushed via the restricted release-automation identity; tag-protection ruleset green
- [ ] `release.yml` green: AAR + source archive built, both SBOMs generated, one GitHub Release created
- [ ] `gh attestation verify` passes for provenance and SBOM predicates on the AAR and the source zip
- [ ] `scripts/verify_release.sh <version>` passes
- [ ] SBOMs spot-checked for unexpected new dependencies/licenses
- [ ] JitPack resolves the new tag; `swift package resolve` in a sample consumer pulls it cleanly
- [ ] (If Phase 4 enabled) Maven Central publish succeeded and the compliance key is not expiring within 90 days
- [ ] Release notes published, `SECURITY.md` still accurate
- [ ] No CocoaPods artifacts (podspec, trunk push) referenced anywhere in the release flow

### 7.4 Key rotation

- Default pipeline: none required (no long-lived secrets or keys).
- If Phase 4 is enabled, rotate the Central-compliance PGP key yearly: generate a new key, register
  it with Sonatype Central Portal, update `CENTRAL_COMPLIANCE_GPG_PRIVATE_KEY` /
  `CENTRAL_COMPLIANCE_GPG_PASSPHRASE`. Consumer documentation does not change.

---

## Sequencing recommendation

| Order | Phase | Effort | Consumer-visible break |
|---|---|---|---|
| 1 | Phase 0 + 1 — tag protection, annotated tags | Low | None |
| 2 | Phase 2 + 5 — single CI release + SHA256SUMS + keyless signature | Medium | None |
| 3 | Phase 3 — Android and iOS builds, SBOMs, attestations | Medium | None (JitPack and SPM resolution unchanged) |
| 4 | Phase 6 + 7 — docs and recurring practice | Low | None |
| 5 | Phase 4 — optional direct Maven Central publishing | Medium | New coordinates source; JitPack keeps working |

---

## Relationship to other integrity documents

- Supersedes `HARDEN-RELEASE-INTEGRITY-ANDROID-ATTESTATIONS.md` and
  `ios-communications/HARDEN-RELEASE-INTEGRITY-IOS-ATTESTATIONS.md`.
- `HARDEN-RELEASE-INTEGRITY.md` / `HARDEN-RELEASE-INTEGRITY-ANDROID.md` (GPG-based): if partially
  implemented, remove `GPG_PRIVATE_KEY` / `GPG_PASSPHRASE` / `GPG_KEY_ID` secrets, delete
  `verify-tag.yml` and any `git verify-tag` calls (replaced by the Phase 0.2 ruleset), replace
  `gpg --detach-sign` of `SHA256SUMS.txt` with the keyless `cosign sign-blob` in Phase 5, add the
  CycloneDX Gradle plugin (Phase 3A.1), and make `gh attestation verify` the primary method in
  `SECURITY.md`.
- If an iOS binary distribution re-emerges (e.g. `polar-ble-sdk-binary`), layer
  `actions/attest-build-provenance` / `actions/attest-sbom` on the XCFramework zip instead of the
  source archive; only the subject changes.

