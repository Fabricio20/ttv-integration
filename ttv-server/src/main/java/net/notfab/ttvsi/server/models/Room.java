package net.notfab.ttvsi.server.models;

import lombok.Getter;
import net.notfab.ttvsi.common.rewards.Reward;

import java.util.*;

public class Room {

    @Getter
    private final String id;

    private final Map<String, Reward> rewards = new HashMap<>();

    @Getter
    private final Set<String> members = new HashSet<>();

    public Room(String id) {
        this.id = id;
    }

    public List<Reward> getRewards() {
        return new ArrayList<>(this.rewards.values());
    }

    public void setRewards(List<Reward> rewards) {
        this.rewards.clear();
        rewards.forEach(reward -> this.rewards.put(reward.getId(), reward));
    }

}
