package net.notfab.ttvsi.client.twitch;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.notfab.ttvsi.client.models.TwitchProfile;
import net.notfab.ttvsi.client.models.TwitchResponse;
import net.notfab.ttvsi.client.models.events.TwitchAuthorizedEvent;
import net.notfab.ttvsi.client.models.events.TwitchTerminateEvent;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
public class TwitchOAuthManager {

    @Getter
    private final TwitchAPI api;
    private final ApplicationEventPublisher events;
    private final Set<String> scopes;

    public TwitchOAuthManager(TwitchAPI api, ApplicationEventPublisher events) {
        this.api = api;
        this.events = events;
        this.scopes = new HashSet<>();
        this.scopes.add("channel:manage:polls");
        this.scopes.add("channel:read:polls");
        this.scopes.add("channel:manage:redemptions");
        log.info("Twitch OAuth URL: {}", this.getOAuthUrl());
    }

    public String getOAuthUrl() {
        return Objects.requireNonNull(HttpUrl.parse("https://id.twitch.tv/oauth2/authorize"))
                .newBuilder()
                .addQueryParameter("response_type", "token")
                .addQueryParameter("client_id", TwitchAPI.TWITCH_APP_CLIENT_ID)
                .addQueryParameter("redirect_uri", "http://localhost:5583")
                .addQueryParameter("scope", String.join(" ", this.scopes))
                .build().url().toExternalForm();
    }

    public boolean validate(String code) {
        Request request = new Request.Builder()
                .url("https://id.twitch.tv/oauth2/validate")
                .header("Authorization", "OAuth " + code)
                .build();
        try (Response response = this.api.getHttp().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return false;
            }
            try (ResponseBody body = response.body()) {
                if (body == null) {
                    return false;
                }
                TwitchResponse data = new ObjectMapper()
                        .readValue(body.string(), TwitchResponse.class);
                if (data == null) {
                    return false;
                } else if (data.login() == null) {
                    return false;
                }
                log.debug("{}", data);
                this.setProfile(data.user_id(), data.login(), code);
            }
        } catch (IOException e) {
            log.error("Failed to validate authorization code", e);
            return false;
        }
        return true;
    }

    private void setProfile(String broadcaster, String channelName, String code) {
        TwitchProfile profile = new TwitchProfile();
        profile.setAccessToken(code);
        profile.setBroadcasterId(broadcaster);
        profile.setChannelName(channelName);
        this.api.setProfile(profile);
        this.events.publishEvent(new TwitchAuthorizedEvent(profile));
    }

    public void disconnect() {
        this.events.publishEvent(new TwitchTerminateEvent());
    }

}
