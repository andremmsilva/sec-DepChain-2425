package depchain.consensus.utils;

import com.google.gson.Gson;
import depchain.config.DepChainConfig;
import depchain.network.message.Message;
import depchain.network.apl.APLSendEvent;
import depchain.network.message.MessagePayload;

public class Broadcast {
    private static final Gson gson = new Gson();

    public static void all(MessagePayload payload, DepChainConfig config) {
        System.out.println(config.selfId + " broadcasting " + payload.serialize(gson));
        for (int i = 0; i < DepChainConfig.PROCESSES; i++) {
            config.eventBus.publish(new APLSendEvent(new Message(payload, config.selfId), i));
        }
    }
}
