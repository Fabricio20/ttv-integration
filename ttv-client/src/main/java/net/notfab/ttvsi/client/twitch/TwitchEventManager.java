package net.notfab.ttvsi.client.twitch;

import lombok.extern.slf4j.Slf4j;
import net.notfab.ttvsi.client.models.TwitchCallbackDetails;
import net.notfab.ttvsi.client.models.TwitchProfile;
import net.notfab.ttvsi.client.models.events.TwitchAuthorizedEvent;
import net.notfab.ttvsi.client.models.events.TwitchEstablishedEvent;
import net.notfab.ttvsi.client.models.events.TwitchTerminateEvent;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TwitchEventManager {

    private final TwitchAPI api;
    private final ApplicationEventPublisher publisher;
    private final Set<String> events;

    private TwitchWebsocket socket;

    public TwitchEventManager(TwitchAPI api, ApplicationEventPublisher events) {
        this.api = api;
        this.publisher = events;
        this.events = new HashSet<>();
        this.events.add("channel.poll.progress");
        this.events.add("channel.poll.end");
        this.events.add("channel.channel_points_custom_reward_redemption.add");
    }

    @EventListener
    public void onTerminate(TwitchTerminateEvent event) {
        if (this.socket == null) {
            return;
        }
        this.socket.terminate();
        this.socket = null;
    }

    @EventListener
    public void onTwitchAuthorized(TwitchAuthorizedEvent event) {
        if (this.socket != null) {
            log.info("Disconnecting old websocket");
            this.socket.terminate();
        }
        this.socket = new TwitchWebsocket(this.publisher);
        this.socket.connect();
        log.info("Started twitch websocket connection");
    }

    @EventListener
    public void onSocketConnected(TwitchEstablishedEvent event) {
        List<TwitchCallbackDetails> subscriptions = this.getActiveSubscriptions();
        for (TwitchCallbackDetails subscription : subscriptions) {
            if (!subscription.isActive()) {
                log.warn("Subscription {} is not active: {}", subscription.getId(), subscription.getStatus());
                this.unregister(subscription);
            }
        }
        Set<String> active = subscriptions.stream()
                .filter(TwitchCallbackDetails::isActive)
                .map(TwitchCallbackDetails::getType)
                .collect(Collectors.toSet());
        Set<String> events = new HashSet<>(this.events);
        events.removeAll(active);
        events.stream()
                .filter(name -> !this.register(name, 1, event.session()))
                .map(name -> "Failed to register for " + name + " events!")
                .forEach(log::error);
    }

    private void unregister(TwitchCallbackDetails subscription) {
        TwitchProfile profile = api.getProfile();
        if (profile == null) {
            return;
        } else if (!api.getDestructive().get()) {
            log.info("Skipped deleting callback ({}) because destructive actions are disabled", subscription.getId());
            return;
        }
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse("https://api.twitch.tv/helix/eventsub/subscriptions"))
                .newBuilder()
                .addQueryParameter("id", subscription.getId())
                .build();
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + profile.getAccessToken())
                .header("Client-Id", TwitchAPI.TWITCH_APP_CLIENT_ID)
                .delete().build();
        try (Response response = this.api.getHttp().newCall(request).execute()) {
            boolean success = response.isSuccessful();
            if (success) {
                log.info("Removed failed subscription {}", subscription.getId());
            }
        } catch (IOException ex) {
            log.error("Failed to remove old subscription", ex);
        }
    }

    private boolean register(String event, int version, String session) {
        TwitchProfile profile = api.getProfile();
        if (profile == null) {
            return false;
        }

        JSONObject condition = new JSONObject();
        condition.put("broadcaster_user_id", profile.getBroadcasterId());

        JSONObject object = new JSONObject();
        object.put("type", event);
        object.put("version", version);
        object.put("condition", condition);
        object.put("transport", new JSONObject().put("method", "websocket").put("session_id", session));

        Request request = new Request.Builder()
                .url("https://api.twitch.tv/helix/eventsub/subscriptions")
                .header("Authorization", "Bearer " + profile.getAccessToken())
                .header("Client-Id", TwitchAPI.TWITCH_APP_CLIENT_ID)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(object.toString().getBytes(StandardCharsets.UTF_8)))
                .build();
        try (Response response = this.api.getHttp().newCall(request).execute()) {
            boolean success = response.isSuccessful();
            if (success) {
                log.info("Registered for {} events", event);
            }
            return success;
        } catch (IOException ex) {
            log.error("Failed to register event listener", ex);
            return false;
        }
    }

    private List<TwitchCallbackDetails> getActiveSubscriptions() {
        TwitchProfile profile = api.getProfile();
        if (profile == null) {
            return new ArrayList<>();
        }
        Request request = new Request.Builder()
                .url("https://api.twitch.tv/helix/eventsub/subscriptions")
                .header("Authorization", "Bearer " + profile.getAccessToken())
                .header("Client-Id", TwitchAPI.TWITCH_APP_CLIENT_ID)
                .header("Content-Type", "application/json")
                .get().build();
        try (Response response = this.api.getHttp().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return new ArrayList<>();
            }
            try (ResponseBody body = response.body()) {
                if (body == null) {
                    return new ArrayList<>();
                }
                JSONObject object = new JSONObject(body.string());
                if (!object.has("total")) {
                    return new ArrayList<>();
                }
                List<TwitchCallbackDetails> details = new ArrayList<>();
                JSONArray data = object.getJSONArray("data");
                for (int i = 0; i < data.length(); i++) {
                    JSONObject sub = data.getJSONObject(i);
                    TwitchCallbackDetails detail = new TwitchCallbackDetails();
                    detail.setId(sub.getString("id"));
                    detail.setType(sub.getString("type"));
                    detail.setStatus(sub.getString("status"));
                    details.add(detail);
                }
                return details;
            }
        } catch (IOException ex) {
            log.error("Failed to fetch active event listeners", ex);
            return new ArrayList<>();
        }
    }

}
