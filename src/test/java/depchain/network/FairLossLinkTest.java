package depchain.network;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class FairLossLinkTest {
    private final String fakeSignature = "QW5kcmU=";

    @Test
    public void testFairLossLinkMessageLoss() throws Exception {
        FairLossLink sender = new FairLossLink(5000);
        FairLossLink receiver = new FairLossLink(5001);

        Message message1 = new Message(InetAddress.getLocalHost(), 5001, "Message 1", fakeSignature.getBytes());
        sender.sendMessage(message1);

        Message receivedMessage = receiver.receiveMessage();
        assertTrue(receivedMessage == null || receivedMessage.getMessage().equals("Message 1"),
                "FLL should sometimes drop messages");

        sender.close();
        receiver.close();
    }

    @Test
    public void testFairLossLinkMessageDuplication() throws Exception {
        FairLossLink sender = new FairLossLink(5000);
        FairLossLink receiver = new FairLossLink(5001);

        Message message1 = new Message(InetAddress.getLocalHost(), 5001, "DuplicateTest", fakeSignature.getBytes());
        sender.sendMessage(message1);
        sender.sendMessage(message1); // Send twice to induce potential duplication

        Message receivedMessage1 = receiver.receiveMessage();
        Message receivedMessage2 = receiver.receiveMessage();

        assertTrue(receivedMessage1 != null && receivedMessage2 != null,
                "FLL should sometimes duplicate messages");

        sender.close();
        receiver.close();
    }

    @Test
    public void testFairLossLinkMessageReordering() throws Exception {
        FairLossLink sender = new FairLossLink(5000);
        FairLossLink receiver = new FairLossLink(5001);

        Message message1 = new Message(InetAddress.getLocalHost(), 5001, "First", fakeSignature.getBytes());
        Message message2 = new Message(InetAddress.getLocalHost(), 5001, "Second", fakeSignature.getBytes());

        sender.sendMessage(message1);
        sender.sendMessage(message2);


        Message receivedMessage1 = receiver.receiveMessage();
        Message receivedMessage2 = receiver.receiveMessage();

        if (receivedMessage1 != null && receivedMessage2 != null) {
            assertTrue(Objects.equals(receivedMessage1.getMessage(), "First") || Objects.equals(receivedMessage2.getMessage(), "First"),
                    "Messages may arrive out of order due to FLL properties");
        }

        sender.close();
        receiver.close();
    }
}
