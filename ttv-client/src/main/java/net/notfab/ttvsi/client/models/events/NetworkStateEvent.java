package net.notfab.ttvsi.client.models.events;

import net.notfab.ttvsi.client.models.SocketState;

/**
 * Fired when network state changed
 */
public record NetworkStateEvent(SocketState state) {
}
