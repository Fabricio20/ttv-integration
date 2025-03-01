package net.notfab.ttvsi.server.websocket.events;

import net.notfab.ttvsi.common.NetEvent;
import net.notfab.ttvsi.common.NetworkEvent;
import net.notfab.ttvsi.common.WsCredentials;

public record WsMessageEvent(WsCredentials credentials, NetEvent event, NetworkEvent payload) {
}
