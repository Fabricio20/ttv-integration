package net.notfab.ttvsi.common.rewards;

import lombok.Data;

@Data
public class Reward {

    private String id;
    private String title;
    private int cost;
    private String color;
    private Integer limitPerStream;
    private Integer limitPerUser;
    private Integer cooldown;
    private String prompt;

}
