package net.notfab.ttvsi.client.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import net.notfab.ttvsi.common.NetworkEvent;
import net.notfab.ttvsi.common.polls.server.ServerCreatePollEvent;
import net.notfab.ttvsi.common.polls.server.ServerPollFinishedEvent;
import net.notfab.ttvsi.common.polls.server.ServerPollUpdateEvent;
import net.notfab.ttvsi.common.protocol.RoomMemberSyncEvent;
import net.notfab.ttvsi.common.rewards.RewardRedeemEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@EnableWebSocket
public class WebsocketHandler extends TextWebSocketHandler implements WebSocketConfigurer {

    private final Map<String, WebSocketSession> sessions = new HashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void registerWebSocketHandlers(@Nonnull WebSocketHandlerRegistry registry) {
        registry.addHandler(this, "websocket")
                .setAllowedOrigins("*");
    }

    @Override
    public void afterConnectionEstablished(@Nonnull WebSocketSession session) {
        this.sessions.put(session.getId(), session);
    }

    @Override
    public void afterConnectionClosed(@Nonnull WebSocketSession session, @Nonnull CloseStatus status) {
        this.sessions.remove(session.getId());
    }

    @EventListener
    public void onPollCreated(ServerCreatePollEvent event) {
        this.broadcast(event);
    }

    @EventListener
    public void onPollProgress(ServerPollUpdateEvent event) {
        this.broadcast(event);
    }

    @EventListener
    public void onPollFinished(ServerPollFinishedEvent event) {
        this.broadcast(event);
    }

    @EventListener
    public void onRewardRedeemed(RewardRedeemEvent event) {
        this.broadcast(event);
    }

    @EventListener
    public void onMembersSync(RoomMemberSyncEvent event) {
        this.broadcast(event);
    }

    private void broadcast(NetworkEvent event) {
        String message;
        try {
            message = this.mapper.writeValueAsString(event);
        } catch (Exception ex) {
            return;
        }
        this.sessions.values().forEach(session -> {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (Exception ex) {
                log.warn("Failed to send message to API session {}", session.getId(), ex);
            }
        });
    }

}
