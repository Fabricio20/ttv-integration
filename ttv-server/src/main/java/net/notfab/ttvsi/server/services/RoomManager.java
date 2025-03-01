package net.notfab.ttvsi.server.services;

import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.notfab.ttvsi.common.WsCredentials;
import net.notfab.ttvsi.common.protocol.RoomMemberSyncEvent;
import net.notfab.ttvsi.server.models.Room;
import net.notfab.ttvsi.server.websocket.BufferedWebSocketHandler;
import net.notfab.ttvsi.server.websocket.events.WsClosedEvent;
import net.notfab.ttvsi.server.websocket.events.WsOpenEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class RoomManager {

    private final BufferedWebSocketHandler server;
    private final Map<String, Room> rooms = new HashMap<>();

    public RoomManager(BufferedWebSocketHandler server) {
        this.server = server;
    }

    @EventListener
    public void onConnected(WsOpenEvent event) {
        WsCredentials credentials = event.credentials();
        if (credentials == null) {
            return;
        }
        log.info("{} connected to room {}", credentials.twitchId(), credentials.roomId());
        // --- Send room data
        Room room = rooms.computeIfAbsent(credentials.roomId(), k -> new Room(credentials.roomId()));
        room.getMembers().add(credentials.twitchId());
        this.server.broadcast(room.getId(), new RoomMemberSyncEvent(room.getMembers()));
    }

    @EventListener
    public void onDisconnected(WsClosedEvent event) {
        WsCredentials credentials = event.credentials();
        if (credentials == null) {
            return;
        }
        log.info("{} disconnected from room {}", credentials.twitchId(), credentials.roomId());
        // --- Send room data
        Room room = rooms.get(credentials.roomId());
        if (room == null) {
            return;
        }
        room.getMembers().remove(credentials.twitchId());
        this.server.broadcast(room.getId(), new RoomMemberSyncEvent(room.getMembers()));
    }

    public @Nullable Room get(@Nullable String roomId) {
        if (roomId == null) {
            return null;
        }
        return this.rooms.get(roomId);
    }

}
