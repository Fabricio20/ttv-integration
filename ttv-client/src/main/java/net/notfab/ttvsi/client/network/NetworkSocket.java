package net.notfab.ttvsi.client.network;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.notfab.ttvsi.client.models.SocketState;
import net.notfab.ttvsi.client.models.events.NetworkStateEvent;
import net.notfab.ttvsi.common.Headers;
import net.notfab.ttvsi.common.NetworkEvent;
import net.notfab.ttvsi.common.WsCredentials;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.context.ApplicationEventPublisher;

import java.net.ConnectException;
import java.net.URI;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class NetworkSocket extends WebSocketClient {

    private final ApplicationEventPublisher publisher;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Queue<String> buffer = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @Getter
    private SocketState state;

    public NetworkSocket(
            URI uri,
            ApplicationEventPublisher publisher,
            WsCredentials credentials) {
        super(uri);
        this.publisher = publisher;
        this.addHeader(Headers.X_CLIENT_ID, credentials.clientId().toString());
        this.addHeader(Headers.X_TWITCH_ID, credentials.twitchId());
        this.addHeader(Headers.X_ROOM_ID, credentials.roomId());
        this.setConnectionLostTimeout(10);
        this.state = SocketState.DISCONNECTED;
        this.publisher.publishEvent(new NetworkStateEvent(this.state));
    }

    /**
     * Terminates a connection and any reconnection attempts
     */
    public void terminate() {
        this.scheduler.shutdownNow();
        this.close();
        this.state = SocketState.DISCONNECTED;
        this.publisher.publishEvent(new NetworkStateEvent(this.state));
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        this.state = SocketState.CONNECTED;
        this.publisher.publishEvent(new NetworkStateEvent(this.state));
        this.sendBuffer();
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.warn("Disconnected from server ({} - {})", code, remote);
        if (remote) {
            this.state = SocketState.CONNECTING;
            this.scheduler.schedule(this::reconnect, 5, TimeUnit.SECONDS);
            this.publisher.publishEvent(new NetworkStateEvent(this.state));
        } else if (code >= 0) {
            this.state = SocketState.DISCONNECTED;
            this.publisher.publishEvent(new NetworkStateEvent(this.state));
        }
    }

    @Override
    public void onMessage(String message) {
        log.debug("Received from Network: {}", message);
        try {
            NetworkEvent event = mapper.readValue(message, NetworkEvent.class);
            this.publisher.publishEvent(event);
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse network message", ex);
        }
    }

    @Override
    public void onError(Exception exception) {
        if (exception instanceof ConnectException) {
            log.error("Socket error (Reconnecting)", exception);
            this.state = SocketState.CONNECTING;
            this.scheduler.schedule(this::reconnect, 5, TimeUnit.SECONDS);
        } else {
            this.state = SocketState.DISCONNECTED;
            log.error("Socket error (Giving up)", exception);
        }
        this.publisher.publishEvent(new NetworkStateEvent(this.state));
    }

    public void emit(NetworkEvent payload) {
        String message;
        try {
            message = this.mapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            log.error("Failed to process network event", ex);
            return;
        }
        buffer.add(message);
        if (this.state == SocketState.CONNECTED) {
            this.sendBuffer();
        }
    }

    private void sendBuffer() {
        if (buffer.isEmpty()) {
            return;
        }
        do {
            String message = buffer.peek();
            this.send(message);
            buffer.poll();
        } while (!buffer.isEmpty());
    }

}
