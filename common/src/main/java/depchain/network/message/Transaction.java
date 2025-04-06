package depchain.network.message;


import com.google.gson.Gson;

public class Transaction {
    private String senderAddress;         // Address of senderAddress
    private String receiverAddress;       // Address of receiver
    private long amount;         // Amount receiverAddress transfer
    private String currency;     //DepCoin or ISTcoin

    public Transaction(String senderAddress, String receiverAddress, long amount, String currency) {
        this.senderAddress = senderAddress;
        this.receiverAddress = receiverAddress;
        this.amount = amount;
        this.currency = currency;
    }

    public Transaction(String senderAddress, String currency) {
        this.senderAddress = senderAddress;
        this.receiverAddress = null;
        this.amount = 0;
        this.currency = currency;
    }

    // Required no-args construct for GSON
    @SuppressWarnings("unused")
    public Transaction() {
        this.senderAddress = null;
        this.receiverAddress = null;
        this.amount = 0;
        this.currency = null;
    }

    public static Transaction fromJson(Gson gson, String json) {
        return gson.fromJson(json, Transaction.class);
    }

    public String getSenderAddress() {
        return senderAddress;
    }

    public void setSenderAddress(String senderAddress) {
        this.senderAddress = senderAddress;
    }

    public String getRecieverAddress() {
        return receiverAddress;
    }

    public void setRecieverAddress(String receiverAddress) {
        this.receiverAddress = receiverAddress;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String serialize(Gson gson) {
        return gson.toJson(this);
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "senderAddress='" + senderAddress + '\'' +
                ", receiverAddress='" + receiverAddress + '\'' +
                ", amount=" + amount + '\'' +
                ", currency=" + currency + '\'' +
                '}';
    }

    public boolean isEmpty() {
        return this.senderAddress == null || this.receiverAddress == null;
    }
}
