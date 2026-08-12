import com.android.apksig.ApkVerifier;

import java.io.File;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class VerifyApkSigner {
    private VerifyApkSigner() {}

    public static void main(String[] args) {
        try {
            if (args.length != 1) {
                throw new IllegalArgumentException("expected exactly one APK path");
            }

            ApkVerifier.Result result = new ApkVerifier.Builder(new File(args[0])).build().verify();
            if (!result.isVerified()) {
                throw new IllegalArgumentException(
                        "APK signature verification failed with " + result.getErrors().size() + " error(s)");
            }

            List<byte[]> certificates = new ArrayList<>();
            for (X509Certificate certificate : result.getSignerCertificates()) {
                certificates.add(certificate.getEncoded());
            }
            System.out.println(digestExactlyOne(certificates));
        } catch (Exception error) {
            System.err.println("structured APK signer verification failed: " + error.getMessage());
            System.exit(1);
        }
    }

    public static String digestExactlyOne(List<byte[]> certificates) throws Exception {
        if (certificates.size() != 1) {
            throw new IllegalArgumentException(
                    "expected exactly one verified signer certificate, found " + certificates.size());
        }
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificates.get(0));
        return HexFormat.of().formatHex(digest);
    }
}
