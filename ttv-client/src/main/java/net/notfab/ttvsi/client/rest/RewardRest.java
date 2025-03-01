package net.notfab.ttvsi.client.rest;

import net.notfab.ttvsi.client.advice.FormError;
import net.notfab.ttvsi.client.models.TwitchProfile;
import net.notfab.ttvsi.client.network.NetworkAPI;
import net.notfab.ttvsi.client.twitch.TwitchAPI;
import net.notfab.ttvsi.common.rewards.Reward;
import net.notfab.ttvsi.common.rewards.client.ClientCreateRewardEvent;
import net.notfab.ttvsi.common.rewards.client.ClientDeleteRewardEvent;
import net.notfab.ttvsi.common.rewards.client.ClientUpdateRewardsEvent;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class RewardRest {

    private final NetworkAPI network;
    private final TwitchAPI twitch;

    public RewardRest(NetworkAPI network, TwitchAPI twitch) {
        this.network = network;
        this.twitch = twitch;
    }

    @PostMapping("rewards")
    public void createReward(@RequestBody Reward reward) {
        if (reward.getId() == null || reward.getId().isBlank()) {
            throw new FormError("Reward is missing an ID");
        } else if (reward.getTitle() == null || reward.getTitle().isBlank()) {
            throw new FormError("Reward is missing a title");
        }
        TwitchProfile profile = this.twitch.getProfile();
        if (profile == null) {
            return;
        }
        this.network.publish(new ClientCreateRewardEvent(profile.getChannelName(), reward));
    }

    @PutMapping("rewards")
    public void setRewards(@RequestBody List<Reward> rewards) {
        if (rewards.size() > 30) {
            throw new FormError("Reward limit reached");
        }
        for (Reward reward : rewards) {
            if (reward.getId() == null || reward.getId().isBlank()) {
                throw new FormError("Reward is missing an ID");
            } else if (reward.getTitle() == null || reward.getTitle().isBlank()) {
                throw new FormError("Reward " + reward.getId() + " is missing a title");
            }
            // Other validations?
        }
        TwitchProfile profile = this.twitch.getProfile();
        if (profile == null) {
            return;
        }
        ClientUpdateRewardsEvent event = new ClientUpdateRewardsEvent();
        event.setChannel(profile.getChannelName());
        event.setRewards(rewards);
        this.network.publish(event);
    }

    @DeleteMapping("rewards/{id}")
    public void deleteRewards(@PathVariable("id") String reward) {
        TwitchProfile profile = this.twitch.getProfile();
        if (profile == null) {
            return;
        }
        this.network.publish(new ClientDeleteRewardEvent(reward, profile.getChannelName()));
    }

}
