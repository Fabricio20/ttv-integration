package net.notfab.ttvsi.server.websocket;

import jakarta.annotation.Nonnull;
import net.notfab.ttvsi.common.Headers;
import net.notfab.ttvsi.common.WsCredentials;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

@Component
public class WebsocketInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            @Nonnull ServerHttpResponse response,
            @Nonnull WebSocketHandler wsHandler,
            @Nonnull Map<String, Object> attributes) {

        String clientId = this.getHeader(Headers.X_CLIENT_ID, request.getHeaders());
        if (clientId == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(Headers.X_CLIENT_ID, clientId);

        String twitchId = this.getHeader(Headers.X_TWITCH_ID, request.getHeaders());
        if (twitchId == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(Headers.X_TWITCH_ID, twitchId);

        String roomId = this.getHeader(Headers.X_ROOM_ID, request.getHeaders());
        if (roomId == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(Headers.X_ROOM_ID, roomId);

        // Store them all in a credentials attribute
        attributes.put(Headers.X_CREDENTIALS, new WsCredentials(UUID.fromString(clientId), twitchId, roomId));
        return true;
    }

    @Override
    public void afterHandshake(
            @Nonnull ServerHttpRequest request,
            @Nonnull ServerHttpResponse response,
            @Nonnull WebSocketHandler wsHandler,
            Exception exception) {
        // No actions needed after handshake
    }

    private String getHeader(String name, HttpHeaders headers) {
        List<String> values = headers.get(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.getFirst();
    }

    private Map<String, String> parseQuery(URI uri) {
        Map<String, List<String>> list = Pattern.compile("&")
                .splitAsStream(uri.getQuery())
                .map(s -> Arrays.copyOf(s.split("=", 2), 2))
                .collect(groupingBy(s -> decode(s[0]),
                        Collectors.mapping(s -> decode(s[1]), toList())));
        Map<String, String> value = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : list.entrySet()) {
            value.put(entry.getKey(), entry.getValue().getFirst());
        }
        return value;
    }

    private String decode(final String encoded) {
        return Optional.ofNullable(encoded)
                .map(e -> URLDecoder.decode(e, StandardCharsets.UTF_8))
                .orElse(null);
    }

}
