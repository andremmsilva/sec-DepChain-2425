package depchain.network.message;

import com.google.gson.Gson;

import java.util.Arrays;

public class Message {
    private MessagePayload payload;
    private byte[] mac;
    private byte[] signature;
    private int senderId;

    public Message(Message other) {
        this.payload = other.payload;
        this.mac = other.mac;
        this.signature = other.signature;
        this.senderId = other.senderId;
    }

    public Message(MessagePayload payload, int senderId) {
        this.payload = payload;
        this.senderId = senderId;
        this.mac = null;
        this.signature = null;
    }

    public Message(MessagePayload payload, int senderId, byte[] mac, byte[] signature) {
        this.payload = payload;
        this.mac = mac;
        this.signature = signature;
        this.senderId = senderId;
    }

    public static Message deserialize(Gson gson, String json) {
        return gson.fromJson(json, Message.class);
    }

    public String serialize(Gson gson) {
        return gson.toJson(this);
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }

    public MessagePayload getPayload() {
        return payload;
    }

    public void setPayload(MessagePayload payload) {
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
