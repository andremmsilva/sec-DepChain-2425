package depchain.crypto;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.stream.Collectors;

public class CryptoUtils {

    // --- MAC functions using HmacSHA256 ---

    // Compute MAC for a given message using the shared secret key.
    public static byte[] computeMAC(String message, SecretKey key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(message.getBytes());
        } catch (Exception e) {
            System.err.println("Error computing MAC: " + e.getMessage());
            return new byte[0];
        }
    }

    // Verify the MAC for the given message.
    public static boolean verifyMAC(String message, byte[] macBytes, SecretKey key) {
        byte[] computed = computeMAC(message, key);
        return MessageDigest.isEqual(computed, macBytes);
    }

    // Generate a shared key for MAC (e.g., HmacSHA256 with a 256-bit key)
    public static SecretKey generateSharedKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("HmacSHA256");
        keyGen.init(256);
        return keyGen.generateKey();
    }

    // --- Legacy RSA functions (if still needed) ---
    public static byte[] signMessage(String message, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(message.getBytes());
            return signature.sign();
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            System.err.println("Error signing message: " + e.getMessage());
            return new byte[0];
        }
    }

    public static boolean verifySignature(String message, byte[] signatureBytes, PublicKey publicKey) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(message.getBytes());
            return signature.verify(signatureBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            System.err.println("Error verifying signature: " + e.getMessage());
            return false;
        }
    }

    public static byte[] cipherMessage(byte[] message, PublicKey publicKey) {
        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return cipher.doFinal(message);
        } catch (NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException | NoSuchAlgorithmException |
                 InvalidKeyException e) {
            System.err.println("Error ciphering message: " + e.getMessage());
            return new byte[0];
        }
    }

    public static byte[] decipherMessage(byte[] cipherText, PrivateKey privateKey) {
        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return cipher.doFinal(cipherText);
        } catch (NoSuchPaddingException | IllegalBlockSizeException | NoSuchAlgorithmException | BadPaddingException |
                 InvalidKeyException e) {
            System.err.println("Error deciphering message " + encodeToBase64(cipherText) + ": " + e.getMessage());
            return new byte[0];
        }
    }

    public static PrivateKey loadPrivateKey(InputStream inputStream) {
        try {
            String keyPem = new BufferedReader(new InputStreamReader(inputStream))
                    .lines()
                    .filter(line -> !line.contains("-----")) // Remove header/footer lines
                    .collect(Collectors.joining());

            byte[] keyBytes = Base64.getDecoder().decode(keyPem);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(spec);
        } catch (Exception e) {
            System.err.println("Error loading private key: " + e.getMessage());
            return null;
        }
    }

    public static PublicKey loadPublicKey(InputStream inputStream) {
        try {
            String keyPem = new BufferedReader(new InputStreamReader(inputStream))
                    .lines()
                    .filter(line -> !line.contains("-----")) // Remove header/footer lines
                    .collect(Collectors.joining());

            byte[] keyBytes = Base64.getDecoder().decode(keyPem);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(spec);
        } catch (Exception e) {
            System.err.println("Error loading public key: " + e.getMessage());
            return null;
        }
    }

    public static SecretKey secretKeyFromBytes(byte[] keyBytes) throws NoSuchAlgorithmException,
            InvalidKeySpecException {
        try {
            return new SecretKeySpec(keyBytes, 0, keyBytes.length, "AES");
        } catch (Exception e) {
            System.err.println("Error decoding secret key: " + e.getMessage());
            return null;
        }
    }

    public static String encodeToBase64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    public static byte[] decodeFromBase64(String data) throws IllegalArgumentException {
        return Base64.getDecoder().decode(data);
    }
}
