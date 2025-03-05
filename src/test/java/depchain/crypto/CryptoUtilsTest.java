package depchain.crypto;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static org.junit.jupiter.api.Assertions.*;

public class CryptoUtilsTest {
    @Test
    public void testSignAndVerify() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        String message = "Test";
        byte[] signature = CryptoUtils.signMessage(message, keyPair.getPrivate());

        assertTrue(CryptoUtils.verifySignature(message, signature, keyPair.getPublic()));
    }
}
