package depchain.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WriteMethods {
    private int N_PROCESSES;
    private int myId;
    private List<AuthenticatedPerfectLink> links;

    private Map<Integer, Object> written = new HashMap<>();
    private Map<Integer, Object> accepted = new HashMap<>();

    public WriteMethods(int N_PROCESSES, int myId, List<AuthenticatedPerfectLink> links) {
        this.N_PROCESSES = N_PROCESSES;
        this.myId = myId;
        this.links = links;
    }

    // Write method to broadcast state to all processes
    public void write(State state) {
        for (AuthenticatedPerfectLink link : links) {
            for (int port : link.getPorts()) {
                link.sendMessage(port, port, new Message("WRITE", state));
            }
        }
    }

    public void deliverWrite(AuthenticatedPerfectLink link, int p, State state) {
        accepted.put(p, state);
        if (Collections.frequency(new ArrayList<>(accepted.values()), state) > ((N_PROCESSES + (N_PROCESSES / 3)/2))) {
            Object valts = "ets"; // replace with actual logic
            accepted.clear();
            broadcastAccept(state);
        }
    }


}
