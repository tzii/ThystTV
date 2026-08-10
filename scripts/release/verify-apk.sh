#!/usr/bin/env bash
# Fail-closed APK inspection for ThystTV release candidates.
# Verifies package id, version name/code, and signing certificate against
# mandatory expected values, then reports non-secret key=value evidence.
set -euo pipefail

: "${APK_PATH:?APK_PATH is required}"
: "${EXPECTED_PACKAGE_ID:?EXPECTED_PACKAGE_ID is required}"
: "${EXPECTED_VERSION_NAME:?EXPECTED_VERSION_NAME is required}"
: "${EXPECTED_VERSION_CODE:?EXPECTED_VERSION_CODE is required}"
: "${EXPECTED_CERT_SHA256:?EXPECTED_CERT_SHA256 is required}"
: "${ANDROID_HOME:?ANDROID_HOME is required}"

if [[ ! -f "$APK_PATH" ]]; then
  echo "APK not found: $APK_PATH" >&2
  exit 1
fi

apkanalyzer="$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer"
if [[ ! -x "$apkanalyzer" ]]; then
  echo "apkanalyzer not found at $apkanalyzer" >&2
  exit 1
fi

apksigner="$(find "$ANDROID_HOME/build-tools" -name apksigner -type f | sort -V | tail -n 1)"
if [[ -z "$apksigner" ]]; then
  echo "apksigner not found under $ANDROID_HOME/build-tools" >&2
  exit 1
fi

actual_package="$("$apkanalyzer" manifest application-id "$APK_PATH")"
actual_version_name="$("$apkanalyzer" manifest version-name "$APK_PATH")"
actual_version_code="$("$apkanalyzer" manifest version-code "$APK_PATH")"
actual_certificate="$("$apksigner" verify --print-certs "$APK_PATH" | awk -F': ' '/Signer #1 certificate SHA-256 digest/ { print $2; exit }' | tr -d ':' | tr '[:upper:]' '[:lower:]')"
apk_sha256="$(sha256sum "$APK_PATH" | awk '{print $1}')"

if [[ ! "$actual_certificate" =~ ^[0-9a-f]{64}$ ]]; then
  echo "certificate digest is not a 64-character lowercase hex value" >&2
  exit 1
fi
if [[ ! "$apk_sha256" =~ ^[0-9a-f]{64}$ ]]; then
  echo "APK digest is not a 64-character lowercase hex value" >&2
  exit 1
fi

if [[ "$actual_package" != "$EXPECTED_PACKAGE_ID" ]]; then
  echo "package id mismatch: expected $EXPECTED_PACKAGE_ID, got $actual_package" >&2
  exit 1
fi
if [[ "$actual_version_name" != "$EXPECTED_VERSION_NAME" ]]; then
  echo "version name mismatch: expected $EXPECTED_VERSION_NAME, got $actual_version_name" >&2
  exit 1
fi
if [[ "$actual_version_code" != "$EXPECTED_VERSION_CODE" ]]; then
  echo "version code mismatch: expected $EXPECTED_VERSION_CODE, got $actual_version_code" >&2
  exit 1
fi
if [[ "$actual_certificate" != "$EXPECTED_CERT_SHA256" ]]; then
  echo "signing certificate mismatch: expected $EXPECTED_CERT_SHA256, got $actual_certificate" >&2
  exit 1
fi

echo "package_id=$actual_package"
echo "version_name=$actual_version_name"
echo "version_code=$actual_version_code"
echo "certificate_sha256=$actual_certificate"
echo "apk_sha256=$apk_sha256"
