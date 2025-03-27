package depchain.network.message;

public enum MessageType {
    HS_SYN,
    HS_SYNACK,
    HS_ACK,
    SL_ACK,
    SEND,
    COLLECTED,
    READ,
    WRITE,
    ACCEPT,
    APPEND_REQ,
    APPEND_CONFIRMED,
    APPEND_FAILED
}
