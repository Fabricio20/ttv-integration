package net.notfab.ttvsi.server.websocket;

import jakarta.annotation.Nonnull;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebsocketConfig implements WebSocketConfigurer {

    private final BufferedWebSocketHandler handler;
    private final WebsocketInterceptor interceptor;

    public WebsocketConfig(BufferedWebSocketHandler handler, WebsocketInterceptor interceptor) {
        this.handler = handler;
        this.interceptor = interceptor;
    }

    @Override
    public void registerWebSocketHandlers(@Nonnull WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "websocket")
                .addInterceptors(interceptor)
                .setAllowedOrigins("*");
    }

}
