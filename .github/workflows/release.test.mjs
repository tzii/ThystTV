import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";

const buildGradle = fs.readFileSync("app/build.gradle.kts", "utf8");
const ciWorkflow = fs.readFileSync(".github/workflows/ci.yml", "utf8");
const debugWorkflow = fs.readFileSync(".github/workflows/debug-build.yml", "utf8");
const workflow = fs.readFileSync(".github/workflows/release.yml", "utf8");
const workflows = [
  ["release", workflow],
  ["CI", ciWorkflow],
  ["debug build", debugWorkflow],
];
const permittedActions = new Set([
  "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1",
  "actions/setup-java@b6effb05e454b25005698d916606bdc6ffcbf961",
  "gradle/actions/setup-gradle@9c971963bec38e04b3d30dcc455b5382be2fdbfb",
  "actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a",
  "actions/download-artifact@3e5f45b2cfb9172054b4087a40e8e0b5a5461e7c",
]);

function actionSteps(workflowName, contents) {
  return contents
    .split(/(?=^ {6}- name: )/m)
    .filter((block) => block.startsWith("      - name: "))
    .map((block) => ({
      workflowName,
      stepName: block.match(/^ {6}- name:\s*([^\r\n]+)/m)?.[1] ?? "unnamed",
      uses: block.match(/^\s*uses:\s*([^\s#]+)/m)?.[1],
      block,
    }))
    .filter((step) => step.uses);
}

function actionInput(step, inputName) {
  const withSection = step.block.match(
    /^ {8}with:\s*\r?\n([\s\S]*?)(?=^ {8}\S|(?![\s\S]))/m,
  )?.[1] ?? "";
  return withSection.match(new RegExp(`^ {10}${inputName}:\\s*([^\\s#]+)`, "m"))?.[1];
}

function hasContentsReadPermission(contents) {
  for (const indent of [0, 4]) {
    const spaces = " ".repeat(indent);
    const childSpaces = " ".repeat(indent + 2);
    const blocks = contents.match(
      new RegExp(`^${spaces}permissions:\\s*\\r?\\n(?:^${childSpaces}[^\\r\\n]+\\r?\\n?)*`, "gm"),
    ) ?? [];
    if (blocks.some((block) => new RegExp(`^${childSpaces}contents:\\s*read\\s*$`, "m").test(block))) {
      return true;
    }
  }
  return false;
}

function releaseStepContaining(fragment) {
  return workflow
    .split(/(?=^ {6}- name: )/m)
    .find((step) => step.startsWith("      - name: ") && step.includes(fragment));
}

function validateTemporaryRoot(root, workspace) {
  assert.equal(path.dirname(root), workspace);
  assert.match(path.basename(root), /^\.manifest-checksum-test-[A-Za-z0-9]+$/);
}

test("release signing never falls back to the debug key", () => {
  const releaseBlock = buildGradle.match(/release\s*\{[\s\S]*?\n\s{8}\}/)?.[0] ?? "";
  assert.doesNotMatch(releaseBlock, /getByName\("debug"\)/);
  assert.match(buildGradle, /releaseKeystore\.isFile/);
  for (const name of ["KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD"]) {
    assert.match(buildGradle, new RegExp(name));
  }
});

test("every build and release workflow action is on the exact allowlist", () => {
  for (const [name, contents] of workflows) {
    const steps = actionSteps(name, contents);
    const uses = [...contents.matchAll(/^\s*uses:\s*([^\s#]+)/gm)].map((match) => match[1]);
    assert.ok(uses.length > 0, `${name} workflow has no actions`);
    assert.deepEqual(
      steps.map((step) => step.uses),
      uses,
      `${name} workflow has an action outside a named step block`,
    );
    for (const step of steps) {
      assert.ok(
        permittedActions.has(step.uses),
        `${name} step ${step.stepName} uses unapproved action: ${step.uses}`,
      );
    }
  }
});

test("CI and debug workflows explicitly default to read-only contents", () => {
  assert.ok(hasContentsReadPermission(ciWorkflow), "CI workflow must set contents: read");
  assert.ok(hasContentsReadPermission(debugWorkflow), "debug workflow must set contents: read");
});

test("every checkout step disables persisted credentials", () => {
  const checkouts = workflows
    .flatMap(([name, contents]) => actionSteps(name, contents))
    .filter((step) => step.uses === "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1");
  assert.equal(checkouts.length, 4);
  for (const step of checkouts) {
    assert.equal(
      actionInput(step, "persist-credentials"),
      "false",
      `${step.workflowName} step ${step.stepName} must disable persisted credentials`,
    );
  }
});

test("every Gradle setup step selects the basic cache provider", () => {
  const gradleSteps = workflows
    .flatMap(([name, contents]) => actionSteps(name, contents))
    .filter((step) => step.uses === "gradle/actions/setup-gradle@9c971963bec38e04b3d30dcc455b5382be2fdbfb");
  assert.equal(gradleSteps.length, 3);
  for (const step of gradleSteps) {
    assert.equal(
      actionInput(step, "cache-provider"),
      "basic",
      `${step.workflowName} step ${step.stepName} must select the basic cache provider`,
    );
  }
});

test("workflow exposes the build-once promotion state machine", () => {
  assert.match(workflow, /expected_rc_sha:/);
  assert.match(workflow, /build_signed_rc:/);
  assert.match(workflow, /promote_release:/);
});

test("default and job permissions are least privilege", () => {
  assert.match(workflow, /contents:\s*read/);
  assert.match(workflow, /actions:\s*read/);
  assert.match(workflow, /contents:\s*write/);
});

test("keystore handling is guarded and cleaned up", () => {
  assert.match(workflow, /release-keystore\.jks/);
  assert.match(workflow, /if:\s*always\(\)/);
  // Test and lint must run before the keystore is decoded.
  const keystoreDecode = workflow.indexOf("base64 --decode > app/release-keystore.jks");
  const unitTest = workflow.search(/gradlew(\.bat)? test /);
  const lint = workflow.search(/gradlew(\.bat)?[^\n]*lintDebug/);
  assert.ok(keystoreDecode > -1);
  assert.ok(unitTest > -1 && unitTest < keystoreDecode, "unit tests must precede the keystore decode");
  assert.ok(lint > -1 && lint < keystoreDecode, "lint must precede the keystore decode");
});

test("RC binds run ID and manifest digest through tag metadata", () => {
  assert.match(workflow, /RC-Workflow-Run/);
  assert.match(workflow, /RC-Manifest-SHA256/);
});

test("promotion publishes verified bytes only", () => {
  assert.match(workflow, /--verify-tag/);
  const promotion = workflow.split(/^\s{2}promote_release:\s*$/m)[1] ?? "";
  assert.ok(promotion.length > 0, "promote_release job not found");
  assert.doesNotMatch(
    promotion,
    /(?:\.\/)?gradlew|assembleRelease|KEYSTORE_BASE64|KEYSTORE_PASSWORD|KEY_ALIAS|KEY_PASSWORD/,
  );
  assert.match(promotion, /Protect release tags/);
  assert.match(promotion, /Authorize release tag creation/);
  assert.match(promotion, /release-metadata\.mjs policy/);
  assert.match(promotion, /github\.actor[^\n]*tzii|tzii[^\n]*github\.actor/);
  assert.match(promotion, /release-bundle\/rc-manifest\.json/);
  assert.match(promotion, /178386212/);
});

test("manifest checksum is generated and verified with directory-stable paths", () => {
  const checksumStep = releaseStepContaining("rc-manifest.json >");
  assert.ok(checksumStep, "manifest checksum generation step not found");
  const workingDirectory = checksumStep.match(/^\s*working-directory:\s*([^\s#]+)\s*$/m)?.[1] ?? ".";
  const checksumCommand = checksumStep.match(/^\s*(sha256sum\s+[^\n]+rc-manifest\.json\.sha256)\s*$/m)?.[1];
  assert.ok(checksumCommand, "manifest checksum generation command not found");

  const promotion = workflow.split(/^\s{2}promote_release:\s*$/m)[1] ?? "";
  const verifyCommand = promotion.match(/^\s*(\(cd release-bundle && sha256sum --check rc-manifest\.json\.sha256\))\s*$/m)?.[1];
  assert.ok(verifyCommand, "manifest checksum verification command not found");

  const workspace = process.cwd();
  const root = fs.mkdtempSync(path.join(workspace, ".manifest-checksum-test-"));
  validateTemporaryRoot(root, workspace);

  try {
    fs.mkdirSync(path.join(root, "release-bundle"));
    fs.writeFileSync(path.join(root, "release-bundle", "rc-manifest.json"), '{"version":1}\n');
    assert.match(workingDirectory, /^[A-Za-z0-9._/-]+$/);
    const temporaryDirectory = path.basename(root);
    const generated = spawnSync(
      "bash",
      ["-c", `cd ${temporaryDirectory}/${workingDirectory} && ${checksumCommand}`],
      { cwd: workspace, encoding: "utf8" },
    );
    const verified = spawnSync(
      "bash",
      ["-c", `cd ${temporaryDirectory} && ${verifyCommand}`],
      { cwd: workspace, encoding: "utf8" },
    );

    assert.equal(generated.status, 0, generated.stderr);
    assert.equal(verified.status, 0, verified.stderr);
    assert.match(verified.stdout, /rc-manifest\.json: OK/);
  } finally {
    validateTemporaryRoot(root, workspace);
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test("promotion reads the official signing certificate and attaches required assets", () => {
  assert.match(workflow, /official-signing-certificate\.sha256/);
  const promotion = workflow.split(/^\s{2}promote_release:\s*$/m)[1] ?? "";
  assert.match(promotion, /download-artifact/);
  assert.match(promotion, /run-id:/);
  assert.match(promotion, /github-token:/);
  assert.match(promotion, /ThystTV-\$\{VERSION_NAME\}\.apk"/);
  assert.match(promotion, /ThystTV-\$\{VERSION_NAME\}\.apk\.sha256"/);
  assert.match(promotion, /rc-manifest\.json"/);
});

test("artifacts are never overwritten", () => {
  assert.match(workflow, /overwrite:\s*false/);
  assert.doesNotMatch(workflow, /overwrite:\s*true/);
});

test("release notes cannot fall back to generated placeholder text", () => {
  assert.doesNotMatch(workflow, /Release notes were not found/);
  assert.match(workflow, /--notes-file "docs\/release-notes\/\$\{VERSION_NAME\}\.md"/);
});

test("PR CI runs repository contract tests and the release verifier syntax check", () => {
  assert.match(
    ciWorkflow,
    /node --test docs\/site\.test\.js \.github\/workflows\/release\.test\.mjs scripts\/release\/\*\.test\.mjs/,
  );
  assert.match(ciWorkflow, /bash -n scripts\/release\/verify-apk\.sh/);
});
