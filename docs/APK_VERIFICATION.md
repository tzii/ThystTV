# Verifying ThystTV APKs

Every official ThystTV release APK is signed with the same release key and published on
[GitHub Releases](https://github.com/tzii/ThystTV/releases). This page lets you verify that
an APK you downloaded is a genuine, unmodified ThystTV build. (Requested in
[issue #8](https://github.com/tzii/ThystTV/issues/8).)

## Official identity

| Field | Value |
|---|---|
| Package name (`applicationId`) | `com.tzii.thysttv` |
| Release signing certificate SHA-256 | `7F:8A:84:3B:92:56:1E:0F:FF:49:D7:75:89:F5:4D:95:16:9F:6B:73:9F:CF:23:5B:52:D4:CA:6B:8A:B7:1F:4A` |

Same fingerprint without separators (the format used by `AllowedAPKSigningKeys` /
F-Droid-style repos and some verification tools):

```text
7f8a843b92561e0fff49d77589f54d95169f6b739fcf235b52d4ca6b8ab71f4a
```

The intention is to keep using this release signing key for all future ThystTV updates. If
that ever has to change, it will be announced clearly in the release notes — never silently.

> Debug builds (`com.tzii.thysttv.debug`) use a throwaway debug key and are **not** covered
> by this fingerprint.

## Verify the signing certificate

### With apksigner (Android SDK build-tools)

```bash
apksigner verify --print-certs ThystTV-X.Y.Z.apk
```

Check that the output contains:

```text
Signer #1 certificate SHA-256 digest: 7f8a843b92561e0fff49d77589f54d95169f6b739fcf235b52d4ca6b8ab71f4a
```

### With keytool (any JDK)

```bash
keytool -printcert -jarfile ThystTV-X.Y.Z.apk
```

Compare the `SHA256:` line against the fingerprint above.

### With AppVerifier (on-device)

[AppVerifier](https://github.com/soupslurpr/AppVerifier) can verify an installed app or an
APK file. Use this entry:

```text
com.tzii.thysttv
7F:8A:84:3B:92:56:1E:0F:FF:49:D7:75:89:F5:4D:95:16:9F:6B:73:9F:CF:23:5B:52:D4:CA:6B:8A:B7:1F:4A
```

## Verify the file checksum

Each GitHub release attaches the APK, and GitHub publishes a `sha256:` digest for every
release asset (visible via the API and shown next to assets). Releases also ship a
`ThystTV-X.Y.Z.apk.sha256` checksum file starting after v1.2.0.

```bash
sha256sum ThystTV-X.Y.Z.apk
# Windows PowerShell:
Get-FileHash ThystTV-X.Y.Z.apk -Algorithm SHA256
```

Known checksums:

| Release | APK | SHA-256 |
|---|---|---|
| v1.2.0 | `ThystTV-1.2.0.apk` | `b597295d17bc64478e7369135495298333958345803232ea3363c8e7d68968bf` |

## Troubleshooting: "App not installed" / signature mismatch

- **Updating from an older official ThystTV?** All official releases use the same key, so
  updates install over each other. If an update is refused, re-download the APK (a corrupted
  download is the most common cause) and verify the checksum.
- **Coming from a debug build or a self-built APK?** Those are signed with a different key.
  Android will refuse the update — uninstall the old build first (this clears local app data,
  including local stats).
- **Coming from upstream Xtra?** ThystTV uses a different package name (`com.tzii.thysttv`),
  so it installs alongside Xtra; there is no signature conflict.
- **Fingerprint doesn't match the value above?** Do not install the APK. Only download
  ThystTV from [github.com/tzii/ThystTV/releases](https://github.com/tzii/ThystTV/releases),
  and please report where you got the file via an issue.
