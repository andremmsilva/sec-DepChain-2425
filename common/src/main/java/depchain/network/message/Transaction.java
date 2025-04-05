package depchain.network.message;


public class Transaction {
    private String senderAddress;         // Address of senderAddress
    private String receiverAddress;       // Address of receiver
    private long amount;         // Amount receiverAddress transfer
    private String currency;     //DepCoin or ISTcoin
    private byte[] signature;    // Digital signature of the transaction

    public Transaction( String senderAddress, String receiverAddress, long amount, String currency, byte[] signature) {
        this.senderAddress = senderAddress;
        this.receiverAddress = receiverAddress;
        this.amount = amount;
        this.currency = currency;
        this.signature = signature;
    }
    public Transaction( String senderAddress, String currency, byte[] signature) {
        this.senderAddress = senderAddress;
        this.receiverAddress = null;
        this.amount=0;
        this.currency = currency;
        this.signature = signature;
    }

    // Required no-args construct for GSON
    public Transaction() {
        this.senderAddress = null;
        this.receiverAddress = null;
        this.amount=0;
        this.currency = null;
        this.signature = null;
    }

    public String getSender() {
        return senderAddress;
    }

    public void setSender(String senderAddress) {
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

    public String getCurrency(){
        return currency;
    }

    public void setCurrency(String currency){
        this.currency = currency;
    }

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "senderAddress='" + senderAddress + '\'' +
                ", receiverAddress='" + receiverAddress + '\'' +
                ", amount=" + amount+ '\'' +
                ", currency=" + currency+ '\'' +
                ", signature='" + signature.toString() + '\'' +
                '}';
    }

    public boolean isEmpty() {
        return this.senderAddress.equals(null)||this.receiverAddress.equals(null)||this.signature.equals(null);
    }
}
