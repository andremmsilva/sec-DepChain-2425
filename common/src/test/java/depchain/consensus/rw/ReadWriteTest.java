// package depchain.consensus.rw;

// import com.google.gson.Gson;
// import depchain.config.DepChainConfig;
// import depchain.consensus.cc.CCCollectedEvent;
// import depchain.consensus.utils.Broadcast;
// import depchain.network.apl.APLDeliverEvent;
// import depchain.network.apl.APLSendEvent;
// import depchain.network.apl.AuthenticatedPerfectLink;
// import depchain.network.message.Message;
// import depchain.network.message.MessagePayload;
// import depchain.network.message.MessageType;
// import org.junit.jupiter.api.AfterEach;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.Timeout;

// import java.util.ArrayList;
// import java.util.Collections;
// import java.util.List;
// import java.util.concurrent.CountDownLatch;
// import java.util.concurrent.locks.Lock;
// import java.util.concurrent.locks.ReentrantLock;
// import java.util.function.Consumer;

// import static org.junit.jupiter.api.Assertions.assertEquals;
// import static org.junit.jupiter.api.Assertions.assertTrue;

// public class ReadWriteTest {
//     private static final Gson gson = new Gson();
//     private final ArrayList<DepChainConfig> configs = new ArrayList<>(DepChainConfig.PROCESSES);
//     private final ArrayList<AuthenticatedPerfectLink> links = new ArrayList<>(DepChainConfig.PROCESSES);
//     private final ArrayList<ReadWrite> rws = new ArrayList<>(DepChainConfig.PROCESSES);

//     @BeforeEach
//     void setUp() throws InterruptedException {
//         configs.clear();
//         links.clear();
//         rws.clear();
//         configs.add(new DepChainConfig(0, 0, "127.0.0.1"));
//         configs.add(new DepChainConfig(1, configs.getFirst().getBasePort(), "127.0.0.1"));
//         configs.add(new DepChainConfig(2, configs.getFirst().getBasePort(), "127.0.0.1"));
//         configs.add(new DepChainConfig(3, configs.getFirst().getBasePort(), "127.0.0.1"));
//         links.add(new AuthenticatedPerfectLink(configs.get(0)));
//         links.add(new AuthenticatedPerfectLink(configs.get(1)));
//         links.add(new AuthenticatedPerfectLink(configs.get(2)));
//         links.add(new AuthenticatedPerfectLink(configs.get(3)));
//         rws.add(new ReadWrite(configs.get(0)));
//         rws.add(new ReadWrite(configs.get(1)));
//         rws.add(new ReadWrite(configs.get(2)));
//         rws.add(new ReadWrite(configs.get(3)));
//         // Cheat to make handshakes work :/
//         Thread.sleep(200);
//         Broadcast.all(new MessagePayload(null, "1 msg"), configs.getFirst());
//         Thread.sleep(300);
//     }

//     @AfterEach
//     void tearDown() throws InterruptedException {
//         links.forEach(AuthenticatedPerfectLink::close);
//         rws.forEach(ReadWrite::close);
//         Thread.sleep(300);
//     }

//     @Test
//     @Timeout(10)
//     void testProposeResultsInBroadcastingRead() throws InterruptedException {
//         System.out.println("\ntestProposeResultsInBroadcastingRead()\n");
//         CountDownLatch latch = new CountDownLatch(DepChainConfig.PROCESSES);
//         List<Message> received = Collections.synchronizedList(new ArrayList<>());
//         Consumer<APLDeliverEvent> consumer = e -> {
//             if (e.arg.getPayload().getType().equals(MessageType.READ)) {
//                 received.add(e.arg);
//                 latch.countDown();
//             }
//         };
//         configs.forEach(config -> {
//             // Listen for APL Delivers on all processes
//             config.eventBus.register(APLDeliverEvent.class, consumer);
//         });

//         // Leader proposes "1"
//         configs.getFirst().eventBus.publish(new RWProposeEvent("1"));

//         latch.await();

//         assertEquals((int) DepChainConfig.PROCESSES, received.size());

//         // Teardown
//         configs.forEach(config -> config.eventBus.unregister(APLDeliverEvent.class, consumer));
//     }

//     @Test
//     @Timeout(10)
//     void testReadMessageResultsInCCInput() throws InterruptedException {
//         System.out.println("\ntestReadMessageResultsInCCInput()\n");
//         CountDownLatch latch = new CountDownLatch(1);
//         List<Message> received = Collections.synchronizedList(new ArrayList<>());
//         Consumer<APLDeliverEvent> consumer = e -> {
//             received.add(e.arg);
//             latch.countDown();
//         };

