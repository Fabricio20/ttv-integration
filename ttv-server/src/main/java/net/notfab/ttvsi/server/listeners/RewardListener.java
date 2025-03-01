package net.notfab.ttvsi.server.listeners;

import lombok.extern.slf4j.Slf4j;
import net.notfab.ttvsi.common.NetEvent;
import net.notfab.ttvsi.common.WsCredentials;
import net.notfab.ttvsi.common.rewards.Reward;
import net.notfab.ttvsi.common.rewards.client.ClientCreateRewardEvent;
import net.notfab.ttvsi.common.rewards.client.ClientDeleteRewardEvent;
import net.notfab.ttvsi.common.rewards.client.ClientUpdateRewardsEvent;
import net.notfab.ttvsi.common.rewards.server.RewardSyncEvent;
import net.notfab.ttvsi.server.models.Room;
import net.notfab.ttvsi.server.services.RoomManager;
import net.notfab.ttvsi.server.websocket.BufferedWebSocketHandler;
import net.notfab.ttvsi.server.websocket.events.WsMessageEvent;
import net.notfab.ttvsi.server.websocket.events.WsOpenEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class RewardListener {

    private final BufferedWebSocketHandler server;
    private final RoomManager rooms;

    public RewardListener(BufferedWebSocketHandler server, RoomManager rooms) {
        this.server = server;
        this.rooms = rooms;
    }

    /**
     * User connected, push rewards state
     */
    @EventListener
    public void onConnected(WsOpenEvent event) {
        WsCredentials credentials = event.credentials();
        if (credentials == null) {
            return;
        }
        Room room = this.rooms.get(credentials.roomId());
        if (room == null) {
            return;
        }
        this.server.send(credentials.clientId(), new RewardSyncEvent(room.getRewards()));
    }

    /**
     * Reward was redeemed on a client, re-publish it to all clients
     */
    @EventListener
    public void onRedeem(WsMessageEvent event) {
        if (!event.event().equals(NetEvent.REWARD_REDEEMED)) {
            return;
        }
        WsCredentials credentials = event.credentials();
        this.server.broadcast(credentials.roomId(), event.payload());
    }

    /**
     * Reward was created on a client, register it and publish to everyone
     */
    @EventListener
    public void onCreate(WsMessageEvent event) {
        if (!event.event().equals(NetEvent.CLIENT_CREATE_REWARD)) {
            return;
        }
        WsCredentials credentials = event.credentials();
        if (credentials == null) {
            return;
        }
        Room room = this.rooms.get(credentials.roomId());
        if (room == null) {
            return;
        }
        ClientCreateRewardEvent payload = (ClientCreateRewardEvent) event.payload();
        if (payload == null) {
            return;
        }
        List<Reward> rewards = room.getRewards();
        rewards.add(payload.reward());
        room.setRewards(rewards);
        this.server.broadcast(room.getId(), new RewardSyncEvent(room.getRewards()));
        log.info("Reward {} was created by {} on {}", payload.reward(), payload.channel(), room.getId());
    }

    /**
     * Reward was deleted on a client, register it and publish to everyone
     */
    @EventListener
    public void onDelete(WsMessageEvent event) {
        if (!event.event().equals(NetEvent.CLIENT_DELETE_REWARD)) {
            return;
        }
        WsCredentials credentials = event.credentials();
        if (credentials == null) {
            return;
        }
        Room room = this.rooms.get(credentials.roomId());
        if (room == null) {
            return;
        }
        ClientDeleteRewardEvent payload = (ClientDeleteRewardEvent) event.payload();
        if (payload == null) {
            return;
        }
        List<Reward> rewards = room.getRewards().stream()
                .filter(reward -> !reward.getId().equals(payload.reward()))
                .toList();
        room.setRewards(rewards);
        this.server.broadcast(room.getId(), new RewardSyncEvent(room.getRewards()));
        log.info("Reward {} was deleted by {} on {}", payload.reward(), payload.channel(), room.getId());
    }

    /**
     * Reward list was updated by a client, replace all and publish to everyone
     */
    @EventListener
    public void onUpdate(WsMessageEvent event) {
        if (!event.event().equals(NetEvent.CLIENT_UPDATE_REWARDS)) {
            return;
        }
        WsCredentials credentials = event.credentials();
        if (credentials == null) {
            return;
        }
        Room room = this.rooms.get(credentials.roomId());
        if (room == null) {
            return;
        }
        ClientUpdateRewardsEvent payload = (ClientUpdateRewardsEvent) event.payload();
        if (payload == null) {
            return;
        }
        room.setRewards(payload.getRewards());
        this.server.broadcast(room.getId(), new RewardSyncEvent(room.getRewards()));
        log.info("Rewards for {} were updated by {} ({})", room.getId(), payload.getChannel(), payload.getRewards().size());
    }

}
