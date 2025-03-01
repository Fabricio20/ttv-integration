package net.notfab.ttvsi.common.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import net.notfab.ttvsi.common.NetEvent;
import net.notfab.ttvsi.common.NetworkEvent;

import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RoomMemberSyncEvent(Set<String> members) implements NetworkEvent {

    @Override
    public NetEvent getType() {
        return NetEvent.SERVER_ROOM_MEMBERS_SYNC;
    }

}
