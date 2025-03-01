package net.notfab.ttvsi.client.models.events;

import net.notfab.ttvsi.client.models.TwitchProfile;

/**
 * Fired when twitch oauth flow finished
 */
public record TwitchAuthorizedEvent(TwitchProfile profile) {
}
