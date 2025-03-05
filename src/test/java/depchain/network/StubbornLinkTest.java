package depchain.network;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StubbornLinkTest {
    @Test
    public void testStubbornLinkEnsuresDelivery() throws Exception {
        StubbornLink sender = new StubbornLink(5000);
        StubbornLink receiver = new StubbornLink(5001);

        Message message = new Message(InetAddress.getLocalHost(), 5001, "GuaranteedDelivery", "FakeSignature".getBytes());
        sender.sendMessage(message);

        Thread.sleep(3000); // Give time for retransmissions

        Message receivedMessage = receiver.receiveMessage();
        assertEquals("GuaranteedDelivery", receivedMessage.getMessage(),
                "SL should always deliver messages eventually");

        sender.close();
        receiver.close();
    }
}
