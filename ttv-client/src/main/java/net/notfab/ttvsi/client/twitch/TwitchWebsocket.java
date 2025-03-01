package net.notfab.ttvsi.client.twitch;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.notfab.ttvsi.client.models.SocketState;
import net.notfab.ttvsi.client.models.events.TwitchEstablishedEvent;
import net.notfab.ttvsi.client.models.events.TwitchWsStateEvent;
import net.notfab.ttvsi.client.models.twitch.TwitchPollFinishedEvent;
import net.notfab.ttvsi.client.models.twitch.TwitchPollProgressedEvent;
import net.notfab.ttvsi.client.models.twitch.TwitchRewardEvent;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.context.ApplicationEventPublisher;

import java.net.ConnectException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class TwitchWebsocket extends WebSocketClient {

    private final ApplicationEventPublisher publisher;
    private final AtomicReference<String> id = new AtomicReference<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @Getter
    private SocketState state;

    public TwitchWebsocket(ApplicationEventPublisher publisher) {
        super(URI.create("wss://eventsub.wss.twitch.tv/ws"));
        this.publisher = publisher;
        this.setConnectionLostTimeout(15);
        this.state = SocketState.DISCONNECTED;
        this.publisher.publishEvent(new TwitchWsStateEvent(this.state));
    }

    /**
     * Terminates a connection and any reconnection attempts
     */
    public void terminate() {
        this.scheduler.shutdownNow();
        this.close();
        this.state = SocketState.DISCONNECTED;
        this.publisher.publishEvent(new TwitchWsStateEvent(this.state));
    }

    @Override
    public void onOpen(ServerHandshake data) {
        log.info("Websocket connection opened");
        this.state = SocketState.CONNECTED;
        this.publisher.publishEvent(new TwitchWsStateEvent(this.state));
    }

    @Override
    public void onMessage(String message) {
        JSONObject object = new JSONObject(message);

        String type = this.getEventType(object);
        if (this.id.get() == null) {
            String session = this.getWelcomeId(object);
            if (session == null) {
                return;
            }
            this.id.set(session);
            log.info("Twitch websocket established ({})", session);
            this.publisher.publishEvent(new TwitchEstablishedEvent(session));
            return;
        } else if ("session_keepalive".equals(type)) {
            // Keepalive means connection is healthy
            return;
        } else if (!object.has("event") || type == null) {
            log.warn("Weird twitch event received: {}", message);
            return;
        }
        JSONObject event = object.getJSONObject("event");
        if ("channel.poll.progress".equalsIgnoreCase(type)) {
            Map<String, Integer> votes = this.getPollVotes(event);
            this.publisher.publishEvent(new TwitchPollProgressedEvent(event.getString("id"), votes));
        } else if ("channel.poll.end".equalsIgnoreCase(type)) {
            Map<String, Integer> votes = this.getPollVotes(event);
            this.publisher.publishEvent(new TwitchPollFinishedEvent(event.getString("id"), votes));
        } else if ("channel.channel_points_custom_reward_redemption.add".equalsIgnoreCase(type)) {
            // user_login = channel url name
            // user_name = display name
            // user_input = textual user input, may be empty
            // id = redemption id
            // reward = object
            // reward.id = reward identifier
            JSONObject reward = event.getJSONObject("reward");
            this.publisher.publishEvent(new TwitchRewardEvent(event.getString("id"), reward.getString("id"),
                    event.getString("user_login"), event.getString("user_name"), event.optString("user_input")));
        }
    }

    private Map<String, Integer> getPollVotes(JSONObject event) {
        Map<String, Integer> votes = new HashMap<>();
        JSONArray choices = event.getJSONArray("choices");
        if (choices == null) {
            return null;
        }
        // {"id": "123", "title": "Blue", "bits_votes": 50, "channel_points_votes": 70, "votes": 120},
        for (int i = 0; i < choices.length(); i++) {
            JSONObject choice = choices.optJSONObject(i);
            int total = choice.optInt("channel_points_votes", 0) + choice.optInt("votes", 0);
            votes.put(choice.getString("title"), total);
        }
        return votes;
    }

    private String getEventType(JSONObject object) {
        if (object.has("metadata")) {
            JSONObject metadata = object.getJSONObject("metadata");
            if (metadata == null) {
                return null;
            } else if (!metadata.has("message_type")) {
                return null;
            }
            return metadata.getString("message_type");
        } else if (object.has("subscription")) {
            JSONObject subscription = object.getJSONObject("subscription");
            if (subscription == null) {
                return null;
            } else if (!subscription.has("type")) {
                return null;
            }
            return subscription.getString("type");
        }
        return null;
    }

    private String getWelcomeId(JSONObject object) {
        JSONObject metadata = object.getJSONObject("metadata");
        if (metadata == null) {
            return null;
        } else if (!metadata.has("message_type")) {
            return null;
        } else if (!metadata.getString("message_type").equals("session_welcome")) {
            return null;
        }
        JSONObject payload = object.getJSONObject("payload");
        if (payload == null) {
            return null;
        } else if (!payload.has("session")) {
            return null;
        }
        JSONObject session = payload.getJSONObject("session");
        if (session == null) {
            return null;
        } else if (!session.has("id")) {
            return null;
        }
        return session.getString("id");
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.warn("Disconnected from twitch ({} - {})", code, remote);
        this.id.set(null);
        if (remote) {
            this.state = SocketState.CONNECTING;
            this.scheduler.schedule(this::reconnect, 5, TimeUnit.SECONDS);
            this.publisher.publishEvent(new TwitchWsStateEvent(this.state));
        } else if (code >= 0) {
            this.state = SocketState.DISCONNECTED;
            this.publisher.publishEvent(new TwitchWsStateEvent(this.state));
        }
    }

    @Override
    public void onError(Exception exception) {
        this.id.set(null);
        if (exception instanceof ConnectException) {
            log.error("Twitch socket error (Reconnecting)", exception);
            this.state = SocketState.CONNECTING;
            this.scheduler.schedule(this::reconnect, 5, TimeUnit.SECONDS);
        } else {
            this.state = SocketState.DISCONNECTED;
            log.error("Twitch socket error (Giving up)", exception);
        }
        this.publisher.publishEvent(new TwitchWsStateEvent(this.state));
    }

}
