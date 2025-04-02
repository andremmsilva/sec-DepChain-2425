package depchain.classes;

import java.util.List;
import java.util.Map;

public class GenesisBlock extends Block {
    private final Map<String, AccountState> state;

    public GenesisBlock(String blockHash, String previousBlockHash, List<String> transactions, Map<String, AccountState> state) {
        super(null, transactions);
        this.state = state;
    }

    public Map<String, AccountState> getState() {
        return state;
    }

    class AccountState {
        private String balance;
        private String code;
        private Map<String, String> storage;
    
        public AccountState(String balance, String code, Map<String, String> storage) {
            this.balance = balance;
            this.code = code;
            this.storage = storage;
        }
    
        public String getBalance() {
            return balance;
        }
    
        public String getCode() {
            return code;
        }
    
        public Map<String, String> getStorage() {
            return storage;
        }
    }
}
