package depchain.network;

import depchain.config.DepChainConfig;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.SocketException;

public class SocketManager {
    private DatagramSocket socket;

    public SocketManager(DepChainConfig config) throws SocketException, IllegalArgumentException {
        socket = new DatagramSocket(DepChainConfig.BASE_PORT + config.selfId);
//        socket.setReuseAddress(true);
    }

    public void close() throws IOException {
        if (socket != null) {
            socket.close();
            socket = null;
        }
    }

    public DatagramSocket getSocket() {
        return socket;
    }
}
