package net.notfab.ttvsi.client.models.events;

/**
 * Fired when twitch websocket was authenticated
 */
public record TwitchEstablishedEvent(String session) {
}
