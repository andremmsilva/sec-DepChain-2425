package depchain;

import depchain.network.message.MessagePayload;

import java.util.List;

public class Block {
    String hash;
    String previousHash;
    List<MessagePayload> transactions;

    public Block(List<MessagePayload> transactions) {
        this.transactions = transactions;
    }

    public Block(String hash, String previousHash, List<MessagePayload> transactions) {
        this.hash = hash;
        this.previousHash = previousHash;
        this.transactions = transactions;
    }
}
