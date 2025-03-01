package net.notfab.ttvsi.client.models.events;

import net.notfab.ttvsi.client.models.SocketState;

/**
 * Fired when twitch websocket state changed
 */
public record TwitchWsStateEvent(SocketState state) {
}
