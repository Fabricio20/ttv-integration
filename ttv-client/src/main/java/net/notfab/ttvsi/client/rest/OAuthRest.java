package net.notfab.ttvsi.client.rest;

import lombok.extern.slf4j.Slf4j;
import net.notfab.ttvsi.client.twitch.TwitchOAuthManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@Slf4j
@RestController
public class OAuthRest {

    private final TwitchOAuthManager oauth;
    private final String HTTP_PORT;

    public OAuthRest(TwitchOAuthManager oauth, @Value("${server.port}") String HTTP_PORT) {
        this.oauth = oauth;
        this.HTTP_PORT = HTTP_PORT;
    }

    /**
     * Twitch sends the code in the fragment section for some reason
     */
    @GetMapping
    public ResponseEntity<String> onTwitchResponse() {
        String html = """
                <html>
                <head>
                <script type="text/javascript">
                document.location = 'http://localhost:{HTTP_PORT}/authorize?' + document.location.hash.substr(1);
                </script>
                </head>
                </html>
                """;
        return ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType.TEXT_HTML)
                .body(html.replace("{HTTP_PORT}", this.HTTP_PORT));
    }

    @GetMapping("authorize")
    public ResponseEntity<String> onAuthorize(Optional<String> access_token) {
        if (access_token.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Authorization failed, please try again");
        }
        if (!this.oauth.validate(access_token.get())) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Verification failed, please try again");
        }
        return ResponseEntity
                .ok("Authorization succeeded! You can close this page.");
    }

}
