package net.notfab.ttvsi.common.rewards.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import net.notfab.ttvsi.common.NetEvent;
import net.notfab.ttvsi.common.NetworkEvent;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientDeleteRewardEvent(String reward, String channel) implements NetworkEvent {

    @Override
    public NetEvent getType() {
        return NetEvent.CLIENT_DELETE_REWARD;
    }

}
