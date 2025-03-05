package depchain.network;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Base64;

public class Message {
    private InetAddress destination;
    private int destinationPort;
    private byte[] payload;

    public Message(InetAddress destination, int destinationPort, byte[] payload) {
        this.destination = destination;
        this.destinationPort = destinationPort;
        this.payload = payload;
    }

    public Message(InetAddress destination, int destinationPort, String message, byte[] signature) {
        this.destination = destination;
        this.destinationPort = destinationPort;

        String signedMessage = message + "|" + Base64.getEncoder().encodeToString(signature);
        this.payload = signedMessage.getBytes();
    }

    public byte[] getSignature() {
        String payloadStr = new String(this.getPayload()).trim(); // Trim unwanted chars
        String[] parts = payloadStr.split("\\|");

        if (parts.length != 2) {
            System.err.println("Malformed message: " + payloadStr);
            return new byte[0]; // Return empty array to avoid decoding errors
        }

        try {
            return Base64.getDecoder().decode(parts[1]);
        } catch (IllegalArgumentException e) {
            System.err.println("Failed to decode signature: " + parts[1]);
            return new byte[0]; // Avoid crashing due to malformed Base64
        }
    }

    public String getMessage() {
        String payloadStr = new String(this.getPayload()).trim(); // Trim unwanted chars
        String[] parts = payloadStr.split("\\|");

        if (parts.length != 2) {
            System.err.println("Malformed message: " + payloadStr);
            return ""; // Prevent incorrect access
        }

        return parts[0];
    }

    public InetAddress getDestination() {
        return destination;
    }

    public void setDestination(InetAddress destination) {
        this.destination = destination;
    }

    public int getDestinationPort() {
        return destinationPort;
    }

    public void setDestinationPort(int destinationPort) {
        this.destinationPort = destinationPort;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Message message = (Message) o;
        return destinationPort == message.destinationPort && destination.equals(message.destination) && Arrays.equals(payload, message.payload);
    }

    @Override
    public int hashCode() {
        int result = destination.hashCode();
        result = 31 * result + destinationPort;
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }
}
