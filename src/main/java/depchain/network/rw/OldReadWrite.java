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

public class OldReadWrite {
    private DepChainState serverState; // Holds current epoch, etc.
    // For demonstration we'll keep track of which value each process has 'written' or 'accepted.'
    private final String[] written = new String[DepChainConfig.PROCESSES];
    private final String[] accepted = new String[DepChainConfig.PROCESSES];
    // For concurrency control in watchers
    private final Lock lock = new ReentrantLock();
    private final Condition newWrite = lock.newCondition();
    private final Condition newAccept = lock.newCondition();
    private final AtomicBoolean running = new AtomicBoolean(true);

    private boolean readFlag = false;
    

    // We'll run two watcher loops (one for write, one for accept)
    private final ExecutorService executor = Executors.newFixedThreadPool(4);


    private final EventBus eventBus;
    
    public OldReadWrite(DepChainState serverState) {
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
        this.executor.submit(this::timer);
    }

    /**
     *  Handle the 'bep Propose | v' event. Only the leader should act here.
     */
    private final Consumer<ProposeEvent> onProposal = (event) -> {
        System.out.println("On ProposeEvent on RW Class" + event.arg.getPayload());
        String proposedValue = event.arg.getPayload(); 
        int selfId = serverState.getConfig().selfId;

        if (!Objects.equals(selfId, DepChainConfig.LEADER)) {
            // Only the leader does the propose action
            return;
        }

        // If we have not yet stored any "last-round" value in our internal DepChainState,
        // set it to (epoch, proposedValue). 
        if (serverState.getState().getLastRound().getValue() == "") {
            // Suppose we track "timestamp" = current epoch, "value" = proposedValue
            serverState.getState().setLastRound(new Tuple(serverState.getEpoch(), proposedValue));
        }

        // Then broadcast a "READ" request to all processes
        broadcast("READ");
    };

    /**
     *  Handle any messages delivered from AuthenticatedPerfectLink (APL). 
     *  For example: if the leader sent "READ", processes respond with "STATE-..." 
     *  or if the leader eventually sends "WRITE-...", we handle that, etc.
     */
    private final Consumer<APLDeliverEvent> onAPLDelivery = (event) -> {
        Message msg = event.arg;
        System.out.println("On ALPDeliverEvent on RW: " + msg.getPayload());
        String payload = msg.getPayload();
        int sender = msg.getSenderId();
        if (sender == DepChainConfig.LEADER) {
            this.readFlag = true;
        }
        if (payload.startsWith("TRANSACTION###")) {

        }


        // Example: if we receive "[READ]" from the leader, we respond by sending "STATE-..."
        if ("READ".equals(payload) && sender == DepChainConfig.LEADER) {
            System.out.println("Got Read Message");
            sendStateToConditionalCollect();
        }
        else if (payload.startsWith("WRITE###")) {
            String v = payload.substring("WRITE###".length());
            System.out.println("Got Write Message " + v + " from " + sender);
        
            lock.lock();
            try {
                // Store the write
                written[sender] = v;
                // Wake up BOTH watchers
                newWrite.signalAll();
            } finally {
                lock.unlock();
            }
        }
        
    
        // If the leader (or anyone) broadcasts "ACCEPT###v", store it and signal the watcher
        else if (payload.startsWith("ACCEPT###")) {
            String v = payload.substring("ACCEPT###".length());
            System.out.println("Got accept Message " + v + " from " + sender);
        
            lock.lock();
            try {
                // Store the acceptance
                accepted[sender] = v;
                // Wake up BOTH watchers
                newAccept.signalAll();
            } finally {
                lock.unlock();
            }
        }
        

        else if (payload.startsWith("COLLECTED###")) {
            String v = payload.substring("COLLECTED###".length());
            System.out.println("Got Collected Message: " + v);

        }
    };

    /**
     *  Publish a 'STATE###...' message into the CC pipeline (ConditionalCollect). 
     *  This is typically done when we get a "READ" from the leader.
     */
    private void sendStateToConditionalCollect() {
        // Build a message that includes some notion of your local state
        // E.g., "STATE###<the entire state>" or "STATE###<epoch, lastAcceptedVal>"
        String stateString = serverState.getState().toString();

        Message m = new Message(stateString, serverState.getConfig().selfId);
        // Feed it into the ConditionalCollect's input. 
        // (ConditionalCollect is listening for CCInputEvent.)
        eventBus.publish(new CCInputEvent(m));
    }

    /**
     *  When the CC has collected enough "STATE-..." messages from processes, 
     *  it publishes CCCollectedEvent. We can examine the set of states, 
     *  pick a final value to write, and then broadcast a "WRITE-value".
     */

