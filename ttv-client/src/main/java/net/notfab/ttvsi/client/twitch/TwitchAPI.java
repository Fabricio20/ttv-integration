package net.notfab.ttvsi.client.twitch;

import lombok.Getter;
import lombok.Setter;
import net.notfab.ttvsi.client.models.TwitchProfile;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Getter
@Service
public class TwitchAPI {

    public static final String TWITCH_APP_CLIENT_ID = "05gxk5g7jaqr7lhlvgqjwxkoba7s6v";
    private final OkHttpClient http = new OkHttpClient();
    private final AtomicBoolean destructive = new AtomicBoolean(false);
    @Setter
    private TwitchProfile profile;

}
