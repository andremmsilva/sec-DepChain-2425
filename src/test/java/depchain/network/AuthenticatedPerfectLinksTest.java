package depchain.network;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AuthenticatedPerfectLinksTest {
    @Test
    public void testAuthenticatedMessageExchange() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        AuthenticatedPerfectLink sender = new AuthenticatedPerfectLink(5000, keyPair.getPrivate(), keyPair.getPublic());
        AuthenticatedPerfectLink receiver = new AuthenticatedPerfectLink(5001, keyPair.getPrivate(),
                keyPair.getPublic());

        String message = "Hello world!";
        sender.sendMessage(InetAddress.getLocalHost(), 5001, message);

        Message m = receiver.getNextMessage();
        assert m.getMessage().equals(message);
        assertTrue(receiver.hasReceivedMessage(m));

        sender.close();
        receiver.close();
    }

    @Test
    public void testAuthenticatedPerfectLinkAuthenticity() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair1 = keyGen.generateKeyPair();
        KeyPair keyPair2 = keyGen.generateKeyPair(); // Different key pair for forgery

        AuthenticatedPerfectLink receiver = new AuthenticatedPerfectLink(5001, keyPair1.getPrivate(), keyPair1.getPublic());

        // Send a message with a fake signature
        Message fakeMessage = new Message(InetAddress.getLocalHost(), 5001, "FakeMessage", keyPair2.getPrivate().getEncoded());
        receiver.handleMessage(fakeMessage);

        assertFalse(receiver.hasReceivedMessage(fakeMessage), "APL should reject forged messages");

        receiver.close();
    }
}
