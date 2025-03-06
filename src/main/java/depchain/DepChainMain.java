package depchain;

import depchain.network.AuthenticatedPerfectLink;
import depchain.network.Message;
import depchain.crypto.CryptoUtils;

import java.net.InetAddress;
import java.security.KeyPair;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import classes.State;

public class DepChainMain {
    private static final int PORT = 8080; // FIX ME
    private static final String[] MEMBERS = {"192.168.1.1", "192.168.1.2", "192.168.1.3"}; // FIX ME: Replace with actual addresses
    private static final int[] PORTS = {8080, 8081, 8082};
    private static int N_PROCESSES = 4;
    private static int KEY_SIZE = 256;
    private static int f = 1;
    private int leader_id;
    private State state;
       
    public static void main(String[] args) throws Exception {

        
        AuthenticatedPerfectLink[] links = new AuthenticatedPerfectLink[N_PROCESSES];
        for (int i = 0; i < N_PROCESSES; i++) {
            KeyPair kp = CryptoUtils.generateRSAKeyPair(KEY_SIZE);
            links[i] = new AuthenticatedPerfectLink(PORTS[i], kp.getPrivate(), kp.getPublic());
        }
        
        //PHASE 1 READ NEEDS  A LOT OF FIXING

        // Create an instance of SignedConditionalCollect for each process.
        SignedConditionalCollect[] collectors = new SignedConditionalCollect[N_PROCESSES];
        // Let process "0" be the leader.
        int leaderId = 0;
        for (int i = 0; i < N_PROCESSES; i++) {
            collectors[i] = new SignedConditionalCollect(N_PROCESSES, f, leaderId, links[i]);
        }
        
        // Wait for one of the processes (say, process 1) to collect the final vector.
        Object[] collectedVector = collectors[1].awaitCollected();

    
        //PHASE 2 WRITE

    }
}
