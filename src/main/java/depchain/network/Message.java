package depchain.network;

import depchain.crypto.CryptoUtils;

import java.util.Arrays;

public class Message {
    private String payload;
    private byte[] mac;
    private byte[] signature;
    private int senderId;

    public Message(Message other) {
        this.payload = other.payload;
        this.mac = other.mac;
        this.signature = other.signature;
        this.senderId = other.senderId;
    }

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
        // Find the last delimiter for the signature
        int lastDelim = messageStr.lastIndexOf("||");
        if (lastDelim == -1) {
            System.err.println("Received incorrect message format - " + messageStr);
            return null;
        }
        String signaturePart = messageStr.substring(lastDelim + 2);
        String remainder = messageStr.substring(0, lastDelim);

        // Find the second last delimiter for the MAC
        int secondLastDelim = remainder.lastIndexOf("||");
        if (secondLastDelim == -1) {
            System.err.println("Received incorrect message format - " + messageStr);
            return null;
        }
        String macPart = remainder.substring(secondLastDelim + 2);
        remainder = remainder.substring(0, secondLastDelim);

        // Find the third last delimiter for the senderId
        int thirdLastDelim = remainder.lastIndexOf("||");
        if (thirdLastDelim == -1) {
            System.err.println("Received incorrect message format - " + messageStr);
            return null;
        }
        String senderIdStr = remainder.substring(thirdLastDelim + 2);
        String payload = remainder.substring(0, thirdLastDelim);

        try {
            int senderId = Integer.parseInt(senderIdStr);
            Message m = new Message(payload, senderId);
            if (!macPart.isEmpty()) {
                m.mac = CryptoUtils.decodeFromBase64(macPart);
            }
            if (!signaturePart.isEmpty()) {
                m.signature = CryptoUtils.decodeFromBase64(signaturePart);
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
