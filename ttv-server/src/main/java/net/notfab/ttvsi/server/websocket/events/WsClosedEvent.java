package net.notfab.ttvsi.server.websocket.events;

import net.notfab.ttvsi.common.WsCredentials;

public record WsClosedEvent(WsCredentials credentials) {
}
