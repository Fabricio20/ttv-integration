package net.notfab.ttvsi.server.listeners;

import lombok.extern.slf4j.Slf4j;
import net.jodah.expiringmap.ExpirationListener;
import net.jodah.expiringmap.ExpiringMap;
import net.notfab.ttvsi.common.NetEvent;
import net.notfab.ttvsi.common.WsCredentials;
import net.notfab.ttvsi.common.polls.Poll;
import net.notfab.ttvsi.common.polls.client.ClientPollFinishedEvent;
import net.notfab.ttvsi.common.polls.client.ClientPollProgressEvent;
import net.notfab.ttvsi.common.polls.client.ClientRequestedPollCreateEvent;
import net.notfab.ttvsi.common.polls.server.ServerCreatePollEvent;
import net.notfab.ttvsi.common.polls.server.ServerPollFinishedEvent;
import net.notfab.ttvsi.common.polls.server.ServerPollUpdateEvent;
import net.notfab.ttvsi.server.models.PollState;
import net.notfab.ttvsi.server.websocket.BufferedWebSocketHandler;
import net.notfab.ttvsi.server.websocket.events.WsMessageEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class PollListener implements ExpirationListener<String, Integer> {

    private final ExpiringMap<String, Integer> running = ExpiringMap.builder()
            .variableExpiration()
            .expirationListener(this)
            .build();
    private final Map<String, PollState> polls = new HashMap<>();
    private final BufferedWebSocketHandler server;

    public PollListener(BufferedWebSocketHandler server) {
        this.server = server;
    }

    /**
     * Poll was requested by a connected client
     */
    @EventListener
    public void onPollCreated(WsMessageEvent event) {
        if (!event.event().equals(NetEvent.CLIENT_CREATE_POLL)) {
            return;
        }
        WsCredentials credentials = event.credentials();
        if (credentials == null) {
            return;
        }
        ClientRequestedPollCreateEvent payload = (ClientRequestedPollCreateEvent) event.payload();
        if (payload == null) {
            return;
        }
        Poll poll = payload.poll();
        if (this.polls.containsKey(poll.getId())) {
            log.warn("Prevented creation of duplicate poll with id {}", poll.getId());
            return;
        }
        this.polls.put(poll.getId(), new PollState(poll.getId(), credentials.roomId()));
        this.server.broadcast(credentials.roomId(), new ServerCreatePollEvent(poll.getId(), poll));
        this.running.put(poll.getId(), 0, poll.getDuration() + 2, TimeUnit.SECONDS);
        log.info("Poll {} created", poll.getId());
    }

    /**
     * Poll had a progress update on a client
     */
    @EventListener
    private void onPollProgress(WsMessageEvent event) {
        if (!event.event().equals(NetEvent.CLIENT_POLL_PROGRESS)) {
            return;
        }
        WsCredentials credentials = event.credentials();
        if (credentials == null) {
            return;
        }
        ClientPollProgressEvent payload = (ClientPollProgressEvent) event.payload();
        if (payload == null) {
            return;
        }
        PollState state = this.polls.get(payload.id());
        if (state == null) {
            log.warn("Invalid poll {}", payload.id());
            return;
        }
        state.setVotes(credentials.twitchId(), payload.votes());
        this.server.broadcast(credentials.roomId(), new ServerPollUpdateEvent(state.getId(), state.getResults()));
        log.info("Poll {} had a progress update on channel {}", payload.id(), credentials.twitchId());
    }

    /**
     * Poll finished on a client
     */
    @EventListener
    private void onPollFinished(WsMessageEvent event) {
        if (!event.event().equals(NetEvent.CLIENT_POLL_FINISHED)) {
            return;
        }
        WsCredentials credentials = event.credentials();
        if (credentials == null) {
            return;
        }
        ClientPollFinishedEvent payload = (ClientPollFinishedEvent) event.payload();
        if (payload == null) {
            return;
        }
        PollState state = this.polls.get(payload.id());
        if (state == null) {
            log.warn("Invalid poll {}", payload.id());
            return;
        }
        state.setVotes(credentials.twitchId(), payload.votes());
        log.info("Poll {} finished on channel {}", payload.id(), credentials.twitchId());
    }

    @Override
    public void expired(String poll, Integer expiry) {
        PollState state = this.polls.get(poll);
        if (state == null) {
            return;
        }
        Map<String, Integer> results = state.getResults();
        log.info("Poll {} expired, results: {}", poll, results);
        this.server.broadcast(state.getRoom(), new ServerPollFinishedEvent(poll, results));
    }

}
