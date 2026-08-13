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
  structuredSignerOutput = expectedCertificate,
  structuredSignerStderr = "",
  structuredSignerExit = 0,
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
    fs.writeFileSync(path.join(root, "structured-signer-output.txt"), `${structuredSignerOutput}\n`);
    fs.writeFileSync(path.join(root, "structured-signer-stderr.txt"), `${structuredSignerStderr}\n`);
    fs.writeFileSync(path.join(root, "VerifyApkSigner.java"), "// test placeholder; fake java intercepts this source file\n");

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
    writeExecutable(
      path.join(androidHome, "cmdline-tools", "latest", "bin", `apkanalyzer${toolExtension}`),
      apkanalyzer,
    );
    fs.mkdirSync(path.join(androidHome, "build-tools", "35.0.0", "lib"), { recursive: true });
    fs.writeFileSync(path.join(androidHome, "build-tools", "35.0.0", "lib", "apksigner.jar"), "test jar");
    writeExecutable(
      path.join(root, "bin", "java"),
      `#!/usr/bin/env bash\ncat structured-signer-output.txt\ncat structured-signer-stderr.txt >&2\nexit ${structuredSignerExit}\n`,
    );

    const command = [
      `cd ${path.basename(root)}`,
      "export ANDROID_HOME=./android-sdk",
      "export APK_PATH=./candidate.apk",
      "export EXPECTED_PACKAGE_ID=com.tzii.thysttv",
      "export EXPECTED_VERSION_NAME=1.2.1",
      "export EXPECTED_VERSION_CODE=11",
      `export EXPECTED_CERT_SHA256=${pinnedCertificate}`,
      'export PATH="./bin:$PATH"',
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

test("accepts and normalizes exactly one structured signer digest", () => {
  const result = runVerifier({ structuredSignerOutput: fakeDigest(expectedCertificate) });

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, new RegExp(`certificate_sha256=${expectedCertificate}`));
});

test("rejects a valid signer digest that does not match the pin", () => {
  const result = runVerifier({ structuredSignerOutput: "cd".repeat(32) });

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /signing certificate mismatch/);
});

for (const scenario of [
  {
    name: "zero structured signer digests",
    options: { structuredSignerOutput: "" },
    error: /certificate digest is not a 64-character lowercase hex value/,
  },
  {
    name: "multiple structured signer digests",
    options: { structuredSignerOutput: `${expectedCertificate}\n${"cd".repeat(32)}` },
    error: /certificate digest is not a 64-character lowercase hex value/,
  },
  {
    name: "a malformed structured signer digest",
    options: { structuredSignerOutput: "not-a-sha256-digest" },
    error: /certificate digest is not a 64-character lowercase hex value/,
  },
  {
    name: "a malformed pinned signer digest",
    options: { pinnedCertificate: "not-a-pinned-sha256-digest" },
    error: /expected certificate digest is not a 64-character lowercase hex value/,
  },
  {
    name: "a structured verifier failure",
    options: {
      structuredSignerStderr: "structured APK signer verification failed: simulated failure",
      structuredSignerExit: 23,
    },
    error: /structured APK signer verification failed: simulated failure/,
  },
]) {
  test(`rejects ${scenario.name}`, () => {
    const result = runVerifier(scenario.options);

    assert.notEqual(result.status, 0, result.stdout);
    assert.match(result.stderr, scenario.error);
  });
}

test("resolves the standard Windows .bat apkanalyzer", () => {
  const result = runVerifier({ toolExtension: ".bat" });

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, new RegExp(`certificate_sha256=${expectedCertificate}`));
});
