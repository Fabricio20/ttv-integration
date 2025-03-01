package net.notfab.ttvsi.common.polls.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import net.notfab.ttvsi.common.NetEvent;
import net.notfab.ttvsi.common.NetworkEvent;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientPollProgressEvent(String id, String channel, Map<String, Integer> votes) implements NetworkEvent {

    @Override
    public NetEvent getType() {
        return NetEvent.CLIENT_POLL_PROGRESS;
    }

}
