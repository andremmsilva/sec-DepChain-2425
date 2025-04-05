package depchain.network.message;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class MessagePayload {

    private MessageType type;
    private Transaction transaction;
    private String data;
    private Map<String,String> parameters;

    public MessagePayload(MessagePayload other) {
        this.type = other.type;
        this.transaction = other.transaction;
        this.data = other.data;
        this.parameters = other.parameters;
    }

    public MessagePayload(MessageType type, String data) {
        this.type = type;
        this.data = data;
        this.transaction=null;
        this.parameters = null;
    }
    public MessagePayload(MessageType type, Transaction transaction) {
        this.type = type;
        this.data = null;
        this.transaction=transaction;
        this.parameters = null;
    }

    public MessagePayload(MessageType type, String data, Map<String, String> parameters) {
        this.type = type;
        this.data = data;
        this.transaction=null;
        this.parameters = parameters;
    }

    public MessagePayload(MessageType type, String data, Transaction transaction) {
        this.type = type;
        this.data = data;
        this.transaction = transaction;
        this.parameters = null;
    }

    public String serialize(Gson gson) {
        return gson.toJson(this);
    }

    public static MessagePayload deserialize(Gson gson, String json) {
        return gson.fromJson(json, MessagePayload.class);
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public Transaction getTransaction(){
        return transaction;
    }

    public void setTransaction(Transaction transaction){
        this.transaction=transaction;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    public void addParameter(String key, String value) {
        if (parameters == null) {
            parameters = new HashMap<>();
        }

        parameters.put(key, value);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        MessagePayload that = (MessagePayload) o;
        return Objects.equals(getType(), that.getType()) && Objects.equals(getData(), that.getData()) && Objects.equals(getTransaction(), that.getTransaction()) && Objects.equals(getParameters(), that.getParameters());
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(getType());
        result = 31 * result + Objects.hashCode(getData());
        result = 31 * result + Objects.hashCode(getTransaction());
        result = 31 * result + Objects.hashCode(getParameters());
        return result;
    }
}
