package depchain.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MessageTest {
    @Test
    public void testEncodeDecode() {
        Message m = new Message("Hello world!", 0);
        String mStr = m.toString();
        assertTrue(mStr.contains("Hello world!||0"));

        Message m2 = Message.fromStr(mStr);
        assertNotNull(m2);
        assertEquals(m2, m);
    }

    @Test
    public void testInvalidMsg() {
        Message m = Message.fromStr("Hello world");
        assertNull(m);

        Message m2 = Message.fromStr("Hello|");
        assertNull(m2);

        Message m3 = Message.fromStr("Hello world||");
        assertNull(m3);
    }

    @Test
    public void testDecodeWithMac() {
        Message m = new Message("Hello world!", 0);
        m.setMac("TESTMAC==".getBytes());
        String mStr = m.toString();

        Message m2 = Message.fromStr(mStr);
        assertNotNull(m2);
        assertEquals(new String(m2.getMac()), new String(m.getMac()));
        assertEquals(m2, m);
    }
}
