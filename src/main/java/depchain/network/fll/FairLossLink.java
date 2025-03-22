package depchain.network.fll;

import depchain.config.DepChainConfig;
import depchain.network.Message;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.function.Consumer;

public class FairLossLink {
    private final DepChainConfig config;
    // Address and port of the target of this link.
    private final Thread listenerThread;
    private final DatagramSocket socket;
    private final Consumer<FLLSendEvent> sendHandler = e -> {
        try {
            this.sendMessage(e.arg, e.getTargetId());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    };
    // Received message blocking queue
    private volatile boolean running = true;

    public FairLossLink(DepChainConfig config) {
        this.config = config;

        this.listenerThread = new Thread(this::startReceiving);
        this.socket = this.config.socketManager.getSocket();

        // Listen forever for messages in a background thread.
        this.listenerThread.start();

        // Listen for the FLL Send Event, and perform the expected action.
        this.config.eventBus.register(FLLSendEvent.class, this.sendHandler);
    }

    private void startReceiving() {
        try {
            while (running && socket != null) {
                // Get the next message into a buffer
                byte[] buffer = new byte[4096];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                // Build the actual message object from the buffer
                Message m = Message.fromStr(new String(packet.getData(), 0, packet.getLength()));
                if (m == null) {
                    continue;
                }

                // Emit the deliver event
                this.config.eventBus.publish(new FLLDeliverEvent(m));
            }
        } catch (Exception e) {
            if (running) {
                throw new RuntimeException(e);
            }
        }
    }

    public void sendMessage(Message m, int targetId) throws Exception {
        String destAddress = DepChainConfig.ADDRESS;
        int destPort = DepChainConfig.basePort + targetId;
        DatagramPacket packet = new DatagramPacket(m.toString().getBytes(), m.toString().length(),
                InetAddress.getByName(destAddress),
                destPort);
        socket.send(packet);
    }

    public void close() {
        running = false;
        listenerThread.interrupt();
        try {
            this.config.socketManager.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.config.eventBus.unregister(FLLSendEvent.class, sendHandler);
    }
}
