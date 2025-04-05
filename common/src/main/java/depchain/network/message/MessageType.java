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
    TRANSFER_REQ,
    BALANCE_REQ,
    TRANSFERFROM_REQ,
    TRANSACTION_CONFIRMED,
    TRANSACTION_FAILED
}
