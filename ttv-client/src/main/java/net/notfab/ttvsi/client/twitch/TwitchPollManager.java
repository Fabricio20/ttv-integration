package net.notfab.ttvsi.client.twitch;

import lombok.extern.slf4j.Slf4j;
import net.notfab.ttvsi.client.models.TwitchProfile;
import net.notfab.ttvsi.client.models.twitch.TwitchPollFinishedEvent;
import net.notfab.ttvsi.client.models.twitch.TwitchPollProgressedEvent;
import net.notfab.ttvsi.client.network.NetworkAPI;
import net.notfab.ttvsi.common.polls.Choice;
import net.notfab.ttvsi.common.polls.Poll;
import net.notfab.ttvsi.common.polls.client.ClientPollFinishedEvent;
import net.notfab.ttvsi.common.polls.client.ClientPollProgressEvent;
import net.notfab.ttvsi.common.polls.server.ServerCreatePollEvent;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class TwitchPollManager {

    private final TwitchAPI api;
    private final Map<String, Poll> registered = new HashMap<>();
    private final NetworkAPI network;

    public TwitchPollManager(TwitchAPI api, NetworkAPI network) {
        this.api = api;
        this.network = network;
    }

    /**
     * Poll was created by the server, propagate to twitch
     */
    @EventListener
    public void onPollCreateRequested(ServerCreatePollEvent event) {
        Poll poll = event.poll();
        if (this.isPollRegistered(poll.getId())) {
            log.warn("Poll {} is already registered", poll.getId());
            return;
        }
        boolean created = this.create(event.poll());
        if (!created) {
            log.error("Poll {} failed to create", poll.getId());
        } else {
            log.info("Poll {} was created on twitch.tv", poll.getId());
        }
    }

    private boolean isPollRegistered(String id) {
        for (Poll value : this.registered.values()) {
            if (value.getId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    @EventListener
    public void onTwitchPollProgress(TwitchPollProgressedEvent event) {
        Poll poll = this.registered.get(event.id());
        if (poll == null) {
            log.warn("Received progress update for unknown poll {}", event.id());
            return;
        }
        Map<String, Integer> votes = new HashMap<>();
        event.votes().forEach((title, total) -> {
            for (Choice choice : poll.getChoices()) {
                if (choice.getTitle().equalsIgnoreCase(title)) {
                    votes.put(choice.getId(), total);
                }
            }
        });
        ClientPollProgressEvent progress =
                new ClientPollProgressEvent(poll.getId(), api.getProfile().getChannelName(), votes);
        this.network.publish(progress);
        log.info("Poll {} received a progress update on twitch.tv", poll.getId());
    }

    @EventListener
    public void onTwitchPollFinished(TwitchPollFinishedEvent event) {
        Poll poll = this.registered.get(event.id());
        if (poll == null) {
            log.warn("Received finish event for unknown poll {}", event.id());
            return;
        }
        Map<String, Integer> votes = new HashMap<>();
        event.votes().forEach((title, total) -> {
            for (Choice choice : poll.getChoices()) {
                if (choice.getTitle().equalsIgnoreCase(title)) {
                    votes.put(choice.getId(), total);
                }
            }
        });
        ClientPollFinishedEvent progress =
                new ClientPollFinishedEvent(poll.getId(), api.getProfile().getChannelName(), votes);
        this.network.publish(progress);
        log.info("Poll {} has finished on twitch.tv", poll.getId());
    }

    /**
     * Creates a poll on twitch and starts tracking its progress
     */
    private boolean create(Poll poll) {
        TwitchProfile profile = api.getProfile();
        if (profile == null) {
            return false;
        }

        // "broadcaster_id":"141981764",
        //  "title":"Heads or Tails?",
        //  "choices":[{
        //    "title":"Heads"
        //  },
        //  {
        //    "title":"Tails"
        //  }],
        //  "channel_points_voting_enabled":true,
        //  "channel_points_per_vote":100,
        //  "duration":1800

        JSONObject object = new JSONObject();
        object.put("broadcaster_id", profile.getBroadcasterId());
        object.put("title", poll.getTitle());
        object.put("duration", poll.getDuration());
        if (poll.getPoints() != null) {
            object.put("channel_points_voting_enabled", true);
            object.put("channel_points_per_vote", poll.getPoints());
        }
        JSONArray choices = new JSONArray();
        for (Choice choice : poll.getChoices()) {
            JSONObject data = new JSONObject();
            data.put("title", choice.getTitle());
            choices.put(data);
        }
        object.put("choices", choices);

        Request request = new Request.Builder()
                .url("https://api.twitch.tv/helix/polls")
                .header("Authorization", "Bearer " + profile.getAccessToken())
                .header("Client-Id", TwitchAPI.TWITCH_APP_CLIENT_ID)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(object.toString(), MediaType.parse("application/json")))
                .build();
        try (Response response = this.api.getHttp().newCall(request).execute(); ResponseBody body = response.body()) {
            if (body == null) {
                return false;
            } else if (!response.isSuccessful()) {
                log.error("Failed to create poll on twitch.tv - {}", body.string());
                return false;
            }
            JSONObject json = new JSONObject(body.string());
            if (!json.has("data")) {
                return false;
            }
            JSONArray data = json.getJSONArray("data");
            if (data.isEmpty()) {
                return false;
            }
            JSONObject item = data.getJSONObject(0);
            this.registered.put(item.getString("id"), poll);
        } catch (Exception ex) {
            log.error("Failed to create poll on twitch.tv", ex);
            return false;
        }
        return true;
    }

}
