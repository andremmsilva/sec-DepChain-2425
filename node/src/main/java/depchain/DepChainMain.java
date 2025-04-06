package depchain;

import com.google.gson.reflect.TypeToken;
import depchain.classes.Block;
import depchain.config.DepChainConfig;
import depchain.consensus.ByzantineLeaderDrivenConsensus;
import depchain.consensus.LDCDecideEvent;
import depchain.consensus.LDCProposeEvent;
import depchain.crypto.CryptoUtils;
import depchain.network.apl.AuthenticatedPerfectLink;
import depchain.network.message.Message;
import depchain.network.message.MessagePayload;
import depchain.network.message.MessageType;
import depchain.network.message.Transaction;
import depchain.network.sl.SLDeliverEvent;
import depchain.network.sl.SLSendEvent;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class DepChainMain {
    private static final int BLOCK_SIZE = 10;
    private static final int BLOCK_TIMEOUT_MS = 1000;

    private final List<Block> blockChain = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger blockChainIdx = new AtomicInteger(0);
    private final DepChainConfig config;
    private final AuthenticatedPerfectLink apl;
    private final ContractExecutor contractExecutor;

    private final TransactionQueue transactionQueue;
    private final ScheduledExecutorService blockBuilderExecutor;
    private final AtomicBoolean inConsensus = new AtomicBoolean(false);
    private final Lock lock = new ReentrantLock();

    // Consumers for diverse events
    private final ByzantineLeaderDrivenConsensus BLDConsensus;
    private final Consumer<SLDeliverEvent> onSLDeliver = this::handleSLDeliver;
    private final Consumer<LDCDecideEvent> onLDCDecide = this::handleLDCDecide;

    public DepChainMain(DepChainConfig config) {
        this.config = config;
        this.apl = new AuthenticatedPerfectLink(config);
        this.contractExecutor = new ContractExecutor(config);
        this.BLDConsensus = new ByzantineLeaderDrivenConsensus(config);

        this.transactionQueue = new TransactionQueue(BLOCK_SIZE);
        this.blockBuilderExecutor = Executors.newSingleThreadScheduledExecutor();
        startBlockBuilder();

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

    private void startBlockBuilder() {
        blockBuilderExecutor.scheduleWithFixedDelay(() -> {
            // Skip if no transactions or consensus active
            if (transactionQueue.isEmpty() || inConsensus.get())
                return;

            List<MessagePayload> batch;

            try {
                lock.lock();
                // Double-check after acquiring lock
                if (inConsensus.get() || transactionQueue.isEmpty())
                    return;

                // Take either BLOCK_SIZE items or whatever is available after timeout
                batch = transactionQueue.takeBatch();
            } finally {
                lock.unlock();
            }

            if (!batch.isEmpty()) {
                startConsensusForBatch(batch);
            }
        }, BLOCK_TIMEOUT_MS, BLOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private void startConsensusForBatch(List<MessagePayload> batch) {
        inConsensus.set(true);

        // Serialize entire batch
        String batchData = serializeBatch(batch);
        String proposal = batchData + "####" + blockChainIdx.get();

        System.out.println("Proposing block " + blockChainIdx.get() +
                " with " + batch.size() + " transactions");
        config.eventBus.publish(new LDCProposeEvent(proposal));
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
        } finally {
            lock.unlock();
        }
    }

    private void handleSLDeliver(SLDeliverEvent e) {
        // Not a client message - ignore
        Message m = e.arg;
        if (!CryptoUtils.verifySignature(m.getPayload().serialize(config.gson), m.getSignature(),
                config.getPublicKey(m.getSenderId()))) {
            System.err.println("Received invalid signature in client message " + m.serialize(config.gson));
            return;
        }

        Transaction t = Transaction.fromJson(config.gson, m.getPayload().getData());
        if (!t.getSenderAddress().equals(config.accountAddresses.get(String.valueOf(m.getSenderId())))) {
            System.err.println("Received invalid sender address " + t.getSenderAddress());
            return;
        }

        switch (m.getPayload().getType()) {
            case BALANCE_REQ -> {
                System.out.println("Received BALANCE_REQ " + m.serialize(config.gson));
            }
            case TRANSFER_REQ -> {
                System.out.println("Received TRANSFER_REQ " + m.serialize(config.gson));
            }
            case TRANSFERFROM_REQ -> {
                System.out.println("Received TRANSFERFROM_REQ " + m.serialize(config.gson));
            }
            default -> {
                return;
            }
        }

        this.transactionQueue.add(m.getPayload());
        System.out.println("Queued transaction from client " +
                e.arg.getSenderId() +
                " (Queue size: " + transactionQueue.size() + ")");
    }

    private void handleLDCDecide(LDCDecideEvent e) {
        String[] parts = e.arg.split("####");
        String batchData = parts[0];
        int blockIndex = Integer.parseInt(parts[1]);

        List<MessagePayload> transactions = deserializeBatch(batchData);

        System.out.println(this + " received LDCDecide for idx " + blockIndex);
        transactions.forEach(this::processDecidedTransaction);

        blockChain.add(blockChainIdx.getAndIncrement(), new Block(transactions));
        try {
            this.lock.lock();
            if (this.inConsensus.get()) {
                this.BLDConsensus.shutdown();
                this.inConsensus.set(false);
            }
        } finally {
            this.lock.unlock();
        }
    }

    private void processDecidedTransaction(MessagePayload payload) {
        Transaction t = Transaction.fromJson(config.gson, payload.getData());
        Message m = new Message(new MessagePayload(MessageType.TRANSACTION_CONFIRMED,
                ""), config.selfId);

        // Get the client ID from the sender address
        String clientIdStr = config.accountAddresses.entrySet().stream()
                .filter(e -> Objects.equals(e.getValue(), t.getSenderAddress()))
                .map(Map.Entry::getKey)
                .findFirst().orElse("");
        try {
            switch (payload.getType()) {
                case BALANCE_REQ -> {
                    if (Objects.equals(t.getCurrency(), "C")) {
                        Long istCoinBalance = contractExecutor.getISTCoinBalance(t.getRecieverAddress());
                        m.getPayload().setData(istCoinBalance.toString());
                    } else if (Objects.equals(t.getCurrency(), "D")) {
                        BigInteger depCoinBalance =
                                contractExecutor.getBalanceDepCoin(t.getRecieverAddress());
                        m.getPayload().setData(depCoinBalance.toString());
                    } else {
                        throw new InvalidOperationError("Invalid currency to get balance from");
                    }
                }
                case TRANSFER_REQ -> {
                    if (Objects.equals(t.getCurrency(), "C"))
                        contractExecutor.transferISTCoin(t.getSenderAddress(), t.getRecieverAddress(),
                                t.getAmount());
                    else {
                        contractExecutor.transferDepCoin(t.getSenderAddress(), t.getRecieverAddress(),
                                t.getAmount());
                    }
                }
                case TRANSFERFROM_REQ -> {
                    contractExecutor.transferFromISTCoin(t.getSenderAddress(), t.getRecieverAddress(),
                            t.getAmount());
                }
                case ADD_TO_BLACKLIST_REQ -> {
                    boolean success = contractExecutor.addToBlackList(t.getSenderAddress(),
                            t.getRecieverAddress());
                    m.getPayload().setData(String.valueOf(success));
                }
                case REMOVE_FROM_BLACKLIST_REQ -> {
                    boolean success = contractExecutor.removeFromBlackList(t.getSenderAddress(),
                            t.getRecieverAddress());
                    m.getPayload().setData(String.valueOf(success));
                }
                case IS_BLACKLISTED_REQ -> {
                    boolean success = contractExecutor.isBlackListed(t.getSenderAddress(),
                            t.getRecieverAddress());
                    m.getPayload().setData(String.valueOf(success));
                }
                default -> {
                    return;
                }
            }
        } catch (InvalidOperationError e) {
            m.getPayload().setType(MessageType.TRANSACTION_FAILED);
            m.getPayload().setData(e.getMessage());
        } catch (Exception e) {
            System.err.println("\n\nError in processDecidedTransaction " + e.getMessage());
        }

        try {
            int clientId = Integer.parseInt(clientIdStr);
            System.out.println(
                    this + " sending " + m.serialize(config.gson) + " to " + clientId);
            config.eventBus.publish(new SLSendEvent(m, clientId));
        } catch (NumberFormatException e) {
            System.err.println(
                    "Got invalid clientId from decided transaction" + payload.serialize(config.gson));
        }
    }

    private List<MessagePayload> deserializeBatch(String batchData) {
        return config.gson.fromJson(batchData,
                new TypeToken<List<MessagePayload>>() {
                }.getType());
    }

    private String serializeBatch(List<MessagePayload> batch) {
        return config.gson.toJson(new ArrayList<>(batch));
    }

    @Override
    public String toString() {
        return "DepChainMain{id=" + config.selfId + "}";
    }
}
