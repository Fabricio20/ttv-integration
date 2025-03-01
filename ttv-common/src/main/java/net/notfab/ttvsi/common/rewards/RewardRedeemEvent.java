package net.notfab.ttvsi.common.rewards;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import net.notfab.ttvsi.common.NetEvent;
import net.notfab.ttvsi.common.NetworkEvent;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RewardRedeemEvent implements NetworkEvent {

    private String id;
    private String reward;
    private String userId;
    private String channel;
    private String userName;
    private String input;

    @Override
    public NetEvent getType() {
        return NetEvent.REWARD_REDEEMED;
    }

}
