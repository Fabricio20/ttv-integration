package net.notfab.ttvsi.client.models.twitch;

import java.util.Map;

/**
 * Twitch says a poll updated
 *
 * @param id    Twitch Poll ID
 * @param votes Votes
 */
public record TwitchPollProgressedEvent(String id, Map<String, Integer> votes) {
}