//         configs.getFirst().eventBus.register(APLDeliverEvent.class, consumer);

//         // Leader sends "READ" to 1
//         // 1 should then send it's state to the leader, via conditional collect
//         configs.getFirst().eventBus.publish(new APLSendEvent(new Message(new MessagePayload(MessageType.READ, ""), 0)
//                 , 1));

//         latch.await();

//         assertEquals(1, received.size());
//         assertEquals(new MessagePayload(MessageType.SEND, rws.get(1).getEpochState().serialize(gson)).serialize(gson),
//                 received.getFirst().getPayload().serialize(gson));

//         // Teardown
//         configs.getFirst().eventBus.unregister(APLDeliverEvent.class, consumer);
//     }

//     @Test
//     @Timeout(10)
//     void testProposeResultsInCollectedEvents() throws InterruptedException {
//         System.out.println("\ntestProposeResultsInCollectedEvents()\n");
//         CountDownLatch latch = new CountDownLatch(DepChainConfig.PROCESSES);
//         List<Message> received = Collections.synchronizedList(new ArrayList<>());
//         Consumer<CCCollectedEvent> consumer = e -> {
//             received.addAll(e.arg);
//             latch.countDown();
//         };
//         configs.forEach(config -> {
//             // Listen for CCCollected on all processes
//             config.eventBus.register(CCCollectedEvent.class, consumer);
//         });

//         // Leader proposes "1"
//         configs.getFirst().eventBus.publish(new RWProposeEvent("1"));

//         latch.await();

//         // We expect at least 3 messages (states) per process that published CCCollected
//         assertTrue(DepChainConfig.PROCESSES * (DepChainConfig.PROCESSES - 1) <= received.size(), String.format(
//                 "Expected %s, got %s", DepChainConfig.PROCESSES * (DepChainConfig.PROCESSES - 1), received.size()));

//         // Teardown
//         configs.forEach(config -> config.eventBus.unregister(CCCollectedEvent.class, consumer));
//     }

//     @Test
//     @Timeout(10)
//     void testOnCCCollectedUnbound() throws InterruptedException {
//         System.out.println("\ntestOnCCCollectedUnbound()\n");
//         CountDownLatch latch =
//                 new CountDownLatch(DepChainConfig.PROCESSES * (DepChainConfig.PROCESSES - DepChainConfig.FAIL_BOUND));
//         List<Message> received = new ArrayList<>();
//         Lock lock = new ReentrantLock();
//         Consumer<APLDeliverEvent> aplDeliverConsumer = e -> {
//             if (e.arg.getPayload().getType().equals(MessageType.WRITE)) {
//                 try {
//                     lock.lock();
//                     received.add(e.arg);
//                 } finally {
//                     lock.unlock();
//                 }
//                 latch.countDown();
//             }
//         };
//         configs.forEach(config -> {
//             // Listen for APL Delivers on all processes to confirm WRITE
//             config.eventBus.register(APLDeliverEvent.class, aplDeliverConsumer);
//         });

//         // Leader proposes "1"
//         configs.getFirst().eventBus.publish(new RWProposeEvent("1"));

//         latch.await();

//         System.out.println(received.size());
//         try {
//             lock.lock();
//             assertTrue(
//                     received.stream().allMatch(m -> m.getPayload().getData().equals("1")));
//         } finally {
//             lock.unlock();
//         }

//         // Teardown
//         configs.forEach(config -> config.eventBus.unregister(APLDeliverEvent.class, aplDeliverConsumer));
//     }

//     @Test
//     @Timeout(20)
//     public void testFullIteration() throws InterruptedException {
//         System.out.println("\ntestFullIteration()\n");
//         CountDownLatch latch = new CountDownLatch(DepChainConfig.PROCESSES);
//         List<String> received = Collections.synchronizedList(new ArrayList<>());
//         Consumer<RWDecideEvent> consumer = e -> {
//             System.out.println("Received Decide " + e.arg);
//             received.add(e.arg);
//             latch.countDown();
//         };

//         configs.forEach(config -> {
//             config.eventBus.register(RWDecideEvent.class, consumer);
//         });

//         configs.getFirst().eventBus.publish(new RWProposeEvent("1"));

//         latch.await();

//         assertEquals((int) DepChainConfig.PROCESSES, received.size());

//         configs.forEach(config -> config.eventBus.unregister(RWDecideEvent.class, consumer));
//     }
// }
