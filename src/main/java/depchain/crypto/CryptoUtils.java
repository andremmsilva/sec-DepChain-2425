package depchain.crypto;

import java.security.*;
import java.util.Base64;

public class CryptoUtils {

    // Sign a message using the provided private key.
    public static byte[] signMessage(String message, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(message.getBytes());
            return signature.sign();
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            System.out.println("Error signing message: " + e.getMessage());
            return new byte[0];
        }
    }

    // Verify a signature using the provided public key.
    public static boolean verifySignature(String message, byte[] signatureBytes, PublicKey publicKey) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(message.getBytes());
            return signature.verify(signatureBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            System.out.println("Error verifying signature: " + e.getMessage());
            return false;
        }
    }

    // Utility: encode bytes to Base64 string.
    public static String encodeToBase64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    // Utility: decode Base64 string to bytes.
    public static byte[] decodeFromBase64(String data) {
        return Base64.getDecoder().decode(data);
    }
}
