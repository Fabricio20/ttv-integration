package net.notfab.ttvsi.common.polls;

import lombok.Data;

import java.util.List;

@Data
public class Poll {

    private String id;
    private String title;
    private Integer points;
    private int duration;
    private List<Choice> choices;

}
