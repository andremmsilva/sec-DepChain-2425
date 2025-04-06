package client;

public interface IDepChainClient {
    long getBalance() throws InterruptedException;

    boolean transfer(String receiverAddress, long amount, String currency) throws InterruptedException;

    boolean transferFrom(String senderAddress, String receiverAddress, long amount, String currency)
            throws InterruptedException;
}
