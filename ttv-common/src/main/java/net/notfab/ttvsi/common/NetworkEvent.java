package net.notfab.ttvsi.common;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import net.notfab.ttvsi.common.polls.client.ClientPollFinishedEvent;
import net.notfab.ttvsi.common.polls.client.ClientPollProgressEvent;
import net.notfab.ttvsi.common.polls.client.ClientRequestedPollCreateEvent;
import net.notfab.ttvsi.common.polls.server.ServerCreatePollEvent;
import net.notfab.ttvsi.common.polls.server.ServerPollFinishedEvent;
import net.notfab.ttvsi.common.polls.server.ServerPollUpdateEvent;
import net.notfab.ttvsi.common.protocol.RoomMemberSyncEvent;
import net.notfab.ttvsi.common.rewards.RewardRedeemEvent;
import net.notfab.ttvsi.common.rewards.client.ClientCreateRewardEvent;
import net.notfab.ttvsi.common.rewards.client.ClientDeleteRewardEvent;
import net.notfab.ttvsi.common.rewards.client.ClientUpdateRewardsEvent;
import net.notfab.ttvsi.common.rewards.server.RewardSyncEvent;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ClientPollProgressEvent.class, name = "CLIENT_POLL_PROGRESS"),
        @JsonSubTypes.Type(value = ClientPollFinishedEvent.class, name = "CLIENT_POLL_FINISHED"),
        @JsonSubTypes.Type(value = ClientRequestedPollCreateEvent.class, name = "CLIENT_CREATE_POLL"),
        @JsonSubTypes.Type(value = ClientCreateRewardEvent.class, name = "CLIENT_CREATE_REWARD"),
        @JsonSubTypes.Type(value = ClientDeleteRewardEvent.class, name = "CLIENT_DELETE_REWARD"),
        @JsonSubTypes.Type(value = ClientUpdateRewardsEvent.class, name = "CLIENT_UPDATE_REWARDS"),

        @JsonSubTypes.Type(value = RewardSyncEvent.class, name = "SERVER_REWARD_SYNC"),
        @JsonSubTypes.Type(value = RoomMemberSyncEvent.class, name = "SERVER_ROOM_MEMBERS_SYNC"),
        @JsonSubTypes.Type(value = ServerPollFinishedEvent.class, name = "SERVER_POLL_FINISHED"),
        @JsonSubTypes.Type(value = ServerCreatePollEvent.class, name = "SERVER_POLL_CREATE"),
        @JsonSubTypes.Type(value = ServerPollUpdateEvent.class, name = "SERVER_POLL_UPDATE"),

        @JsonSubTypes.Type(value = RewardRedeemEvent.class, name = "REWARD_REDEEMED"),
})
public interface NetworkEvent {

    NetEvent getType();

}
