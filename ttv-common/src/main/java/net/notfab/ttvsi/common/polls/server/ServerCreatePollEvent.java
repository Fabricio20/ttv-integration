package net.notfab.ttvsi.common.polls.server;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import net.notfab.ttvsi.common.NetEvent;
import net.notfab.ttvsi.common.NetworkEvent;
import net.notfab.ttvsi.common.polls.Poll;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ServerCreatePollEvent(String id, Poll poll) implements NetworkEvent {

    @Override
    public NetEvent getType() {
        return NetEvent.SERVER_POLL_CREATE;
    }

}
