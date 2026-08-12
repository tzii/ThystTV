import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

test("structured signer helper main fails closed and excludes source stamps", () => {
  const helper = fileURLToPath(new URL("VerifyApkSigner.java", import.meta.url));
  const workspace = process.cwd();
  const root = fs.mkdtempSync(path.join(workspace, ".structured-signer-test-"));
  const apkVerifier = path.join(root, "com", "android", "apksig", "ApkVerifier.java");

  try {
    fs.mkdirSync(path.dirname(apkVerifier), { recursive: true });
    fs.writeFileSync(apkVerifier, `
package com.android.apksig;

import java.io.File;
import java.security.cert.X509Certificate;
import java.util.List;

public final class ApkVerifier {
    private final File apk;
    private ApkVerifier(File apk) { this.apk = apk; }
    public Result verify() { return Result.forName(apk.getName()); }

    public static final class Builder {
        private final File apk;
        public Builder(File apk) { this.apk = apk; }
        public ApkVerifier build() { return new ApkVerifier(apk); }
    }

    public static final class Result {
        private final boolean verified;
        private final List<X509Certificate> signers;
        private Result(boolean verified, List<X509Certificate> signers) {
            this.verified = verified;
            this.signers = signers;
        }
        static Result forName(String name) {
            if (name.equals("invalid.apk")) return new Result(false, List.of());
            if (name.equals("source-stamp-only.apk")) return new Result(true, List.of());
            if (name.equals("multiple.apk")) return new Result(true, List.of(new StubCertificate(1), new StubCertificate(2)));
            return new Result(true, List.of(new StubCertificate(1)));
        }
        public boolean isVerified() { return verified; }
        public List<Object> getErrors() { return verified ? List.of() : List.of("invalid signature"); }
        public List<X509Certificate> getSignerCertificates() { return signers; }
    }

    private static final class StubCertificate extends X509Certificate {
        private final int value;
        StubCertificate(int value) { this.value = value; }
        public byte[] getEncoded() { return new byte[] {(byte) value}; }
        public void checkValidity() {}
        public void checkValidity(java.util.Date date) {}
        public int getVersion() { return 3; }
        public java.math.BigInteger getSerialNumber() { return java.math.BigInteger.ONE; }
        public java.security.Principal getIssuerDN() { return null; }
        public java.security.Principal getSubjectDN() { return null; }
        public java.util.Date getNotBefore() { return new java.util.Date(0); }
        public java.util.Date getNotAfter() { return new java.util.Date(0); }
        public byte[] getTBSCertificate() { return new byte[0]; }
        public byte[] getSignature() { return new byte[0]; }
        public String getSigAlgName() { return "none"; }
        public String getSigAlgOID() { return "0"; }
        public byte[] getSigAlgParams() { return null; }
        public boolean[] getIssuerUniqueID() { return null; }
        public boolean[] getSubjectUniqueID() { return null; }
        public boolean[] getKeyUsage() { return null; }
        public int getBasicConstraints() { return -1; }
        public void verify(java.security.PublicKey key) {}
        public void verify(java.security.PublicKey key, String provider) {}
        public String toString() { return "stub"; }
        public java.security.PublicKey getPublicKey() { return null; }
        public boolean hasUnsupportedCriticalExtension() { return false; }
        public java.util.Set<String> getCriticalExtensionOIDs() { return null; }
        public java.util.Set<String> getNonCriticalExtensionOIDs() { return null; }
        public byte[] getExtensionValue(String oid) { return null; }
    }
}
`);

    const compile = spawnSync("javac", [
      "-d", root,
      apkVerifier,
    ], { encoding: "utf8" });
    assert.equal(compile.status, 0, `${compile.stdout}\n${compile.stderr}`);

    const runHelper = (apkName) => spawnSync("java", [
      "--class-path", root,
      "--source", "21",
      helper,
      path.join(root, apkName),
    ], { encoding: "utf8" });

    const oneSigner = runHelper("one.apk");
    assert.equal(oneSigner.status, 0, `${oneSigner.stdout}\n${oneSigner.stderr}`);
    assert.equal(oneSigner.stdout.trim(), "4bf5122f344554c53bde2ebb8cd2b7e3d1600ad631c385a5d7cce23c7785459a");

    for (const [apkName, error] of [
      ["invalid.apk", /APK signature verification failed with 1 error/],
      ["source-stamp-only.apk", /expected exactly one verified signer certificate, found 0/],
      ["multiple.apk", /expected exactly one verified signer certificate, found 2/],
    ]) {
      const result = runHelper(apkName);
      assert.notEqual(result.status, 0, result.stdout);
      assert.match(result.stderr, error);
    }
  } finally {
    assert.equal(path.dirname(root), workspace);
    assert.match(path.basename(root), /^\.structured-signer-test-[A-Za-z0-9]+$/);
    fs.rmSync(root, { recursive: true, force: true });
  }
});
