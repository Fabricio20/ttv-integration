package net.notfab.ttvsi.server.models;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class PollState {

    private final String id;
    private final String room;
    private final Map<String, Map<String, Integer>> votes = new HashMap<>();

    public PollState(String id, String room) {
        this.id = id;
        this.room = room;
    }

    public synchronized Map<String, Integer> getResults() {
        Map<String, Integer> totals = new HashMap<>();
        this.votes.forEach((channel, votes) -> {
            votes.forEach((id, total) -> totals.merge(id, total, Integer::sum));
        });
        return totals;
    }

    public synchronized void setVotes(String channel, Map<String, Integer> votes) {
        this.votes.put(channel, votes);
    }

}
