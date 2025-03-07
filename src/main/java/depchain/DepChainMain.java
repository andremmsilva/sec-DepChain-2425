package depchain;

import java.io.ObjectInputFilter;
import java.lang.classfile.instruction.ThrowInstruction;

import depchain.network.AuthenticatedPerfectLink;
import depchain.network.Message;
import depchain.network.SignedConditionalCollect;
import depchain.crypto.CryptoUtils;

import java.net.InetAddress;
import java.security.KeyPair;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import depchain.config.*;

import classes.State;

public class DepChainMain {
    private static final int PORT = 8080; // FIX ME
    private static final String[] MEMBERS = {"192.168.1.1", "192.168.1.2", "192.168.1.3"}; // FIX ME: Replace with actual addresses
    private static final int[] PORTS = {8080, 8081, 8082};
    private static int N_PROCESSES = 4;
    private static int KEY_SIZE = 256;
    private static int f = 1;


    public void read(DepChainConfig config) {
        
        Message message = new Message(null, config.BASE_PORT + config.LEADER, null, null);
        link.sendMessage(dest, leader_id, message);
    }

    public void waitForMessage(DepChainConfig config) {
        Message message = null;
        try {
            while (message == null){
                message = link.getNextMessage();
            }
            switch (message.getMessage().substring(0, message.indexOf('-'))) {
                case "READ":
                    read(config);
                    break;
                case "STATE":
                    state(config, message);
                    break;
                case "COLLECTED":
                    collected(config, message);
                    break;
                case "WRITE":
                    write(config, message);
                    break;
                case "ACCEPT":
                    accept(config, message);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown command: " + message);
            }
            
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
       
    public static void main(String[] args) throws Exception {
        int leader_id = 0;
        int my_id = Integer.parseInt(args[0]);
        State state;
        int currentEpoch = 0;
        AuthenticatedPerfectLink link; 
        DepChainConfig config = new DepChainConfig(my_id);
        
        link = new AuthenticatedPerfectLink(config.BASE_PORT + my_id, null, null);


      
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
