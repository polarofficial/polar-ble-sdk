# SDK Versioning Policy

## Versioning Scheme

The Polar BLE SDK uses semantic versioning: `MAJOR.MINOR.PATCH`.

- **MAJOR**: Breaking API changes or significant architectural changes.
- **MINOR**: New features and backwards-compatible changes.
- **PATCH**: Bug fixes and high-priority security patches.

## Platform Dependency Versioning

When the SDK updates its minimum required platform or runtime versions, this is treated as follows:

| Change Type | Minimum Support | Treatment |
|---|---|---|
| Minimum Android version increase | Official Google security support | MINOR release |
| Minimum iOS version increase | Official Apple security support | MINOR release |
| Minimum Java / Kotlin version | LTS and active support versions | MINOR release |
| Minimum Swift version | Official Apple support | MINOR release |

**Rationale**: SDK security support depends on platform security support. Raising minimum versions aligns SDK support scope with platform lifecycle.

### Major OS Version Support

The SDK supports the current and two most recent major OS versions for routine maintenance.

| Support Type | Scope |
|---|---|
| Active OS version | Bug fixes, security patches, technical support |
| Previous OS version (-1) | Critical security fixes, critical bug fixes |
| Previous OS version (-2) | Security fixes only if minimal effort; best-effort technical support |
| Older OS versions | Out of scope; users must upgrade to supported version |

## Third-Party Dependency Changes

Non-platform dependencies follow these rules:

- **Major version updates** of dependencies: MAJOR release (breaking API changes expected).
- **Minor and patch updates**: MINOR or PATCH release (backwards compatible).
- **Dependency removal or replacement**: MAJOR release (requires migration).

## Internal vs User-Handled Scope

**Polar handles internally:**
- Core BLE communication and device connectivity
- Protobuf protocol encoding and decoding
- Platform-specific native bridge implementation
- SDK API design and documentation
- Security patches and critical bug fixes
- Test coverage and quality assurance

**SDK users handle:**
- App-level error handling and reconnection logic
- Feature gating and device capability checking
- Background sync, offline data handling, and local storage
- Platform-specific permission requests and lifecycle management
- Device selection and pairing workflows
- Forked SDK customizations and maintenance

## Breaking Change Notice Period

Polar announces breaking changes at least **two (2) minor releases before a MAJOR release**. For example, changes planned for 7.0 must be announced no later than 6.1, leaving 6.1 and 6.2 for users to test deprecation paths and prepare migration.

## Feature Announcement

New features are announced at release time via:
- Release notes on [GitHub releases](https://github.com/polarofficial/polar-ble-sdk/releases)
- Migration guides (for breaking changes)
- Updated documentation

New features are not publicly announced before release.

## Platform Feature Parity

The SDK aims for feature parity between iOS and Android platforms within a single MAJOR version. If parity is not possible (due to platform limitations), this is documented in:
- Release notes
- Feature-specific documentation
- Known Issues list

Platform-specific limitations are noted in API documentation (KDoc / doc-comments).

## Release Information

All version releases are published at:  
**[https://github.com/polarofficial/polar-ble-sdk/releases](https://github.com/polarofficial/polar-ble-sdk/releases)**

Each release includes:
- Version number and release date
- New features and improvements
- Bug fixes and security patches
- Known issues
- Updated minimum platform and dependency versions
- Migration guide (if applicable for breaking changes)
- Links to API reference and documentation related to notable changes and updates

## Associated Materials

SDK support documentation and related materials, including this policy, can be updated by Polar separately from SDK releases.

---

**Last updated**: 2026-09-16  
**Policy Version**: 1.0
