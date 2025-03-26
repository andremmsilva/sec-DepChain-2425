package depchain.network.message;

public enum MessageType {
    SYN,
    SYNACK,
    ACK,
    SEND,
    COLLECTED,
    READ,
    WRITE,
    ACCEPT,
    APPEND_REQ,
    APPEND_CONFIRMED,
    APPEND_FAILED
}
