package depchain.network.rw;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import depchain.classes.State;
import depchain.classes.Tuple;
import depchain.config.DepChainConfig;
import depchain.consensus.cc.CCCollectedEvent;
import depchain.consensus.cc.CCInputEvent;
import depchain.events.EventBus;
import depchain.network.DepChainState;
import depchain.network.Message;
import depchain.network.apl.APLDeliverEvent;
import depchain.network.apl.APLSendEvent;

public class ReadWrite {
    private DepChainState serverState; // Holds current epoch, etc.
    // For demonstration we’ll keep track of which value each process has “written” or “accepted.”
    private final String[] written = new String[DepChainConfig.PROCESSES];
    private final String[] accepted = new String[DepChainConfig.PROCESSES];
    // For concurrency control in watchers
    private final Lock lock = new ReentrantLock();
    private final Condition newWrite = lock.newCondition();
    private final Condition newAccept = lock.newCondition();
    private final AtomicBoolean running = new AtomicBoolean(true);

    // We'll run two watcher loops (one for write, one for accept)
    private final ExecutorService executor = Executors.newFixedThreadPool(2);


    private final EventBus eventBus;
    
    public ReadWrite(DepChainState serverState) {
        this.serverState = serverState;
        this.eventBus = serverState.getConfig().eventBus;
    
        // Initialize local arrays
        for (int i = 0; i < DepChainConfig.PROCESSES; i++) {
            written[i] = null;
            accepted[i] = null;
        }
    
        // Register event handlers
        this.eventBus.register(ProposeEvent.class, onProposal);
        this.eventBus.register(APLDeliverEvent.class, onAPLDelivery);
        this.eventBus.register(CCCollectedEvent.class, onCCCollected);
    
        // Start our watcher loops
        this.executor.submit(this::watchWrittenMessages);
        this.executor.submit(this::watchAcceptedMessages);
    }

    /**
     *  Handle the “bep Propose | v” event. Only the leader should act here.
     */
    private final Consumer<ProposeEvent> onProposal = (event) -> {
        System.out.println("On ProposeEvent on RW Class" + event.arg.toString());
        String proposedValue = event.arg.getPayload(); 
        int selfId = serverState.getConfig().selfId;

        if (!Objects.equals(selfId, DepChainConfig.LEADER)) {
            // Only the leader does the propose action
            return;
        }

        // If we have not yet stored any "last-round" value in our internal DepChainState,
        // set it to (epoch, proposedValue). 
        if (serverState.getState().getLastRound() == null || 
            serverState.getState().getLastRound().getValue() == null) {
            // Suppose we track "timestamp" = current epoch, "value" = proposedValue
            serverState.getState().setLastRound(new Tuple(serverState.getEpoch(), proposedValue));
        }

        // Then broadcast a "READ" request to all processes
        broadcast("READ");
    };

    /**
     *  Handle any messages delivered from AuthenticatedPerfectLink (APL). 
     *  For example: if the leader sent "READ", processes respond with "STATE–..." 
     *  or if the leader eventually sends "WRITE–...", we handle that, etc.
     */
    private final Consumer<APLDeliverEvent> onAPLDelivery = (event) -> {
        System.out.println("On ALPDeliverEvent on RW Class " + event.arg.toString());
        Message msg = event.arg;
        String payload = msg.getPayload();
        int sender = msg.getSenderId();

        // Example: if we receive "[READ]" from the leader, we respond by sending "STATE–..."
        if ("READ".equals(payload) && sender == DepChainConfig.LEADER) {
            sendStateToConditionalCollect();
        }
        else if (payload.startsWith("WRITE###")) {
            String v = payload.substring("WRITE###".length());
    
            lock.lock();
            try {
                written[sender] = v;
                // signal the write watcher
                newWrite.signal();
            } finally {
                lock.unlock();
            }
        }
    
        // If the leader (or anyone) broadcasts "ACCEPT###v", store it and signal the watcher
        else if (payload.startsWith("ACCEPT###")) {
            String v = payload.substring("ACCEPT###".length());
    
            lock.lock();
            try {
                accepted[sender] = v;
                // signal the accept watcher
                newAccept.signal();
            } finally {
                lock.unlock();
            }
        }
    };

    /**
     *  Publish a “STATE###...” message into the CC pipeline (ConditionalCollect). 
     *  This is typically done when we get a "READ" from the leader.
     */
    private void sendStateToConditionalCollect() {
        // Build a message that includes some notion of your local state
        // E.g., "STATE###<the entire state>" or "STATE###<epoch, lastAcceptedVal>"
        String stateString = serverState.getState().toString();

        Message m = new Message(stateString, serverState.getConfig().selfId);
        // Feed it into the ConditionalCollect’s input. 
        // (ConditionalCollect is listening for CCInputEvent.)
        eventBus.publish(new CCInputEvent(m));  
    }

