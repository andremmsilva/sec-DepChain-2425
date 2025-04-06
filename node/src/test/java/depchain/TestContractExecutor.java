package depchain;

import depchain.config.DepChainConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestContractExecutor {
    private final DepChainConfig config = new DepChainConfig(0, 0, "127.0.0.1");
    private ContractExecutor contractExecutor;

    @Test
    public void testBalanceIsCorrect() {
        contractExecutor = new ContractExecutor(config);
        assertEquals(100000000, contractExecutor.getISTCoinBalance(config.accountAddresses.get("10")));
        assertEquals(0, contractExecutor.getISTCoinBalance(config.accountAddresses.get("11")));
    }

    @Test
    public void testTransferResultsInCorrectBalances() {
        contractExecutor = new ContractExecutor(config);
        contractExecutor.transferISTCoin(config.accountAddresses.get("10"), config.accountAddresses.get("11"), 100L);

        assertEquals(99999900, contractExecutor.getISTCoinBalance(config.accountAddresses.get("10")));
        assertEquals(100, contractExecutor.getISTCoinBalance(config.accountAddresses.get("11")));
    }
}
