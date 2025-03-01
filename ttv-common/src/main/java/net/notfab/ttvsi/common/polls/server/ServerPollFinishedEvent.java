package net.notfab.ttvsi.common.polls.server;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import net.notfab.ttvsi.common.NetEvent;
import net.notfab.ttvsi.common.NetworkEvent;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ServerPollFinishedEvent(String id, Map<String, Integer> votes) implements NetworkEvent {

    @Override
    public NetEvent getType() {
        return NetEvent.SERVER_POLL_FINISHED;
    }

}
