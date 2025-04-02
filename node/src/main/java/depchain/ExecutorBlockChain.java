package depchain;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.tuweni.bytes.Bytes;

import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.*;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.fluent.EVMExecutor;
import org.hyperledger.besu.evm.fluent.SimpleWorld;
import org.hyperledger.besu.evm.tracing.StandardJsonTracer;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigInteger;

import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;
public class ExecutorBlockChain {
    public static void main(String[] args) {
        SimpleWorld simpleWorld = new SimpleWorld();

        Address ownerAddress = Address.fromHexString("adminadminadminadminadminadminadminadmin");
        simpleWorld.createAccount(ownerAddress,0, Wei.fromEth(100));
        MutableAccount ownerAccount = (MutableAccount) simpleWorld.get(ownerAddress);
        System.out.println("Admin Account");
        System.out.println("  Address: "+ownerAccount.getAddress());
        System.out.println("  Balance: "+ownerAccount.getBalance());
        System.out.println("  Nonce: "+ownerAccount.getNonce());
        System.out.println();    
    }
}
