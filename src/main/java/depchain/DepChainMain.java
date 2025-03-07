package depchain;

import java.io.ObjectInputFilter;
import java.lang.classfile.instruction.ThrowInstruction;

import depchain.network.AuthenticatedPerfectLink;
import depchain.network.Message;
import depchain.network.SignedConditionalCollect;
import depchain.crypto.CryptoUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.KeyPair;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import depchain.config.*;

import classes.State;

public class DepChainMain {

    //As a non Leader I received a Read message
    public void read(DepChainConfig config, State state, AuthenticatedPerfectLink link) throws UnknownHostException {
        link.sendMessage(InetAddress.getByName(DepChainConfig.ADDRESS),DepChainConfig.BASE_PORT + DepChainConfig.LEADER , state.toString());
    }

    //As a Leader I have received someone's state
    public void state(DepChainConfig config, Message message, AuthenticatedPerfectLink link) throws UnknownHostException {
        //This has to apart of the conditional collect, we either change how it is structured or the conditional collect is done here
    }

    // As a non Leader I am receiving the Collected message
    public void collected(DepChainConfig config, Message message, AuthenticatedPerfectLink link){
        //Ver das duas condições senão escolher o valor do leader.
    }

    //As a Leader/Non Leader I have received a write message
    public void write(DepChainConfig config, Message message, AuthenticatedPerfectLink link){
        //Preciso de ver do quorum
    }

    public void accept(DepChainConfig config, Message message, AuthenticatedPerfectLink link){
        //Ver se tem quorum
    }

    public void waitForMessage(DepChainConfig config, State state, AuthenticatedPerfectLink link) throws UnknownHostException {
        Message message = null;
        try {
            while (message == null){
                message = link.getNextMessage();
            }
            switch (message.getMessage().substring(0, message.getMessage().indexOf('-'))) {
                case "READ":
                    read(config, state, link);
                    break;
                case "STATE":
                    state(config, message, link);
                    break;
                case "COLLECTED":
                    collected(config, message, link);
                    break;
                case "WRITE":
                    write(config, message, link);
                    break;
                case "ACCEPT":
                    accept(config, message, link);
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
        int currentEpoch = 0;
        State state = new State();
        DepChainConfig config = new DepChainConfig(my_id);
        AuthenticatedPerfectLink link = new AuthenticatedPerfectLink(DepChainConfig.BASE_PORT + my_id,
         config.getSelfPrivateKey(), config.getSelfPublicKey());


      
        //PHASE 1 READ 


    
        //PHASE 2 WRITE
    }
}
