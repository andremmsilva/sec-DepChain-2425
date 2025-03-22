package depchain.main;

import java.util.Scanner;

import depchain.config.DepChainConfig;
import depchain.consensus.cc.ConditionalCollect;
import depchain.events.EventBus;
import depchain.network.DepChainState;
import depchain.network.Message;
import depchain.network.apl.AuthenticatedPerfectLink;
import depchain.network.rw.ProposeEvent;
import depchain.network.rw.OldReadWrite;

public class DepChainMain {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: DepChainMain <selfId> <numProcesses>");
            System.exit(1);
        }

        // Parse our ID and the total number of processes
        int selfId = Integer.parseInt(args[0]);
        int numProcesses = Integer.parseInt(args[1]);

        System.out.println("Starting DepChain node with selfId=" + selfId
                + " out of " + numProcesses + " total processes");

        // 1) Create the DepChainConfig for this process.
        // This loads keys from /src/resources/keys/public_key_<id>.pem, etc.
        DepChainConfig config = new DepChainConfig(selfId);

        // If your code supports setting a dynamic # of processes, do so.
        // (In the posted code, PROCESSES is a static final.
        // If that can't be changed, you might rely on the default.)
        // DepChainConfig.PROCESSES = numProcesses; // if not final
        // Otherwise, you rely on the code's default (4).

        // 2) The config automatically includes an EventBus, or we can store it:
        EventBus eventBus = config.eventBus;

        // 3) Create the AuthenticatedPerfectLink or StubbornLink objects.
        // If your code expects a list, let's do a single link for ourselves.
        // (In a real system, you might create one link to each remote peer.)
        AuthenticatedPerfectLink apl = new AuthenticatedPerfectLink(config);

        // 4) Create DepChainState
        DepChainState depChainState = new DepChainState(config, apl);

        // 5) Create a ConditionalCollect. Usually you pass a predicate
        // that returns true if you have enough messages to proceed.
        // We'll do a trivial version or a real condition based on N/f.
        ConditionalCollect cc = new ConditionalCollect(config, messages -> {
            // e.g. if you need at least N-f states:
            int n = numProcesses; // or DepChainConfig.PROCESSES
            int f = DepChainConfig.FAIL_BOUND;
            return messages.size() >= (n - f);
        });

        // 6) Create the read/write logic
        OldReadWrite rw = new OldReadWrite(depChainState);

        // 7) If this is the leader (ID=0 by default in DepChainConfig),
        // we can accept user input to propose transactions:
        if (selfId == DepChainConfig.LEADER) {
            System.out.println("I am the LEADER. Waiting for client transactions...");
            try (Scanner scanner = new Scanner(System.in)) {
                while (true) {
                    System.out.print("Leader> ");
                    String line = scanner.nextLine().trim();
                    if (line.equalsIgnoreCase("quit")) {
                        System.out.println("Shutting down leader...");
                        break;
                    } else if (line.startsWith("propose ")) {
                        // Process "propose <value>"
                        String proposal = line.substring("propose ".length()).trim();
                        Message msg = new Message(proposal, selfId);
                        eventBus.publish(new ProposeEvent(msg));
                        System.out.println("Leader proposed: " + proposal);
                    }
                }
            }
        } else {
            // NON-LEADER: Stay alive waiting for APLDeliverEvent
            System.out.println("Process " + selfId + " started. Waiting for messages...");

            synchronized (DepChainMain.class) {
                try {
                    DepChainMain.class.wait(); // Prevent exit
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        // 8) Once we exit the main loop (e.g. 'quit' on the leader), close everything
        System.out.println("Process " + selfId + " is shutting down watchers, etc.");
        rw.close(); // stops watchWrittenMessages and watchAcceptedMessages
        cc.close(); // stops watchCollectedMessages
        apl.close(); // stops the link
        eventBus.shutdown(); // stops the event bus

        System.out.println("Process " + selfId + " exited.");
    }
}
