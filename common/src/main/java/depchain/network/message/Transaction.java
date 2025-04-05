package depchain.network.message;


public class Transaction {
    private String type;                  // balance or transfer or transferFrom
    private String senderAddress;         // Address of senderAddress
    private String receiverAddress;       // Address of receiver
    private long amount;         // Amount receiverAddress transfer
    private String currency;     //DepCoin or ISTcoin
    private byte[] signature;    // Digital signature of the transaction
    private String blockId;      // Which block you belong to in the blockchain

    public Transaction(String type, String senderAddress, String receiverAddress, long amount, String currency, byte[] signature) {
        this.type = type;
        this.senderAddress = senderAddress;
        this.receiverAddress = receiverAddress;
        this.amount = amount;
        this.currency = currency;
        this.signature = signature;
        this.blockId=null;
    }
    public Transaction(String type, String senderAddress, String currency, byte[] signature) {
        this.type = type;
        this.senderAddress = senderAddress;
        this.receiverAddress = null;
        this.amount=0;
        this.currency = currency;
        this.signature = signature;
        this.blockId=null;
    }

    // Required no-args construct for GSON
    public Transaction() {
        this.type = null;
        this.senderAddress = null;
        this.receiverAddress = null;
        this.amount=0;
        this.currency = null;
        this.signature = null;
        this.blockId=null;        
    }

    // Getters and setters
    public String getType(){
        return type;
    }

    public void setType(String type){
        this.type=type;
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

    public String getBlockId(){
        return blockId;
    }

    public void setBlockId(String id){
        this.blockId=id;
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
                "type=" + type + '\'' +
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
