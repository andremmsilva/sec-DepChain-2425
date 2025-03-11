package depchain.network;

import depchain.crypto.CryptoUtils;

import java.util.Arrays;

public class Message {
    private String payload;
    private byte[] mac;
    private byte[] signature;
    private int senderId;

    public Message(String payload, int senderId) {
        this.payload = payload;
        this.senderId = senderId;
        this.mac = null;
        this.signature = null;
    }

    public Message(String payload, int senderId, byte[] mac, byte[] signature) {
        this.payload = payload;
        this.mac = mac;
        this.signature = signature;
        this.senderId = senderId;
    }

    public static Message fromStr(String messageStr) {
        String[] parts = messageStr.split("\\|\\|");
        if (parts.length < 3) {
            System.err.println("Received incorrect message format - " + messageStr);
            return null;
        }

        try {
            Message m = new Message(parts[0], Integer.parseInt(parts[1]));
            if (!parts[2].isEmpty()) {
                m.mac = CryptoUtils.decodeFromBase64(parts[2]);
            }

            if (parts.length > 3 && !parts[3].isEmpty()) {
                m.signature = CryptoUtils.decodeFromBase64(parts[3]);
            }

            return m;
        } catch (NumberFormatException e) {
            System.err.println("Received incorrect message format - " + messageStr);
            return null;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(payload);
        sb.append("||");
        sb.append(senderId);

        sb.append("||");
        if (mac != null) {
            sb.append(CryptoUtils.encodeToBase64(mac));
        }

        sb.append("||");
        if (signature != null) {
            sb.append(CryptoUtils.encodeToBase64(signature));
        }

        return sb.toString();
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public byte[] getMac() {
        return mac;
    }

    public void setMac(byte[] mac) {
        this.mac = mac;
    }

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public int getSenderId() {
        return senderId;
    }

    public void setSenderId(int senderId) {
        this.senderId = senderId;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Message message = (Message) o;
        return getSenderId() == message.getSenderId() && getPayload().equals(message.getPayload()) && Arrays.equals(getMac(), message.getMac()) && Arrays.equals(getSignature(), message.getSignature());
    }

    @Override
    public int hashCode() {
        int result = getPayload().hashCode();
        result = 31 * result + Arrays.hashCode(getMac());
        result = 31 * result + Arrays.hashCode(getSignature());
        result = 31 * result + getSenderId();
        return result;
    }
}
