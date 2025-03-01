package net.notfab.ttvsi.client.rest;

import net.notfab.ttvsi.client.advice.FormError;
import net.notfab.ttvsi.client.models.TwitchProfile;
import net.notfab.ttvsi.client.network.NetworkAPI;
import net.notfab.ttvsi.client.twitch.TwitchAPI;
import net.notfab.ttvsi.common.polls.Poll;
import net.notfab.ttvsi.common.polls.client.ClientRequestedPollCreateEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PollRest {

    private final NetworkAPI network;
    private final TwitchAPI twitch;

    public PollRest(NetworkAPI network, TwitchAPI twitch) {
        this.network = network;
        this.twitch = twitch;
    }

    @PostMapping("polls")
    public void createPoll(@RequestBody Poll poll) {
        if (poll.getDuration() < 15) {
            throw new FormError("Duration must be at least 15 seconds");
        } else if (poll.getChoices() == null || poll.getChoices().isEmpty()) {
            throw new FormError("Poll is missing choices");
        } else if (poll.getChoices().size() > 5) {
            throw new FormError("Poll must have 5 or less choices");
        } else if (poll.getTitle() == null || poll.getTitle().isBlank()) {
            throw new FormError("Poll is missing a title");
        }
        TwitchProfile profile = this.twitch.getProfile();
        if (profile == null) {
            return;
        }
        this.network.publish(new ClientRequestedPollCreateEvent(poll, profile.getChannelName()));
    }

}