    private final Consumer<CCCollectedEvent> onCCCollected = (event) -> {
    List<Message> collectedStates = event.arg;
    List<State> allStates = new ArrayList<>();
    for (Message m : collectedStates) {
        State st = State.fromStr(m.getPayload());
        if (st != null) {
            allStates.add(st);
        }
    }

    int f = DepChainConfig.FAIL_BOUND;

    // ----------------------------------------------------------------
    // 1) Gather *all* distinct (ts, val) pairs from each state's lastRound,
    //    but only if ts>0 & val != "".
    // ----------------------------------------------------------------
    List<Tuple> candidates = new ArrayList<>();
    for (State st : allStates) {
        Tuple lr = st.getLastRound();
        if (lr != null && lr.getTimestamp() > 0 && !lr.getValue().isEmpty()) {
            candidates.add(lr);
        }
    }

    // 2) Among all candidates, find the one with the largest ts that also
    //    appears in >= f+1 distinct writesets. We store (bestTs, bestVal).
    int bestTs = -1;
    String bestVal = null;

    // We might have duplicates in 'candidates'. That's okay.
    // We'll check them all, ignoring duplicates. 
    // If you want to ignore duplicates up front, you could use a set.
    for (Tuple candidate : candidates) {
        int ts = candidate.getTimestamp();
        String val = candidate.getValue();

        // Count how many states have 'val' in their writeSet
        int count = 0;
        for (State st : allStates) {
            boolean hasVal = false;
            Set<Tuple> ws = st.getWriteSet();
            synchronized (ws) {
                for (Tuple t : ws) {
                    if (val.equals(t.getValue())) {
                        hasVal = true;
                        break; // only need to see if val appears once
                    }
                }
            }
            if (hasVal) {
                count++;
            }
        }
        // If it meets the f+1 condition, see if it's the largest ts so far
        if (count >= (f + 1) && ts > bestTs) {
            bestTs = ts;
            bestVal = val;
        }
    }

    // ----------------------------------------------------------------
    // 3) If still none found, fallback to the leader's value
    // ----------------------------------------------------------------
    if (bestVal == null) {
        for (Message m : collectedStates) {
            if (m.getSenderId() == DepChainConfig.LEADER) {
                State leaderSt = State.fromStr(m.getPayload());
                if (leaderSt != null && leaderSt.getLastRound() != null) {
                    bestVal = leaderSt.getLastRound().getValue();
                    break;
                }
            }
        }
        System.out.println("No (ts, val) met the f+1 condition. Fallback to leader's: " + bestVal);
    }

    // ----------------------------------------------------------------
    // 4) If bestVal is still non-empty, do final broadcast "WRITE###(ts,val)"
    // ----------------------------------------------------------------
    if (bestVal != null && !bestVal.isEmpty()) {
        int currentEpoch = serverState.getEpoch();  // an integer
        Tuple newTuple = new Tuple(currentEpoch + 1, bestVal);
        final String chosenVal = bestVal;

        // (Optional) remove old versions of bestVal from local writeSet
        Set<Tuple> ws = serverState.getState().getWriteSet();
        synchronized (ws) {
            ws.removeIf(t -> t.getValue().equals(chosenVal));
        }

        serverState.getState().addToWriteSet(newTuple);

        broadcast("WRITE###" + newTuple);
        System.out.println("Process " + serverState.getConfig().selfId
            + " broadcast WRITE with (epoch=" + currentEpoch
            + ", val=" + bestVal + ")");
    } else {
        System.out.println("No suitable bestVal after fallback. Skipping WRITE.");
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

        System.out.println("checking written counts: " + counts.toString());

        // Find if any v has more than (n+f)/2
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            String v = e.getKey();
            int c = e.getValue();
            if (c > (n + f) / 2) {
                // Quorum found => broadcast ACCEPT, reset written[]
                Arrays.fill(written, null);
                broadcast("ACCEPT###" + v);
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

        System.out.println("checking accepted counts: " + counts.toString());

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
    
    private synchronized void timer() {
        while (true) {
            try {
                wait(60000);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            if (!this.readFlag)
                System.out.println("Should Change the leader");
            this.readFlag = false;
        }
    }
    
    private void decideValue(String v) {
        System.out.println("Process " + serverState.getConfig().selfId
                           + " DECIDES value: " + v);
        this.serverState.getState().setLastRound(new Tuple(this.serverState.getEpoch(), v));
        Message message = new Message("CONFIRMED###" + v, DepChainConfig.LEADER);
        eventBus.publish(new APLSendEvent(message,10 ));
    }
    

    /**
     *  Convenience method: broadcast a message to all processes 
     *  using AuthenticatedPerfectLink (via the event bus).
     */
    private void broadcast(String msgPayload) {
        System.out.println("Broadcasting: " + msgPayload);
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
