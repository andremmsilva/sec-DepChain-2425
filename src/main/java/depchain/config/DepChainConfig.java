package depchain.config;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;

import depchain.crypto.CryptoUtils;

public class DepChainConfig {
    public static final Integer PROCESSES = 4;
    public static final Integer FAIL_BOUND = 1;
    public static final Integer LEADER = 0;
    public static final String ADDRESS = "127.0.0.1";
    public static final Integer BASE_PORT = 8080;
    

    public static final ArrayList<PublicKey> PUBLIC_KEYS = new ArrayList<>();
    public static PrivateKey PRIVATE_KEY;
    public static Integer SELF_ID;

    public DepChainConfig(int myId) {
        try {
            for (int i = 0; i < PROCESSES; i++) {
                PUBLIC_KEYS.add(CryptoUtils.loadPublicKey(makeKeyFileName("/src/resources/keys/public_key_" + i + ".pem")));
            }
            SELF_ID = myId;
            PRIVATE_KEY = CryptoUtils.loadPrivateKey(makeKeyFileName("/src/resources/keys/private_key_" + SELF_ID + ".pem"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public PublicKey getPublicKey(int id){
        return PUBLIC_KEYS.get(id);
    }

    public PublicKey getSelfPublicKey() {
        return PUBLIC_KEYS.get(SELF_ID);
    }

    public PrivateKey getSelfPrivateKey(){
        return PRIVATE_KEY;
    }

    private String makeKeyFileName(String fileName) {
        return System.getProperty("user.dir") + fileName;
    }
}
