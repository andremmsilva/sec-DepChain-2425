package depchain;

import com.google.gson.Gson;
import depchain.config.DepChainConfig;
import depchain.consensus.ByzantineLeaderDrivenConsensus;
import depchain.consensus.LDCDecideEvent;
import depchain.consensus.LDCProposeEvent;
import depchain.network.apl.AuthenticatedPerfectLink;
import depchain.network.message.Message;
import depchain.network.message.MessagePayload;
import depchain.network.message.MessageType;
import depchain.network.message.Transaction;
import depchain.network.sl.SLDeliverEvent;
import depchain.network.sl.SLSendEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class DepChainMain {
    private final List<Object> blockChain = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger blockChainIdx = new AtomicInteger(0);
    private final AtomicInteger currentReqClientID = new AtomicInteger(0);
    private final DepChainConfig config;
    private final AuthenticatedPerfectLink apl;
    private final AtomicBoolean inConsensus = new AtomicBoolean(false);
    private final Lock lock = new ReentrantLock();
    private final Condition consensusFinished = lock.newCondition();
    // Consumers for diverse events
    private final Consumer<SLDeliverEvent> onSLDeliver = this::handleSLDeliver;
    private ByzantineLeaderDrivenConsensus BLDConsensus;
    private final Consumer<LDCDecideEvent> onLDCDecide = this::handleLDCDecide;

    public DepChainMain(DepChainConfig config) {
        this.config = config;
        this.apl = new AuthenticatedPerfectLink(config);
//        this.BLDConsensus = new ByzantineLeaderDrivenConsensus(config);

        config.eventBus.register(SLDeliverEvent.class, onSLDeliver);
        config.eventBus.register(LDCDecideEvent.class, onLDCDecide);
    }

    public static void main(String[] args) {
        try {
            int nodeId = Integer.parseInt(args[0]);
            DepChainMain instance = new DepChainMain(new DepChainConfig(nodeId));
            System.out.println("Starting DepChain Node with id " + nodeId);
            // On shutdown, close the DepChain instance
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                instance.shutdown();
                System.out.println("Shutdown DepChain Node with id " + nodeId);
            }));
        } catch (NumberFormatException e) {
            System.out.println("Invalid node id: " + args[0]);
        }
    }

    public void shutdown() {
        config.eventBus.unregister(SLDeliverEvent.class, onSLDeliver);
        config.eventBus.unregister(LDCDecideEvent.class, onLDCDecide);

        this.apl.close();

        try {
            lock.lock();
            if (this.inConsensus.getAndSet(false)) {
                this.BLDConsensus.shutdown();
            }
            this.consensusFinished.signal();
        } finally {
            lock.unlock();
        }
    }

    private void handleSLDeliver(SLDeliverEvent e) {
        // Not a client message - ignore
        if (!(e.arg.getPayload().getType().equals(MessageType.BALANCE_REQ)||e.arg.getPayload().getType().equals(MessageType.TRANSFER_REQ)||e.arg.getPayload().getType().equals(MessageType.TRANSFERFROM_REQ))) {
            return;
        }

        // wait for the current consensus to finish
        while (this.inConsensus.getAndSet(true)) {
            try {
                lock.lock();
                this.consensusFinished.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            } finally {
                lock.unlock();
            }
        }

        // Create a new instance only after cleanup
        this.BLDConsensus = new ByzantineLeaderDrivenConsensus(config);
        Transaction transaction = e.arg.getPayload().getTransaction();
        transaction.setBlockId(String.valueOf(blockChainIdx.get()));
        this.currentReqClientID.set(e.arg.getSenderId());
        System.out.println(this + " received Transaction message " + e.arg.serialize(new Gson()) + " from client " + currentReqClientID.get());

        // Propose this value as the next index in the blockchain
        System.out.println(this + " proposing " + transaction + " for idx " + blockChainIdx.get());
        config.eventBus.publish(new LDCProposeEvent(transaction));
    }

    private void handleLDCDecide(LDCDecideEvent e) {
        Transaction decision = e.arg;

        System.out.println(this + " received LDCDecide " + decision.toString() + " for idx " + decision.getBlockId());

        // Reply to the client
        Message m = new Message(new MessagePayload(MessageType.TRANSACTION_CONFIRMED,
                decision), config.selfId);
        System.out.println(this + " sending " + m.serialize(new Gson()) + " to " + currentReqClientID.get());
        config.eventBus.publish(new SLSendEvent(m, currentReqClientID.get()));
        blockChain.add(blockChainIdx.getAndIncrement(), decision);
        try {
            this.lock.lock();
            if (this.inConsensus.getAndSet(false)) {
                ByzantineLeaderDrivenConsensus oldConsensus = this.BLDConsensus;
                this.BLDConsensus = null; // Clear reference first
                oldConsensus.shutdown();

                // Wait a bit longer for full cleanup
                Thread.sleep(1000);

            }
            // Reset client ID after handling
            this.consensusFinished.signal();
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public String toString() {
        return "DepChainMain{id=" + config.selfId + "}";
    }
}
