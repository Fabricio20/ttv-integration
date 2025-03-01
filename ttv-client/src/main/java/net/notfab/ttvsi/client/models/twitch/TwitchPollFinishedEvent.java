package net.notfab.ttvsi.client.models.twitch;

import java.util.Map;

/**
 * Twitch says a poll finished
 *
 * @param id    Twitch Poll ID
 * @param votes Votes
 */
public record TwitchPollFinishedEvent(String id, Map<String, Integer> votes) {
}
