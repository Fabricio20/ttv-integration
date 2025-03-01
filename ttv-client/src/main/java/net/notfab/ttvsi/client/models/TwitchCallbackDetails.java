package net.notfab.ttvsi.client.models;

import lombok.Data;

@Data
public class TwitchCallbackDetails {

    private String id;
    private String type;
    private String status;

    public boolean isActive() {
        return "enabled".equalsIgnoreCase(this.status);
    }

}
