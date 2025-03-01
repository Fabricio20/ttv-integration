package net.notfab.ttvsi.common;

import java.util.UUID;

public record WsCredentials(UUID clientId, String twitchId, String roomId) {
}
