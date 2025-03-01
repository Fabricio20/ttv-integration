package net.notfab.ttvsi.client.twitch;

import lombok.extern.slf4j.Slf4j;
import net.notfab.ttvsi.client.models.TwitchProfile;
import net.notfab.ttvsi.client.models.events.TwitchTerminateEvent;
import net.notfab.ttvsi.client.models.twitch.TwitchRewardEvent;
import net.notfab.ttvsi.client.network.NetworkAPI;
import net.notfab.ttvsi.common.rewards.Reward;
import net.notfab.ttvsi.common.rewards.RewardRedeemEvent;
import net.notfab.ttvsi.common.rewards.server.RewardSyncEvent;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class TwitchRewardManager {

    private final TwitchAPI api;
    private final Map<String, Reward> registered = new HashMap<>();
    private final Set<String> cache = new HashSet<>();
    private final NetworkAPI network;

    public TwitchRewardManager(TwitchAPI api, NetworkAPI network) {
        this.api = api;
        this.network = network;
    }

    @EventListener
    public void onTerminate(TwitchTerminateEvent event) {
        if (!api.getDestructive().get()) {
            return;
        }
        this.onRewardSync(new RewardSyncEvent(new ArrayList<>()));
    }

    /**
     * Server sent a reward sync request, update all rewards on twitch
     */
    @EventListener
    public void onRewardSync(RewardSyncEvent event) {
        // <title, id>
        Map<String, String> active = this.fetchActiveRewards();
        if (active.isEmpty()) {
            log.info("No rewards are registered on twitch");
        }
        Set<String> registered = new HashSet<>();
        for (Reward reward : event.rewards()) {
            String twitch = active.get(reward.getTitle());
            if (twitch == null) {
                twitch = this.create(reward);
                if (twitch == null) {
                    log.warn("Reward {} failed to register due to error", reward.getId());
                    continue;
                }
            }
            this.registered.put(twitch, reward);
            registered.add(twitch);
        }
        log.info("Registered {} rewards on twitch", registered.size());
        active.values().stream()
                .filter(broken -> !registered.contains(broken))
                .forEach(this::delete);
        log.info("Removed {} rewards from twitch", active.size() - registered.size());
    }

    /**
     * User redeemed a reward, send it upstream
     */
    @EventListener
    public void onRewardRedeemed(TwitchRewardEvent event) {
        Reward reward = this.registered.get(event.reward());
        if (reward == null) {
            log.warn("Invalid reward redeemed ({}) [{}]", event.reward(), event.user_code());
            return;
        } else if (!cache.add(event.id())) {
            log.warn("Ignored duplicate reward");
            return;
        }
        RewardRedeemEvent redeem = new RewardRedeemEvent();
        redeem.setId(event.reward());
        redeem.setReward(reward.getId());
        redeem.setUserId(event.user_code());
        redeem.setUserName(event.user_name());
        redeem.setChannel(api.getProfile().getChannelName());
        if (event.input() != null && !event.input().isBlank()) {
            redeem.setInput(event.input());
        }
        this.network.publish(redeem);
        log.info("Reward {} was redeemed by {}", reward.getId(), redeem.getUserId());
    }

    /**
     * Deletes a custom reward from twitch
     */
    private void delete(String reward) {
        TwitchProfile profile = api.getProfile();
        if (profile == null) {
            return;
        } else if (!api.getDestructive().get()) {
            log.info("Skipped deleting reward ({}) because destructive actions are disabled", reward);
            return;
        }
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse("https://api.twitch.tv/helix/channel_points/custom_rewards"))
                .newBuilder()
                .addQueryParameter("broadcaster_id", profile.getBroadcasterId())
                .addQueryParameter("id", reward)
                .build();
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + profile.getAccessToken())
                .header("Client-Id", TwitchAPI.TWITCH_APP_CLIENT_ID)
                .header("Content-Type", "application/json")
                .delete().build();
        try (Response response = this.api.getHttp().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("Failed to delete reward from twitch (code {})", response.code());
            }
        } catch (Exception ex) {
            log.error("Failed to delete reward from twitch", ex);
        }
    }

    /**
     * Fetches all active rewards on twitch, returning a map of title, id.
     */
    private @NotNull Map<String, String> fetchActiveRewards() {
        TwitchProfile profile = api.getProfile();
        if (profile == null) {
            return new HashMap<>();
        }
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse("https://api.twitch.tv/helix/channel_points/custom_rewards"))
                .newBuilder()
                .addQueryParameter("broadcaster_id", profile.getBroadcasterId())
                .addQueryParameter("only_manageable_rewards", "true")
                .build();
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + profile.getAccessToken())
                .header("Client-Id", TwitchAPI.TWITCH_APP_CLIENT_ID)
                .header("Content-Type", "application/json")
                .get().build();
        try (Response response = this.api.getHttp().newCall(request).execute(); ResponseBody body = response.body()) {
            if (body == null) {
                return new HashMap<>();
            } else if (!response.isSuccessful()) {
                log.error("Failed to fetch active rewards: {}", body.string());
                return new HashMap<>();
            }
            JSONObject twitch = new JSONObject(body.string());
            if (!twitch.has("data")) {
                JSONArray data = twitch.getJSONArray("data");
                if (data.isEmpty()) {
                    return new HashMap<>();
                }
                Map<String, String> registered = new HashMap<>();
                for (int i = 0; i < data.length(); i++) {
                    JSONObject item = data.getJSONObject(i);
                    registered.put(item.getString("title"), item.getString("id"));
                }
                return registered;
            }
        } catch (Exception ex) {
            log.error("Failed to fetch rewards from twitch", ex);
            return new HashMap<>();
        }
        return new HashMap<>();
    }

    /**
     * Registers a custom channel point reward on twitch
     */
    private String create(Reward reward) {
        TwitchProfile profile = api.getProfile();
        if (profile == null) {
            return null;
        }
        JSONObject object = new JSONObject();
        object.put("title", reward.getTitle());
        object.put("cost", reward.getCost());
        object.put("should_redemptions_skip_request_queue", true);
        if (reward.getPrompt() != null && !reward.getPrompt().isBlank()) {
            object.put("is_user_input_required", true);
            object.put("prompt", reward.getPrompt());
        }
        if (reward.getColor() != null) {
            object.put("background_color", reward.getColor());
        }
        if (reward.getLimitPerStream() != null && reward.getLimitPerStream() > 0) {
            object.put("is_max_per_stream_enabled", true);
            object.put("max_per_stream", reward.getLimitPerStream());
        }
        if (reward.getLimitPerUser() != null && reward.getLimitPerUser() > 0) {
            object.put("is_max_per_user_per_stream_enabled", true);
            object.put("max_per_user_per_stream", reward.getLimitPerUser());
        }
        if (reward.getCooldown() != null && reward.getCooldown() > 1) {
            object.put("is_global_cooldown_enabled", true);
            object.put("global_cooldown_seconds", reward.getCooldown());
        }
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse("https://api.twitch.tv/helix/channel_points/custom_rewards"))
                .newBuilder()
                .addQueryParameter("broadcaster_id", profile.getBroadcasterId())
                .build();
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + profile.getAccessToken())
                .header("Client-Id", TwitchAPI.TWITCH_APP_CLIENT_ID)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(object.toString(), MediaType.parse("application/json")))
                .build();
        try (Response response = this.api.getHttp().newCall(request).execute(); ResponseBody body = response.body()) {
            if (body == null) {
                return null;
            } else if (!response.isSuccessful()) {
                log.error("Failed to register reward: {}", body.string());
                return null;
            }
            JSONObject created = new JSONObject(body.string());
            if (!created.has("data")) {
                JSONArray data = created.getJSONArray("data");
                if (data.isEmpty()) {
                    return null;
                }
                JSONObject returned = data.getJSONObject(0);
                return returned.getString("id");
            }
        } catch (Exception ex) {
            log.error("Failed to create reward on twitch.tv", ex);
            return null;
        }
        return null;
    }

}
