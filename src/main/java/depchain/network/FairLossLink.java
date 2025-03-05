package depchain.network;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class FairLossLink {
    private final DatagramSocket socket;
    private final BlockingQueue<Message> messageQueue = new LinkedBlockingQueue<>();
    private volatile boolean running = true;
    private final Thread listenerThread;

    public FairLossLink(int port) throws Exception {
        this.socket = new DatagramSocket(port);
        this.listenerThread = new Thread(this::startReceiving);
        this.listenerThread.start();
    }

    private void startReceiving() {
        try {
            while (running) {
                byte[] buffer = new byte[4096];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                Message m = new Message(packet.getAddress(), packet.getPort(), packet.getData());
                if (!messageQueue.offer(m, 500, TimeUnit.MILLISECONDS)) { // Add received message to queue
                    throw new RuntimeException("Message queue is full");
                }
            }
        } catch (Exception e) {
            if (running) {
                throw new RuntimeException(e);
            }
        }
    }

    public void sendMessage(Message m) throws Exception {
        byte[] payload = m.getPayload();
        DatagramPacket packet = new DatagramPacket(payload, payload.length, m.getDestination(),
                m.getDestinationPort());
        socket.send(packet);
    }

    public Message receiveMessage() throws InterruptedException {
        while (running) {
            Message m = messageQueue.poll(300, TimeUnit.MILLISECONDS);
            if (m != null) return m;
        }

        return null;
    }

    public void close() {
        running = false;
        listenerThread.interrupt();
        socket.close();
    }
}
