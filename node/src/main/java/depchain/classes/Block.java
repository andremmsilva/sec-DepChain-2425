package depchain.classes;

import depchain.network.message.MessagePayload;

import java.util.List;

public class Block {
    private String hash;
    private String previousHash;
    private List<MessagePayload> transactions;

    public Block(List<MessagePayload> transactions) {
        this.transactions = transactions;
    }

    public Block(String hash, String previousHash, List<MessagePayload> transactions) {
        this.hash = hash;
        this.previousHash = previousHash;
        this.transactions = transactions;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public void setPreviousHash(String previousHash) {
        this.previousHash = previousHash;
    }

    public List<MessagePayload> getTransactions() {
        return transactions;
    }

    public void setTransactions(List<MessagePayload> transactions) {
        this.transactions = transactions;
    }
}
