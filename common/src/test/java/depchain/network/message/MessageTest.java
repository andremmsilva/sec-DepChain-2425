package depchain.network.message;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MessageTest {
    private static final Gson gson = new Gson();

    // Helper method for creating test messages
    private Message createTestMessage(MessageType type, String data, int senderId) {
        MessagePayload payload = new MessagePayload(type, data);
        return new Message(payload, senderId);
    }

    @Test
    void testRoundTripSerialization() {
        Message original = createTestMessage(null, "test_data", 1);
        String serialized = original.serialize(gson);
        Message deserialized = Message.deserialize(gson, serialized);

        assertEquals(original.getPayload().getType(), deserialized.getPayload().getType());
        assertEquals(original.getPayload().getData(), deserialized.getPayload().getData());
        assertEquals(original.getSenderId(), deserialized.getSenderId());
    }

    @Test
    void testEmptyPayload() {
        Message original = createTestMessage(null, "", 0);
        String serialized = original.serialize(gson);
        Message deserialized = Message.deserialize(gson, serialized);

        assertTrue(deserialized.getPayload().getType() == null);
        assertTrue(deserialized.getPayload().getData().isEmpty());
    }
}
