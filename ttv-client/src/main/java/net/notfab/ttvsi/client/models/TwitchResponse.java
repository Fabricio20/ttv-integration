package net.notfab.ttvsi.client.models;

import java.util.Set;

public record TwitchResponse(String client_id, String login, String user_id, long expires_in, Set<String> scopes) {
}
