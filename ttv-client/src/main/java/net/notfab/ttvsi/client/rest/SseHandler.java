package net.notfab.ttvsi.client.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.notfab.ttvsi.common.NetworkEvent;
import net.notfab.ttvsi.common.polls.server.ServerCreatePollEvent;
import net.notfab.ttvsi.common.polls.server.ServerPollFinishedEvent;
import net.notfab.ttvsi.common.polls.server.ServerPollUpdateEvent;
import net.notfab.ttvsi.common.protocol.RoomMemberSyncEvent;
import net.notfab.ttvsi.common.rewards.RewardRedeemEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
public class SseHandler {

    private final SseEmitter emitter = new SseEmitter();
    private final ObjectMapper mapper = new ObjectMapper();

    @GetMapping("sse")
    public SseEmitter getSseEmitter() {
        return this.emitter;
    }

    @EventListener
    public void onPollCreated(ServerCreatePollEvent event) {
        this.broadcast(event);
    }

    @EventListener
    public void onPollProgress(ServerPollUpdateEvent event) {
        this.broadcast(event);
    }

    @EventListener
    public void onPollFinished(ServerPollFinishedEvent event) {
        this.broadcast(event);
    }

    @EventListener
    public void onRewardRedeemed(RewardRedeemEvent event) {
        this.broadcast(event);
    }

    @EventListener
    public void onMembersSync(RoomMemberSyncEvent event) {
        this.broadcast(event);
    }

    private void broadcast(NetworkEvent event) {
        String message;
        try {
            message = this.mapper.writeValueAsString(event);
        } catch (Exception ex) {
            return;
        }
        SseEmitter.SseEventBuilder sse = SseEmitter.event()
                .data(message)
                .id(UUID.randomUUID().toString())
                .name(event.getType().name());
        try {
            this.emitter.send(sse);
        } catch (IOException ex) {
            log.warn("Failed to emit SSE", ex);
        }
    }

}
