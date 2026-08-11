import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";

const expectedCertificate = "ab".repeat(32);
const verifierSource = fs
  .readFileSync(new URL("verify-apk.sh", import.meta.url), "utf8")
  .replaceAll("\r\n", "\n");
const bashPlatform = spawnSync("bash", ["-c", "uname -s"], { encoding: "utf8" }).stdout.trim();
const bashUsesWindowsBatch = /^(?:MINGW|MSYS|CYGWIN)/.test(bashPlatform);

function fakeDigest(hex) {
  return hex.toUpperCase().match(/../g).join(":");
}

const oneSignerOutput = [
  "WARNING: META-INF/services entry is not protected by the APK signature",
  "Signer #1 certificate DN: CN=ThystTV Release",
  `Signer #1 certificate SHA-256 digest: ${fakeDigest(expectedCertificate)}`,
  "Signer #1 certificate SHA-1 digest: 00:11:22:33",
  "Verified using v2 scheme (APK Signature Scheme v2): true",
].join("\n");

function writeExecutable(file, contents) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, contents, { mode: 0o755 });
  fs.chmodSync(file, 0o755);
}

function validateTemporaryRoot(root, workspace) {
  assert.equal(path.dirname(root), workspace);
  assert.match(path.basename(root), /^\.verify-apk-test-[A-Za-z0-9]+$/);
}

function runVerifier({
  signerOutput = oneSignerOutput,
  signerStderr = "",
  apksignerExit = 0,
  pinnedCertificate = expectedCertificate,
  toolExtension = "",
} = {}) {
  const workspace = process.cwd();
  const root = fs.mkdtempSync(path.join(workspace, ".verify-apk-test-"));
  validateTemporaryRoot(root, workspace);

  try {
    const androidHome = path.join(root, "android-sdk");
    const apk = path.join(root, "candidate.apk");
    fs.writeFileSync(path.join(root, "verify-apk.sh"), verifierSource);
    fs.writeFileSync(apk, "test APK bytes");
    fs.writeFileSync(path.join(root, "apksigner-output.txt"), `${signerOutput}\n`);
    fs.writeFileSync(path.join(root, "apksigner-stderr.txt"), `${signerStderr}\n`);

    const apkanalyzer = bashUsesWindowsBatch && toolExtension === ".bat"
      ? [
          "@echo off",
          'if "%2"=="application-id" goto application_id',
          'if "%2"=="version-name" goto version_name',
          'if "%2"=="version-code" goto version_code',
          "exit /b 2",
          ":application_id",
          "echo com.tzii.thysttv",
          "exit /b 0",
          ":version_name",
          "echo 1.2.1",
          "exit /b 0",
          ":version_code",
          "echo 11",
          "exit /b 0",
          "",
        ].join("\r\n")
      : `#!/usr/bin/env bash\ncase "$2" in\n  application-id) echo com.tzii.thysttv ;;\n  version-name) echo 1.2.1 ;;\n  version-code) echo 11 ;;\n  *) exit 2 ;;\nesac\n`;
    const apksigner = bashUsesWindowsBatch && toolExtension === ".bat"
      ? [
          "@echo off",
          "type apksigner-output.txt",
          "type apksigner-stderr.txt 1>&2",
          `exit /b ${apksignerExit}`,
          "",
        ].join("\r\n")
      : `#!/usr/bin/env bash\ncat apksigner-output.txt\ncat apksigner-stderr.txt >&2\nexit ${apksignerExit}\n`;

    writeExecutable(
      path.join(androidHome, "cmdline-tools", "latest", "bin", `apkanalyzer${toolExtension}`),
      apkanalyzer,
    );
    writeExecutable(
      path.join(androidHome, "build-tools", "35.0.0", `apksigner${toolExtension}`),
      apksigner,
    );

    const command = [
      `cd ${path.basename(root)}`,
      "export ANDROID_HOME=./android-sdk",
      "export APK_PATH=./candidate.apk",
      "export EXPECTED_PACKAGE_ID=com.tzii.thysttv",
      "export EXPECTED_VERSION_NAME=1.2.1",
      "export EXPECTED_VERSION_CODE=11",
      `export EXPECTED_CERT_SHA256=${pinnedCertificate}`,
      "bash verify-apk.sh",
    ].join("\n");

    return spawnSync("bash", ["-c", command], {
      cwd: workspace,
      encoding: "utf8",
      env: process.env,
    });
  } finally {
    validateTemporaryRoot(root, workspace);
    fs.rmSync(root, { recursive: true, force: true });
  }
}

test("accepts exactly one signer, ignores unrelated output, and normalizes its digest", () => {
  const result = runVerifier();

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, new RegExp(`certificate_sha256=${expectedCertificate}`));
});

test("rejects APKs with multiple signer certificates", () => {
  const result = runVerifier({
    signerOutput: [
      oneSignerOutput,
      `Signer #2 certificate SHA-256 digest: ${fakeDigest("cd".repeat(32))}`,
    ].join("\n"),
  });

  assert.notEqual(result.status, 0, result.stdout);
  assert.match(result.stderr, /expected exactly one signer certificate SHA-256 digest, found 2/);
});

for (const scenario of [
  {
    name: "zero parsed signer digests",
    options: {
      signerOutput: [
        "WARNING: signer certificate details are incomplete",
        "Signer #1 certificate SHA-1 digest: 00:11:22:33",
        "Verified using v2 scheme (APK Signature Scheme v2): true",
      ].join("\n"),
    },
    error: /expected exactly one signer certificate SHA-256 digest, found 0/,
  },
  {
    name: "a malformed actual signer digest",
    options: {
      signerOutput: [
        "WARNING: unrelated verifier warning",
        "Signer #1 certificate SHA-256 digest: not-a-sha256-digest",
      ].join("\n"),
    },
    error: /certificate digest is not a 64-character lowercase hex value/,
  },
  {
    name: "a malformed pinned signer digest",
    options: { pinnedCertificate: "not-a-pinned-sha256-digest" },
    error: /expected certificate digest is not a 64-character lowercase hex value/,
  },
  {
    name: "a nonzero apksigner exit",
    options: {
      signerStderr: "ERROR: simulated apksigner verification failure",
      apksignerExit: 23,
    },
    error: /simulated apksigner verification failure/,
  },
]) {
  test(`rejects ${scenario.name}`, () => {
    const result = runVerifier(scenario.options);

    assert.notEqual(result.status, 0, result.stdout);
    assert.match(result.stderr, scenario.error);
  });
}

test("resolves standard Windows .bat Android SDK tools", () => {
  const result = runVerifier({ toolExtension: ".bat" });

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, new RegExp(`certificate_sha256=${expectedCertificate}`));
});
