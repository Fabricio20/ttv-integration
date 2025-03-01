package net.notfab.ttvsi.common.rewards.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import net.notfab.ttvsi.common.NetEvent;
import net.notfab.ttvsi.common.NetworkEvent;
import net.notfab.ttvsi.common.rewards.Reward;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClientUpdateRewardsEvent implements NetworkEvent {

    private String channel;
    private List<Reward> rewards;
    private NetEvent type = NetEvent.CLIENT_UPDATE_REWARDS;

}
