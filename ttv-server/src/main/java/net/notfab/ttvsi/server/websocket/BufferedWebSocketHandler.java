package net.notfab.ttvsi.server.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import net.jodah.expiringmap.ExpirationListener;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;
import net.notfab.ttvsi.common.Headers;
import net.notfab.ttvsi.common.NetworkEvent;
import net.notfab.ttvsi.common.WsCredentials;
import net.notfab.ttvsi.server.websocket.events.WsClosedEvent;
import net.notfab.ttvsi.server.websocket.events.WsMessageEvent;
import net.notfab.ttvsi.server.websocket.events.WsOpenEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class BufferedWebSocketHandler extends TextWebSocketHandler implements ExpirationListener<UUID, Queue<NetworkEvent>> {

    // Map client ID to active session
    private final Map<UUID, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // Map client ID to a queue of unsent messages with auto-expiration
    private final ExpiringMap<UUID, Queue<NetworkEvent>> buffers = ExpiringMap.builder()
            .expiration(30, TimeUnit.MINUTES)
            .expirationPolicy(ExpirationPolicy.CREATED)
            .expirationListener(this)
            .build();

    // Map client ID to joined room
    private final Map<String, Set<UUID>> rooms = new HashMap<>();

    private final ApplicationEventPublisher publisher;
    private final ObjectMapper mapper = new ObjectMapper();

    public BufferedWebSocketHandler(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void afterConnectionEstablished(@Nonnull WebSocketSession session) {
        WsCredentials credentials = this.getCredentials(session);
        if (credentials != null) {
            // Store the session
            sessions.put(credentials.clientId(), session);
            // Store the room
            rooms.computeIfAbsent(credentials.roomId(), k -> new HashSet<>())
                    .add(credentials.clientId());
            this.publisher.publishEvent(new WsOpenEvent(credentials));

            // Send any buffered messages to the client
            this.sendBufferedMessages(credentials.clientId());
        } else {
            try {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("Missing client ID"));
            } catch (IOException e) {
                // No need for logs
            }
        }
    }

    @Override
    protected void handleTextMessage(@Nonnull WebSocketSession session, @Nonnull TextMessage message) throws Exception {
        WsCredentials credentials = this.getCredentials(session);
        if (credentials == null) {
            return;
        }
        NetworkEvent event = mapper.readValue(message.getPayload(), NetworkEvent.class);
        if (event == null) {
            return;
        }
        this.publisher.publishEvent(new WsMessageEvent(credentials, event.getType(), event));
    }

    @Override
    public void afterConnectionClosed(@Nonnull WebSocketSession session, @Nonnull CloseStatus status) {
        WsCredentials credentials = this.getCredentials(session);
        if (credentials != null) {
            sessions.remove(credentials.clientId());
            this.publisher.publishEvent(new WsClosedEvent(credentials));
        }
    }

    /**
     * Send a message to a specific client.
     * If the client is disconnected, message will be buffered.
     */
    public void send(UUID clientId, NetworkEvent message) {
        WebSocketSession session = sessions.get(clientId);

        // Store message in buffer regardless of connection status
        Queue<NetworkEvent> buffer = buffers.computeIfAbsent(clientId,
                k -> new ConcurrentLinkedQueue<>());
        buffer.add(message);

        // If client is connected, send the message immediately
        if (session != null && session.isOpen()) {
            this.sendBufferedMessages(clientId);
        }
    }

    /**
     * Broadcast a message to all connected clients.
     * For disconnected clients, the message will be buffered.
     */
    public void broadcast(NetworkEvent message) {
        // Add message to all client buffers
        for (UUID clientId : buffers.keySet()) {
            buffers.get(clientId).add(message);
        }

        // Send to all connected clients
        for (UUID clientId : sessions.keySet()) {
            this.sendBufferedMessages(clientId);
        }
    }

    public void broadcast(String room, NetworkEvent message) {
        this.rooms.getOrDefault(room, new HashSet<>())
                .forEach(id -> this.send(id, message));
    }

    /**
     * Send all buffered messages to a client
     */
    private void sendBufferedMessages(UUID clientId) {
        WebSocketSession session = sessions.get(clientId);
        if (session == null || !session.isOpen()) {
            return;
        }
        Queue<NetworkEvent> buffer = buffers.get(clientId);
        if (buffer == null) {
            return;
        }
        while (!buffer.isEmpty()) {
            NetworkEvent message = buffer.peek();
            try {
                session.sendMessage(new TextMessage(mapper.writeValueAsString(message)));
                buffer.poll();
            } catch (IOException e) {
                break;
            }
        }
    }

    /**
     * Extract client ID from session attributes
     */
    private WsCredentials getCredentials(WebSocketSession session) {
        return (WsCredentials) session.getAttributes().get(Headers.X_CREDENTIALS);
    }

    /**
     * Delete expired room mappings
     */
    @Override
    public void expired(UUID uuid, Queue<NetworkEvent> buffer) {
        if (this.sessions.containsKey(uuid)) {
            return;
        }
        this.rooms.forEach((id, room) -> room.remove(uuid));
    }

}
