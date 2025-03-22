package depchain.config;

import depchain.crypto.CryptoUtils;
import org.junit.jupiter.api.Test;

import java.security.PrivateKey;
import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DepChainConfigTest {
    @Test
    public void shouldLoadCorrectPublicKey() throws Exception {
        DepChainConfig config = new DepChainConfig(0, 0, "127.0.0.1");

        // Should have the correct id
        assertEquals(0, config.selfId);

        PublicKey publicKey = CryptoUtils.loadPublicKey("./src/resources/keys/public_key_0.pem");
        PrivateKey privateKey = CryptoUtils.loadPrivateKey("./src/resources/keys/private_key_0.pem");

        assertEquals(publicKey.toString(), config.getSelfPublicKey().toString());
        assertEquals(privateKey.toString(), config.privateKey.toString());
    }
}
