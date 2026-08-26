package me.dzusill.core.license;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Verifies Ed25519 signatures against the public key embedded in the jar (JDK-native, no crypto dependency). This is
 * the whole point of the licensing design: a spoofed or hosts-file-redirected license server cannot forge a valid
 * signature, so "the server said VALID" is worthless unless it is also correctly signed.
 */
public final class SignatureVerifier {

    private final PublicKey publicKey;

    /**
     * @param publicKeyB64
     *            base64 of the DER (X.509/SPKI) encoded Ed25519 public key
     */
    public SignatureVerifier(String publicKeyB64) {
        try {
            byte[] der = Base64.getDecoder().decode(publicKeyB64);
            this.publicKey = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Embedded license public key is corrupt", e);
        }
    }

    public boolean verify(String payload, String signatureB64) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initVerify(publicKey);
            signature.update(payload.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(signatureB64));
        } catch (Exception e) {
            return false; // malformed signature = not verified; the caller treats this like a network failure
        }
    }
}
