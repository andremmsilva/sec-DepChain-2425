package depchain;

import depchain.config.DepChainConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
    public void testTransferResultsInCorrectBalances() throws InvalidOperationError {
        contractExecutor = new ContractExecutor(config);
        contractExecutor.transferISTCoin(config.accountAddresses.get("10"), config.accountAddresses.get("11"),
                100L);

        assertEquals(99999900, contractExecutor.getISTCoinBalance(config.accountAddresses.get("10")));
        assertEquals(100, contractExecutor.getISTCoinBalance(config.accountAddresses.get("11")));
    }

    @Test
    public void testOwnerCanBlackList() throws InvalidOperationError {
        contractExecutor = new ContractExecutor(config);
        contractExecutor.addToBlackList(config.accountAddresses.get("10"), config.accountAddresses.get("11"));

        assertTrue(contractExecutor.isBlackListed(config.accountAddresses.get("10"),
                config.accountAddresses.get("11")));
    }

    @Test
    public void testAliceCantBlackList() throws InvalidOperationError {
        contractExecutor = new ContractExecutor(config);
        try {
            boolean result = contractExecutor.addToBlackList(config.accountAddresses.get("11"),
                    config.accountAddresses.get("10"));
        } catch (InvalidOperationError e) {
            System.out.println(e.getMessage());
            assertEquals("OwnableUnauthorizedAccount", e.getMessage());
        }

        assertFalse(contractExecutor.isBlackListed(config.accountAddresses.get("10"),
                config.accountAddresses.get("11")));
    }

    @Test
    public void testTransferWithNoBalanceThrowsError() {
        contractExecutor = new ContractExecutor(config);
        boolean thrown = false;
        try {
            contractExecutor.transferISTCoin(config.accountAddresses.get("11"),
                    config.accountAddresses.get("10"), 100L);
        } catch (InvalidOperationError e) {
            System.out.println(e.getMessage());
            thrown = true;
            assertEquals("ERC20InsufficientBalance", e.getMessage());
        }

        assertTrue(thrown);
    }
}
