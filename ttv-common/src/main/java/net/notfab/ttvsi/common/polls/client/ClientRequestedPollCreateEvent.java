package net.notfab.ttvsi.common.polls.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import net.notfab.ttvsi.common.NetEvent;
import net.notfab.ttvsi.common.NetworkEvent;
import net.notfab.ttvsi.common.polls.Poll;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientRequestedPollCreateEvent(Poll poll, String channel) implements NetworkEvent {

    @Override
    public NetEvent getType() {
        return NetEvent.CLIENT_CREATE_POLL;
    }

}
