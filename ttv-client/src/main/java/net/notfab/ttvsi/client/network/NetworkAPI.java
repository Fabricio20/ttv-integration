package net.notfab.ttvsi.client.network;

import lombok.extern.slf4j.Slf4j;
import net.notfab.ttvsi.client.models.SocketState;
import net.notfab.ttvsi.client.models.TwitchProfile;
import net.notfab.ttvsi.client.models.events.TwitchTerminateEvent;
import net.notfab.ttvsi.client.twitch.TwitchAPI;
import net.notfab.ttvsi.common.NetworkEvent;
import net.notfab.ttvsi.common.WsCredentials;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Slf4j
@Service
public class NetworkAPI {

    private final ApplicationEventPublisher events;
    private final TwitchAPI twitch;
    private final UUID clientId;

    private NetworkSocket socket;

    public NetworkAPI(ApplicationEventPublisher events, TwitchAPI twitch) {
        this.events = events;
        this.twitch = twitch;
        this.clientId = this.getClientId();
    }

    @EventListener
    public void onTerminate(TwitchTerminateEvent event) {
        this.disconnect();
    }

    private UUID getClientId() {
        File file = new File("client_id.txt");
        try {
            if (!file.exists()) {
                UUID uuid = UUID.randomUUID();
                Files.writeString(file.toPath(), uuid.toString(),
                        StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
                log.info("New client id: {}", uuid);
                return uuid;
            } else {
                String content = new String(Files.readAllBytes(file.toPath()));
                log.info("Reusing client id: {}", content);
                return UUID.fromString(content);
            }
        } catch (IOException ex) {
            log.error("Failed to generate permanent client id", ex);
            return UUID.randomUUID();
        }
    }

    public boolean isConnecting() {
        return this.socket != null &&
               (this.socket.getState() == SocketState.CONNECTED || this.socket.getState() == SocketState.CONNECTING);
    }

    public void connect(String url, String code) {
        TwitchProfile profile = this.twitch.getProfile();
        if (profile == null) {
            return;
        }
        WsCredentials credentials = new WsCredentials(
                this.clientId, profile.getChannelName(), code);
        this.socket = new NetworkSocket(URI.create(url), this.events, credentials);
        this.socket.connect();
    }

    public void disconnect() {
        if (this.socket == null) {
            return;
        }
        this.socket.terminate();
        this.socket = null;
    }

    public void publish(NetworkEvent event) {
        if (this.socket == null) {
            log.warn("Attempted to publish message but socket is null");
            return;
        }
        this.socket.emit(event);
    }

}
