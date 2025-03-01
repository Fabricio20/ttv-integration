package net.notfab.ttvsi.common.rewards.server;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import net.notfab.ttvsi.common.NetEvent;
import net.notfab.ttvsi.common.NetworkEvent;
import net.notfab.ttvsi.common.rewards.Reward;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RewardSyncEvent(List<Reward> rewards) implements NetworkEvent {

    @Override
    public NetEvent getType() {
        return NetEvent.SERVER_REWARD_SYNC;
    }

}
