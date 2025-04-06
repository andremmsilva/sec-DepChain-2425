package depchain;

import com.google.gson.Gson;
import depchain.config.DepChainConfig;
import depchain.crypto.CryptoUtils;
import depchain.network.message.Message;
import depchain.network.message.MessagePayload;
import depchain.network.message.MessageType;
import depchain.network.message.Transaction;
import depchain.network.sl.SLDeliverEvent;
import depchain.network.sl.SLSendEvent;
import depchain.network.sl.StubbornLink;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DepChainMainTest {
    private static final Gson gson = new Gson();
    private final ArrayList<DepChainMain> instances = new ArrayList<>();
    private final List<DepChainConfig> configs = new ArrayList<>();

    @BeforeEach
    void setUp() throws InterruptedException {
        configs.clear();
        instances.clear();
        configs.add(new DepChainConfig(0, 0, "127.0.0.1"));
        configs.add(new DepChainConfig(1, configs.getFirst().getBasePort(), "127.0.0.1"));
        configs.add(new DepChainConfig(2, configs.getFirst().getBasePort(), "127.0.0.1"));
        configs.add(new DepChainConfig(3, configs.getFirst().getBasePort(), "127.0.0.1"));
        instances.add(new DepChainMain(configs.get(0)));
        instances.add(new DepChainMain(configs.get(1)));
        instances.add(new DepChainMain(configs.get(2)));
        instances.add(new DepChainMain(configs.get(3)));
        Thread.sleep(300);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        instances.forEach(DepChainMain::shutdown);
        Thread.sleep(200);
    }

    @Test
    @Timeout(10)
    public void testBalanceOnOwnerResultsInCorrectResult() throws InterruptedException {
        System.out.println("\ntestBalanceOnOwnerResultsInCorrectResult\n");
        CountDownLatch latch = new CountDownLatch(DepChainConfig.PROCESSES);
        DepChainConfig client = new DepChainConfig(10, configs.getFirst().getBasePort(), "127.0.0.1");
        StubbornLink clientSl = new StubbornLink(client);
        List<String> balancesReceived = Collections.synchronizedList(new ArrayList<>());
        Consumer<SLDeliverEvent> consumer = e -> {
            Message m = e.arg;
            if (m.getPayload().getType() == MessageType.TRANSACTION_CONFIRMED) {
                System.out.println("Got balance " + m.getPayload().getData());
                balancesReceived.add(m.getPayload().getData());
                latch.countDown();
            }
            assertEquals("100000000", m.getPayload().getData(),
                    String.format("Expected %s, got %s", "100000000", m.getPayload().getData()));
        };

        client.eventBus.register(SLDeliverEvent.class, consumer);

        Message balanceReq = new Message(new MessagePayload(MessageType.BALANCE_REQ,
                new Transaction(client.getSelfAccountAddress(), "C").serialize(gson)), client.selfId);
        balanceReq.setSignature(
                CryptoUtils.signMessage(balanceReq.getPayload().serialize(gson), client.getSelfPrivateKey()));
        for (int i = 0; i < DepChainConfig.PROCESSES; i++) {
            client.eventBus.publish(new SLSendEvent(balanceReq, i));
        }

        latch.await();
        client.eventBus.unregister(SLDeliverEvent.class, consumer);
        clientSl.close();
    }

    @Test
    @Timeout(10)
    public void testTransferFromOwnerResultsInCorrectISTCoinBalance() throws InterruptedException {
        System.out.println("\ntestDepChain\n");
        CountDownLatch transferLatch = new CountDownLatch(DepChainConfig.PROCESSES);
        CountDownLatch balanceLatch = new CountDownLatch(DepChainConfig.PROCESSES);
        DepChainConfig owner = new DepChainConfig(10, configs.getFirst().getBasePort(), "127.0.0.1");
        StubbornLink ownerSL = new StubbornLink(owner);
        DepChainConfig aliceClient = new DepChainConfig(11, configs.getFirst().getBasePort(), "127.0.0.1");
        List<String> balancesReceived = Collections.synchronizedList(new ArrayList<>());

        Consumer<SLDeliverEvent> transferConsumer = e -> {
            Message m = e.arg;
            if (m.getPayload().getType() == MessageType.TRANSACTION_CONFIRMED) {
                transferLatch.countDown();
            }
        };

        Consumer<SLDeliverEvent> balanceConsumer = e -> {
            Message m = e.arg;
            if (m.getPayload().getType() == MessageType.TRANSACTION_CONFIRMED) {
                System.out.println("Got balance " + m.getPayload().getData());
                balancesReceived.add(m.getPayload().getData());
                balanceLatch.countDown();
            }
            assertEquals("99999000", m.getPayload().getData(),
                    String.format("Expected %s, got %s", "99000000", m.getPayload().getData()));
        };

        // Send a transfer from owner to alice with 1000 ISTCoin
        owner.eventBus.register(SLDeliverEvent.class, transferConsumer);

        Message transferReq = new Message(new MessagePayload(MessageType.TRANSFER_REQ,
                new Transaction(owner.getSelfAccountAddress(), aliceClient.getSelfAccountAddress(),
                        1000L, "C").serialize(gson)), owner.selfId);
        transferReq.setSignature(
                CryptoUtils.signMessage(transferReq.getPayload().serialize(gson), owner.getSelfPrivateKey()));
        for (int i = 0; i < DepChainConfig.PROCESSES; i++) {
            owner.eventBus.publish(new SLSendEvent(transferReq, i));
        }

        transferLatch.await();
        owner.eventBus.unregister(SLDeliverEvent.class, transferConsumer);

        // Now, send a balance request from owner
        owner.eventBus.register(SLDeliverEvent.class, balanceConsumer);

        Message balanceReq = new Message(new MessagePayload(MessageType.BALANCE_REQ,
                new Transaction(owner.getSelfAccountAddress(), "C").serialize(gson)), owner.selfId);
        balanceReq.setSignature(
                CryptoUtils.signMessage(balanceReq.getPayload().serialize(gson), owner.getSelfPrivateKey()));
        for (int i = 0; i < DepChainConfig.PROCESSES; i++) {
            owner.eventBus.publish(new SLSendEvent(balanceReq, i));
        }

        balanceLatch.await();
        owner.eventBus.unregister(SLDeliverEvent.class, balanceConsumer);
        ownerSL.close();
    }

    @Test
    @Timeout(20)
    public void testConcurrentBalanceRequests() throws InterruptedException {
        System.out.println("\ntestConcurrentBalanceRequests\n");
        // Expecting two confirmations from all processes
        CountDownLatch latch = new CountDownLatch(2 * DepChainConfig.PROCESSES); //

        DepChainConfig ownerClient = new DepChainConfig(10, configs.getFirst().getBasePort(), "127.0.0.1");
        StubbornLink ownerSL = new StubbornLink(ownerClient);
        DepChainConfig aliceClient = new DepChainConfig(11, configs.getFirst().getBasePort(), "127.0.0.1");
        StubbornLink aliceSl = new StubbornLink(aliceClient);
        Consumer<SLDeliverEvent> consumer = e -> {
            Message m = e.arg;
            System.out.println("Got message from " + m.getSenderId() + " " + m.serialize(gson));
            if (m.getPayload().getType() == MessageType.TRANSACTION_CONFIRMED) {
                latch.countDown();
            }
        };

        ownerClient.eventBus.register(SLDeliverEvent.class, consumer);
        aliceClient.eventBus.register(SLDeliverEvent.class, consumer);

        // Two concurrent clients send balance requests
        Message ownerBalanceReq = new Message(new MessagePayload(MessageType.BALANCE_REQ,
                new Transaction(ownerClient.getSelfAccountAddress(), "C").serialize(gson)),
                ownerClient.selfId);
        ownerBalanceReq.setSignature(CryptoUtils.signMessage(ownerBalanceReq.getPayload().serialize(gson),
                ownerClient.getSelfPrivateKey()));
        Message aliceBalanceReq = new Message(new MessagePayload(MessageType.BALANCE_REQ,
                new Transaction(aliceClient.getSelfAccountAddress(), "C").serialize(gson)),
                aliceClient.selfId);
        aliceBalanceReq.setSignature(CryptoUtils.signMessage(aliceBalanceReq.getPayload().serialize(gson),
                aliceClient.getSelfPrivateKey()));

        for (int i = 0; i < DepChainConfig.PROCESSES; i++) {
            ownerClient.eventBus.publish(new SLSendEvent(ownerBalanceReq, i));
        }
        for (int i = 0; i < DepChainConfig.PROCESSES; i++) {
            aliceClient.eventBus.publish(new SLSendEvent(aliceBalanceReq, i));
        }

        boolean completed = latch.await(20, TimeUnit.SECONDS);
        assertTrue(completed, "Latch timed out waiting for APPEND_CONFIRMED");

        ownerClient.eventBus.unregister(SLDeliverEvent.class, consumer);
        aliceClient.eventBus.unregister(SLDeliverEvent.class, consumer);
        aliceSl.close();
        ownerSL.close();
    }
}
