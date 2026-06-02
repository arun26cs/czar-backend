import java.security.*;
import java.util.Base64;
import java.nio.file.*;

public class GenKeys {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: GenKeys <private.pem> <public.pem>");
            System.exit(1);
        }
        KeyPairGenerator kg = KeyPairGenerator.getInstance("RSA");
        kg.initialize(2048);
        KeyPair kp = kg.generateKeyPair();

        // Private key — PKCS#8 DER (getEncoded() on RSAPrivateKey returns PKCS#8)
        byte[] privBytes = kp.getPrivate().getEncoded();
        String privB64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(privBytes);
        String privPem = "-----BEGIN PRIVATE KEY-----\n" + privB64 + "\n-----END PRIVATE KEY-----\n";
        Files.writeString(Path.of(args[0]), privPem);

        // Public key — X.509/SubjectPublicKeyInfo DER
        byte[] pubBytes = kp.getPublic().getEncoded();
        String pubB64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(pubBytes);
        String pubPem = "-----BEGIN PUBLIC KEY-----\n" + pubB64 + "\n-----END PUBLIC KEY-----\n";
        Files.writeString(Path.of(args[1]), pubPem);

        System.out.println("RSA-2048 key pair written to:");
        System.out.println("  " + args[0]);
        System.out.println("  " + args[1]);
    }
}
