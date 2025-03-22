package depchain.consensus.utils;

import depchain.config.DepChainConfig;
import depchain.network.Message;
import depchain.network.apl.APLSendEvent;

public class Broadcast {
    public static void all(String payload, DepChainConfig config) {
        System.out.println("RW " + config.selfId + " broadcasting " + payload);
        for (int i = 0; i < DepChainConfig.PROCESSES; i++) {
            config.eventBus.publish(new APLSendEvent(new Message(payload, config.selfId), i));
        }
    }
}
