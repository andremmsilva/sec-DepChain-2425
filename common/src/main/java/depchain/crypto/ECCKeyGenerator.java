package depchain.crypto;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public class ECCKeyGenerator {

    public static void main(String[] args) throws Exception {
        int numKeys = 10; // Change this number to generate more/less keys
        SecureRandom random = new SecureRandom();
        List<String> addresses = new ArrayList<>();

        for (int i = 0; i < numKeys; i++) {
            BigInteger privateKeyInt = new BigInteger(256, random);
            ECKeyPair keyPair = ECKeyPair.create(privateKeyInt);
            String privateKeyHex = Numeric.toHexStringWithPrefixZeroPadded(keyPair.getPrivateKey(), 64);
            String address = "0x" + Keys.getAddress(keyPair);

            // Save private key to a file
            try (FileWriter writer = new FileWriter("private_key_" + i + ".txt")) {
                writer.write(privateKeyHex);
            }

            addresses.add(address);
            System.out.println("Generated address: " + address);
        }

        // Save all addresses to addresses.txt
        try (FileWriter writer = new FileWriter("addresses.txt")) {
            for (String address : addresses) {
                writer.write(address + "\n");
            }
        }

        System.out.println("Finished generating keys and addresses.");
    }
}

