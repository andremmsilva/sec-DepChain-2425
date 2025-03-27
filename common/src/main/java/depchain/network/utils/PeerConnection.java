package depchain.network.utils;

import depchain.network.message.Message;

import javax.crypto.SecretKey;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class PeerConnection {
    public final int peerId;
    public final Lock lock = new ReentrantLock();

    // Auth fields
    public final Condition handshakeComplete = lock.newCondition();
    public SecretKey sharedKey = null; // becomes non-null once handshake completes
    public SecretKey generatedKey = null; // the key we generated when initiating handshake

    // Message buffers
    public final Queue<Message> incomingMessages = new LinkedList<>();
    public PriorityQueue<Message> futureMessages = new PriorityQueue<>((m1, m2) -> {
        int seq1 = Integer.parseInt(m1.getPayload().getParameters().getOrDefault("seq", "-1"));
        int seq2 = Integer.parseInt(m2.getPayload().getParameters().getOrDefault("seq", "-1"));
        return Integer.compare(seq1, seq2);
    });
    public final Queue<Message> outgoingMessages = new LinkedList<>();

    // Flow control and retransmissions
    public int expectedSeqNum = 0;
    public int lastSentSeqNum = 0;
    public int lastAckedSeqNum = -1;
    public ConcurrentHashMap<Integer, Message> unackedMessages = new ConcurrentHashMap<>();

    public PeerConnection(int peerId) {
        this.peerId = peerId;
    }
}

