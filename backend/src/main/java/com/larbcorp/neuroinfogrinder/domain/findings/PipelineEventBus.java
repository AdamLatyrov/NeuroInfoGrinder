package com.larbcorp.neuroinfogrinder.domain.findings;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class PipelineEventBus {

    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException error) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    public void publish(PipelineEvent event) {
        emitters.removeIf(emitter -> !send(emitter, event));
    }

    private boolean send(SseEmitter emitter, PipelineEvent event) {
        try {
            emitter.send(SseEmitter.event().name("pipeline").data(event));
            return true;
        } catch (IOException | IllegalStateException error) {
            log.debug("Pipeline SSE subscriber disconnected: {}", error.getMessage());
            return false;
        }
    }

    public record PipelineEvent(
        String type,
        Long messageId,
        Long groupId,
        String stage,
        String status
    ) {}
}
