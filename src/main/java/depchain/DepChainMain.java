package depchain;

import java.io.ObjectInputFilter;
import depchain.network.AuthenticatedPerfectLink;
import depchain.network.Message;
import depchain.network.SignedConditionalCollect;
import depchain.crypto.CryptoUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import depchain.config.*;

import classes.State;
import classes.Tuple;

public class DepChainMain {

    //As a non Leader I received a Read message
    public void read(DepChainState serverState) throws UnknownHostException {
        serverState.getLink(DepChainConfig.LEADER).sendMessage(InetAddress.getByName(DepChainConfig.ADDRESS),DepChainConfig.BASE_PORT + DepChainConfig.LEADER , serverState.getState().toString());
    }

    //As a Leader I have received someone's state
    public void state(DepChainState serverState, Message message) throws UnknownHostException {
        State state = convertMessageToState(message);
        serverState.addState(message.getSenderId(), state);
    }


    // As a non Leader I am receiving the Collected message
    public void collected(DepChainState state, Message message){
        //Ver das duas condições senão escolher o valor do leader.
    }

    //As a Leader/Non Leader I have received a write message
    public void write(DepChainState state, Message message){
        //Preciso de ver do quorum
    }

    public void accept(DepChainState state, Message message){
        //Ver se tem quorum
    }

    public void waitForMessage(DepChainState serverState, AuthenticatedPerfectLink link) throws UnknownHostException {
        Message message = null;
        try {
            while (message == null){
                //Temos de poder esperar por uma mensagem de todos.
                message = link.getNextMessage();
            }
            switch (message.getMessage().substring(0, message.getMessage().indexOf('-'))) {
                case "READ":
                    read(serverState);
                    break;
                case "STATE":
                    state(serverState, message);
                    break;
                case "COLLECTED":
                    collected(serverState, message);
                    break;
                case "WRITE":
                    write(serverState, message);
                    break;
                case "ACCEPT":
                    accept(serverState, message);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown command: " + message);
            }
            
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public State convertMessageToState(Message message) {
        String messageContent = message.getMessage(); // Extract the actual message (excluding signature)

        if (messageContent.isEmpty()) {
            System.err.println("Error: Received an empty message.");
            return null;
        }

        String[] parts = messageContent.split("\\|");
        if (parts.length != 2) {
            System.err.println("Malformed state message: " + messageContent);
            return null;
        }

        // Extract last_round (first part before "|")
        String[] lastRoundParts = parts[0].split(",");
        if (lastRoundParts.length != 2) {
            System.err.println("Malformed last round data: " + parts[0]);
            return null;
        }

        int lastRoundTimestamp = Integer.parseInt(lastRoundParts[0].trim());
        String lastRoundValue = lastRoundParts[1].trim();
        Tuple lastRound = new Tuple(lastRoundTimestamp, lastRoundValue);

        // Extract writeset (second part after "|")
        List<Tuple> writeSet = new ArrayList<>();
        if (!parts[1].isEmpty()) {
            String[] writesetEntries = parts[1].split(";");
            for (String entry : writesetEntries) {
                String[] tupleParts = entry.split(",");
                if (tupleParts.length == 2) {
                    int timestamp = Integer.parseInt(tupleParts[0].trim());
                    String value = tupleParts[1].trim();
                    writeSet.add(new Tuple(timestamp, value));
                } else {
                    System.err.println("Malformed writeset entry: " + entry);
                }
            }
        }

        return new State(lastRound, writeSet);
    }
       
    public static void main(String[] args) throws Exception {
        int leader_id = 0;
        int my_id = Integer.parseInt(args[0]);
        int currentEpoch = 0;
        DepChainConfig config = new DepChainConfig(my_id);
        AuthenticatedPerfectLink link = new AuthenticatedPerfectLink(DepChainConfig.BASE_PORT + my_id,
         config.getSelfPrivateKey(), config.getSelfPublicKey(), config);
        List<AuthenticatedPerfectLink> links = new ArrayList<>();
        links.add(my_id, link);
        DepChainState serverState = new DepChainState(config, links);

      
        //PHASE 1 READ 


    
        //PHASE 2 WRITE

    }
}
