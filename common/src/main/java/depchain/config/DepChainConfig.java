package depchain.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.crypto.SecretKey;

import depchain.crypto.CryptoUtils;
import depchain.events.EventBus;
import depchain.network.SocketManager;

public class DepChainConfig {
    public static final Integer PROCESSES = 4;
    public static final Integer FAIL_BOUND = 1;
    public static final Integer LEADER = 0;
    public String ADDRESS;
    public Integer basePort;

    public final ArrayList<PublicKey> publicKeys = new ArrayList<>();
    public final ConcurrentMap<Integer, SecretKey> secretKeys = new ConcurrentHashMap<>();
    public PrivateKey privateKey;
    public Integer selfId;
    public EventBus eventBus;
    public SocketManager socketManager;

    public DepChainConfig(int myId) {
        this(myId, 8080, "127.0.0.1");
    }

    public DepChainConfig(int myId, int basePort, String address) {
        this.basePort = basePort;
        this.ADDRESS = address;
        try {
            // Load public keys
            for (int i = 0; i < PROCESSES; i++) {
                try (InputStream pubKeyStream = getClass().getResourceAsStream("/keys/public_key_" + i + ".pem")) {
                    if (pubKeyStream == null) {
                        throw new RuntimeException("Public key not found: public_key_" + i + ".pem");
                    }
                    publicKeys.add(CryptoUtils.loadPublicKey(pubKeyStream));
                }
            }

            // Load private key
            this.selfId = myId;
            try (InputStream privKeyStream = getClass().getResourceAsStream("/keys/private_key_" + Math.abs(selfId) + ".pem")) {
                if (privKeyStream == null) {
                    throw new RuntimeException("Private key not found: private_key_" + Math.abs(selfId) + ".pem");
                }
                this.privateKey = CryptoUtils.loadPrivateKey(privKeyStream);
            }

            this.eventBus = new EventBus();
            this.socketManager = new SocketManager(this);
            this.basePort = this.getBasePort();
        } catch (IOException | IllegalArgumentException e) {
            throw new RuntimeException(e);
        }
    }

    public Integer getBasePort() {
        return this.socketManager.getSocket().getLocalPort() - this.selfId;
    }

    public PublicKey getPublicKey(int id) {
        return publicKeys.get(Math.abs(id));
    }

    public PublicKey getSelfPublicKey() {
        return publicKeys.get(selfId);
    }

    public PrivateKey getSelfPrivateKey() {
        return privateKey;
    }

    public int getSelfId(){
        return selfId;
    }
}
