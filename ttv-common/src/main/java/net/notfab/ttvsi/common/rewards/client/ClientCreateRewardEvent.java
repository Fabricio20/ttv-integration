package net.notfab.ttvsi.common.rewards.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import net.notfab.ttvsi.common.NetEvent;
import net.notfab.ttvsi.common.NetworkEvent;
import net.notfab.ttvsi.common.rewards.Reward;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientCreateRewardEvent(String channel, Reward reward) implements NetworkEvent {

    @Override
    public NetEvent getType() {
        return NetEvent.CLIENT_CREATE_REWARD;
    }

}
