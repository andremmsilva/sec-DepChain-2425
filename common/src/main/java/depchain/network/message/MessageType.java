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
    TRANSACTION_CONFIRMED,
    TRANSACTION_FAILED,
    BALANCE_REQ,
    TRANSFER_REQ,
    TRANSFERFROM_REQ,
}
