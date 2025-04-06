package client;

public interface IDepChainClient {
    long getBalance(String addressOf, String currency) throws InterruptedException;

    boolean transfer(String receiverAddress, long amount, String currency) throws InterruptedException;

    boolean transferFrom(String senderAddress, String receiverAddress, long amount, String currency)
            throws InterruptedException;

    boolean addToBlacklist(String targetAddress);
    boolean removeFromBlacklist(String targetAddress);
    boolean isBlacklisted(String targetAddress);
}
