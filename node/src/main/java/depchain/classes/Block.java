package depchain.classes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class Block {
    private final String previousBlockHash;
    private final List<String> transactions;
    private final String blockHash;

    public Block(String previousBlockHash, List<String> transactions) {
        this.previousBlockHash = previousBlockHash;
        this.transactions = transactions;
        this.blockHash = computeBlockHash();
    }

    private String computeBlockHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Gson gson = new GsonBuilder().create();
            String data = previousBlockHash + gson.toJson(transactions);
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public String getPreviousBlockHash() {
        return previousBlockHash;
    }

    public List<String> getTransactions() {
        return transactions;
    }

    public String getBlockHash() {
        return blockHash;
    }
}
