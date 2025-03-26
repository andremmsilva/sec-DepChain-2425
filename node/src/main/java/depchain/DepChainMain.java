package depchain;

import depchain.config.DepChainConfig;
import depchain.consensus.ByzantineLeaderDrivenConsensus;
import depchain.consensus.LDCDecideEvent;
import depchain.consensus.LDCProposeEvent;
import depchain.network.apl.AuthenticatedPerfectLink;
import depchain.network.fll.FLLSendEvent;
import depchain.network.message.Message;
import depchain.network.message.MessagePayload;
import depchain.network.message.MessageType;
import depchain.network.sl.SLDeliverEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class DepChainMain {
    private final List<Object> blockChain = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger blockChainIdx = new AtomicInteger(0);
    private final AtomicInteger currentReqClientID = new AtomicInteger(0);
    private final DepChainConfig config;
    private final AuthenticatedPerfectLink apl;
    private final ByzantineLeaderDrivenConsensus BLDConsensus;

    // Consumers for diverse events
    private final Consumer<SLDeliverEvent> onSLDeliver = this::handleSLDeliver;
    private final Consumer<LDCDecideEvent> onLDCDecide = this::handleLDCDecide;

    public DepChainMain(DepChainConfig config) {
        this.config = config;
        this.apl = new AuthenticatedPerfectLink(config);
        this.BLDConsensus = new ByzantineLeaderDrivenConsensus(config);

        config.eventBus.register(SLDeliverEvent.class, onSLDeliver);
        config.eventBus.register(LDCDecideEvent.class, onLDCDecide);
    }

    public static void main(String[] args) {
        try {
            int nodeId = Integer.parseInt(args[0]);
            DepChainMain instance = new DepChainMain(new DepChainConfig(nodeId));
            System.out.println("Starting DepChain Node with id " + nodeId);
        } catch (NumberFormatException e) {
            System.out.println("Invalid node id: " + args[0]);
        }
    }

    public void shutdown() {
        config.eventBus.unregister(SLDeliverEvent.class, onSLDeliver);
        config.eventBus.unregister(LDCDecideEvent.class, onLDCDecide);

        this.BLDConsensus.shutdown();
        this.apl.close();
    }

    private void handleSLDeliver(SLDeliverEvent e) {
        // Not a client message - ignore
        if (!e.arg.getPayload().getType().equals(MessageType.APPEND_REQ)) {
            return;
        }

        String val = e.arg.getPayload().getData();
        String proposal = val.concat("####").concat(String.valueOf(blockChainIdx.get()));
        this.currentReqClientID.set(e.arg.getSenderId());
        System.out.println(this + " received APPEND message " + val + " from client " + currentReqClientID.get());

        // Propose this value as the next index in the blockchain
        System.out.println(this + " proposing " + val + " for idx " + blockChainIdx.get());
        config.eventBus.publish(new LDCProposeEvent(proposal));
    }

    private void handleLDCDecide(LDCDecideEvent e) {
        String decision = e.arg;
        String val = e.arg.split("####")[0];
        String idx = e.arg.split("####")[1];

        System.out.println(this + " received LDCDecide " + val + " for idx " + idx);

        // Reply to the client
        config.eventBus.publish(new FLLSendEvent(new Message(new MessagePayload(MessageType.APPEND_CONFIRMED,
                decision), config.selfId), currentReqClientID.get()));
        blockChainIdx.incrementAndGet();
    }

    @Override
    public String toString() {
        return "DepChainMain{id=" + config.selfId + "}";
    }
}
