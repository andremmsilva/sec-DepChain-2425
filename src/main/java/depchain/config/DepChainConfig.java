package depchain.config;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import depchain.crypto.CryptoUtils;
import depchain.events.EventBus;
import depchain.network.SocketManager;

import javax.crypto.SecretKey;

public class DepChainConfig {
    public static final Integer PROCESSES = 4;
    public static final Integer FAIL_BOUND = 1;
    public static final Integer LEADER = 0;
    public static final String ADDRESS = "127.0.0.1";
    public static final Integer BASE_PORT = 8080;
    

    public final ArrayList<PublicKey> publicKeys = new ArrayList<>();
    public PrivateKey privateKey;
    public Integer selfId;

    public final ConcurrentMap<Integer, SecretKey> secretKeys = new ConcurrentHashMap<>();

    public EventBus eventBus;
    public SocketManager socketManager;

    public DepChainConfig(int myId) {
        try {
            for (int i = 0; i < PROCESSES; i++) {
                publicKeys.add(CryptoUtils.loadPublicKey(makeKeyFileName("/src/resources/keys/public_key_" + i + ".pem")));
            }
            selfId = myId;
            privateKey = CryptoUtils.loadPrivateKey(makeKeyFileName("/src/resources/keys/private_key_" + selfId + ".pem"));
            this.eventBus = new EventBus();
            this.socketManager = new SocketManager(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public PublicKey getPublicKey(int id){
        return publicKeys.get(id);
    }

    public PublicKey getSelfPublicKey() {
        return publicKeys.get(selfId);
    }

    public PrivateKey getSelfPrivateKey(){
        return privateKey;
    }

    private String makeKeyFileName(String fileName) {
        return System.getProperty("user.dir") + fileName;
    }
}