    /**
     *  When the CC has collected enough "STATE-..." messages from processes, 
     *  it publishes CCCollectedEvent. We can examine the set of states, 
     *  pick a final value to write, and then broadcast a "WRITE–value".
     */
    private final Consumer<CCCollectedEvent> onCCCollected = (event) -> {
        System.out.println("On CCCollectedEvent RW class " + event.arg.toString());
        List<Message> collectedStates = event.arg;
        List<State> allStates = new ArrayList<>();

        for( Message m : collectedStates){
            State st = State.fromStr(m.getPayload());
            allStates.add(st);
        }

        System.out.println(allStates.toString());
    
        // We'll track the best (ts, val) we see among states with ts > 0
        int bestTs = 0; 
        String bestVal = null;
    
        // 1) Look through all collected states for the highest ts>0
        for (State st : allStates) {
            if (st != null && st.getLastRound() != null) {
                int ts = st.getLastRound().getTimestamp();
                String val = st.getLastRound().getValue();
                // Only consider it if ts>0 and the value is non-empty
                if (ts > bestTs && ts > 0 && val != null && !val.isEmpty()) {
                    bestTs = ts;
                    bestVal = val;
                }
            }
        }

        if(bestVal != null){
            for(int i = 0; i <= bestTs; i++){
                int appearances=0;
                for( State st: allStates){
                    Set<Tuple> ws = st.getWriteSet();
                    for(Tuple tuple: ws){
                        if(tuple.getValue().equals(bestVal) && tuple.getTimestamp() == i)
                            appearances++;
                    }
                }
                if(appearances <= (DepChainConfig.FAIL_BOUND+1))
                    bestVal = null;
            }
        }
    
        // 2) If we didn't find any valid state with ts>0, and appearing in f+1 writesets fallback to the leader's value
        if (bestVal == null) {
            for (Message m : collectedStates) {
                if (m.getSenderId() == DepChainConfig.LEADER) {
                    State st = State.fromStr(m.getPayload());
                    if (st != null && st.getLastRound() != null) {
                        bestVal = st.getLastRound().getValue();
                        break;
                    }
                }
            }
        }
        System.out.println("Bestval: " + bestVal);
    
        // 3) If we still have a value, broadcast (epoch, bestVal)
        if (bestVal != null) {
            int currentEpoch = serverState.getEpoch();  // an integer
            Tuple tuple = new Tuple(currentEpoch + 1, bestVal);
            broadcast("WRITE###" + tuple);
            System.out.println(
                "Process " + serverState.getConfig().selfId
                + " broadcast WRITE with (epoch=" + currentEpoch + ", val=" + bestVal + ")"
            );
        }
    };     

    private void watchWrittenMessages() {
        while (running.get()) {
            lock.lock();
            try {
                // Wait until we have at least one "WRITE" change
                newWrite.await();  // or do a while-check if needed

                // Now that we woke up, scan for a value v that has a majority
                checkWrittenQuorum();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Interrupted watchWrittenMessages");
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * This checks if any value v in 'written[]' has more than (n+f)/2 processes.
     * If so, we broadcast "ACCEPT###v" and reset 'written[]'.
     */
    private void checkWrittenQuorum() {
        int n = DepChainConfig.PROCESSES;
        int f = DepChainConfig.FAIL_BOUND;

        // Count how many times each v appears in 'written[]'
        // (We can use a map from string -> integer)
        Map<String, Integer> counts = new HashMap<>();
        for (int p = 0; p < n; p++) {
            String v = written[p];
            if (v != null) {
                counts.put(v, counts.getOrDefault(v, 0) + 1);
            }
        }

        // Find if any v has more than (n+f)/2
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            String v = e.getKey();
            int c = e.getValue();
            if (c > (n + f) / 2) {
                // Quorum found => broadcast ACCEPT, reset written[]
                Arrays.fill(written, null);

                broadcast("ACCEPT" + v);
                System.out.println("Process " + serverState.getConfig().selfId
                                + " broadcasting ACCEPT###" + v);
                return; // Only pick one value, or break if you prefer
            }
        }
    }

    private void watchAcceptedMessages() {
        while (running.get()) {
            lock.lock();
            try {
                // Wait until we have at least one "ACCEPT" change
                newAccept.await();
    
                // Check if some value has a majority in 'accepted[]'
                checkAcceptedQuorum();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Interrupted watchAcceptedMessages");
            } finally {
                lock.unlock();
            }
        }
    }
    
    /**
     * If any value v appears in 'accepted[]' more than (n+f)/2 times, we decide it.
     */
    private void checkAcceptedQuorum() {
        int n = DepChainConfig.PROCESSES;
        int f = DepChainConfig.FAIL_BOUND;
    
        Map<String, Integer> counts = new HashMap<>();
        for (int p = 0; p < n; p++) {
            String v = accepted[p];
            if (v != null) {
                counts.put(v, counts.getOrDefault(v, 0) + 1);
            }
        }
    
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            String v = e.getKey();
            int c = e.getValue();
            if (c > (n + f) / 2) {
                // We decide v
                Arrays.fill(accepted, null);
    
                decideValue(v);
                return;
            }
        }
    }
    
    private void decideValue(String v) {
        System.out.println("Process " + serverState.getConfig().selfId
                           + " DECIDES value: " + v);
        this.serverState.getState().setLastRound(new Tuple(this.serverState.getEpoch(), v));
        //eventBus.publish(new TransactionConfirmedEvent(v, success));
        // If you want, publish an event so other code can see we decided:
        // eventBus.publish(new DecideEvent(v));
    }
    

    /**
     *  Convenience method: broadcast a message to all processes 
     *  using AuthenticatedPerfectLink (via the event bus).
     */
    private void broadcast(String msgPayload) {
        for (int targetId = 0; targetId < DepChainConfig.PROCESSES; targetId++) {
            Message m = new Message(msgPayload, serverState.getConfig().selfId);
            eventBus.publish(new APLSendEvent(m, targetId));
        }
    }

    /**
     *  Clean up (unregister) if/when needed. 
     *  For example if you close or tear down your system.
     */
    public void close() {
        // Stop watchers
        running.set(false);
        lock.lock();
        try {
            newWrite.signalAll();
            newAccept.signalAll();
        } finally {
            lock.unlock();
        }
        executor.shutdownNow();
    
        // Unregister event handlers
        eventBus.unregister(ProposeEvent.class, onProposal);
        eventBus.unregister(APLDeliverEvent.class, onAPLDelivery);
        eventBus.unregister(CCCollectedEvent.class, onCCCollected);
    }
    
}
