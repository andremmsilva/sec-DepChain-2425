package depchain.network;

import depchain.config.DepChainConfig;
import depchain.crypto.CryptoUtils;

import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class SignedConditionalCollect {
//    // Collected messages and their corresponding signatures (indexed by process index)
//    private final Message[] messages;
//    private boolean collected = false;
//
//    // Representation of UNDEFINED entry (using null)
//    public static final Object UNDEFINED = null;
//
//    // Output predicate interface; C should return true when the collected vector is acceptable.
//    public interface OutputPredicate {
//        boolean evaluate(Message[] messages);
//    }
//
//    private final OutputPredicate predicate;
//
//    // Our authenticated links abstraction instance – provides methods for sending messages.
//    private final AuthenticatedPerfectLink authLink;
//
//    // A blocking queue to simulate delivery of the final collected vector event.
//    private final BlockingQueue<Object[]> outputQueue = new LinkedBlockingQueue<>();
//
//    public SignedConditionalCollect(AuthenticatedPerfectLink authLink) {
//        this.predicate = messages -> {
//            for(Message message : messages) {
//                // Todo check signature of message with other public key
//            }
//            return true;
//        };
//        this.authLink = authLink;
//        this.messages = new Message[DepChainConfig.PROCESSES];
//        Arrays.fill(this.messages, null);
//    }
//
//    // Method to be invoked when this process inputs its message (event: <cc, Input | m>)
//    public void input(Message m) {
//        // For signing, we form a string "cc‖self‖INPUT‖m". Here we assume the process identifier is provided by authLink.
//        authLink.sendMessage(m.getDestination(), m.getDestinationPort(), m.getMessage());
//    }
//
//    // Handler to be called at the leader when a SEND message is delivered from process p.
//    // (Simulating event: <al, Deliver | p, [SEND, m, σ]>)
//    public void onDeliver(String p, String payload) throws Exception {
//        // Only leader processes execute this handler.
//        if (!authLink.getSelfId().equals(leaderId)) {
//            return;
//        }
//        if (!payload.startsWith("SEND|")) return;
//        String[] parts = payload.split("\\|", 3);
//        if (parts.length < 3) return;
//        String mStr = parts[1];
//        String sigBase64 = parts[2];
//        byte[] sig = CryptoUtils.fromBase64(sigBase64);
//        // Verify the signature for input from process p.
//        String expected = "cc" + p + "INPUT" + mStr;
//        if (!CryptoUtils.verifySignature(expected, sig, CryptoUtils.getPublicKey(p))) {
//            return;
//        }
//        // For simplicity, assume process p's id is an integer string (e.g., "0", "1", ...).
//        int index;
//        try {
//            index = Integer.parseInt(p);
//        } catch (NumberFormatException e) {
//            return;
//        }
//        messages[index] = mStr;
//        signatures[index] = sig;
//
//        // If at least N - f messages are defined and the predicate holds, then broadcast the collected vector.
//        if (countDefined(messages) >= (N - f) && predicate.evaluate(messages)) {
//            String collectedPayload = "COLLECTED|" + serializeVector(messages) + "|" + serializeSignatures(signatures);
//            for (int i = 0; i < N; i++) {
//                authLink.sendMessage(String.valueOf(i), collectedPayload);
//            }
//            // Reset the leader's storage for the next collection round.
//            Arrays.fill(messages, UNDEFINED);
//            for (int i = 0; i < N; i++) {
//                signatures[i] = null;
//            }
//        }
//    }
//
//    // Handler for non-leader processes to process the COLLECTED message (event: <al, Deliver | leader, [COLLECTED, M, Σ]>)
//    public void onCollectedDeliver(String from, String payload) throws Exception {
//        if (!payload.startsWith("COLLECTED|")) return;
//        String[] parts = payload.split("\\|", 3);
//        if (parts.length < 3) return;
//        String MSerialized = parts[1];
//        String sigmaSerialized = parts[2];
//        Object[] M = deserializeVector(MSerialized);
//        byte[][] sigma = deserializeSignatures(sigmaSerialized);
//        if (countDefined(M) < (N - f)) return;
//        if (!predicate.evaluate(M)) return;
//        // For each defined entry, verify its signature.
//        for (int i = 0; i < N; i++) {
//            if (M[i] != UNDEFINED) {
//                String p = String.valueOf(i);
//                String expected = "cc" + p + "INPUT" + M[i].toString();
//                if (!CryptoUtils.verifySignature(expected, sigma[i], CryptoUtils.getPublicKey(p))) {
//                    return;
//                }
//            }
//        }
//        if (!collected) {
//            collected = true;
//            outputQueue.offer(M);
//        }
//    }
//
//    // Blocking call to await the collected output.
//    public Object[] awaitCollected() throws InterruptedException {
//        return outputQueue.take();
//    }
//
//    // Utility: Count non-UNDEFINED entries.
//    private int countDefined(Object[] vector) {
//        int count = 0;
//        for (Object o : vector) {
//            if (o != UNDEFINED) count++;
//        }
//        return count;
//    }
//
//    // Simple serialization (for demonstration purposes).
//    private String serializeVector(Object[] vector) {
//        return Arrays.toString(vector);
//    }
//
//    private Object[] deserializeVector(String s) {
//        // Remove the surrounding brackets and split by comma.
//        s = s.substring(1, s.length() - 1);
//        String[] parts = s.split(", ");
//        Object[] vector = new Object[parts.length];
//        for (int i = 0; i < parts.length; i++) {
//            vector[i] = parts[i].equals("null") ? UNDEFINED : parts[i];
//        }
//        return vector;
//    }
//
//    private String serializeSignatures(byte[][] sigs) {
//        StringBuilder sb = new StringBuilder();
//        for (int i = 0; i < sigs.length; i++) {
//            if (sigs[i] == null) {
//                sb.append("null");
//            } else {
//                sb.append(CryptoUtils.toBase64(sigs[i]));
//            }
//            if (i < sigs.length - 1) sb.append(",");
//        }
//        return sb.toString();
//    }
//
//    private byte[][] deserializeSignatures(String s) {
//        String[] parts = s.split(",");
//        byte[][] sigs = new byte[parts.length][];
//        for (int i = 0; i < parts.length; i++) {
//            sigs[i] = parts[i].equals("null") ? null : CryptoUtils.fromBase64(parts[i]);
//        }
//        return sigs;
//    }
}
