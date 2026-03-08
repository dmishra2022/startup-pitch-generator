package com.startup.pitch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.startup.pitch.model.*;
import com.startup.pitch.orchestrator.PitchOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-aware service managing pipeline execution and SSE delivery.
 *
 * SSE event protocol (text/event-stream):
 *
 * event: stage-start
 * data:
 * {"sessionId":"...","stage":"MARKET_ANALYSIS","data":"Researching...","progressPercent":20}
 *
 * event: token
 * data: {"sessionId":"...","stage":"MARKET_ANALYSIS","data":"{\n
 * \"pro","progressPercent":20}
 *
 * event: stage-complete
 * data: {"sessionId":"...","stage":"MARKET_ANALYSIS","data":"TAM:
 * $50B...","progressPercent":20}
 *
 * event: pipeline-complete
 * data: {"sessionId":"...","stage":"COMPLETED","data":"Pipeline
 * complete","progressPercent":100}
 *
 * event: error
 * data: {"sessionId":"...","stage":"FAILED","data":"Error
 * message","progressPercent":-1}
 */
@Service
public class PitchGenerationService {

    private static final Logger log = LoggerFactory.getLogger(PitchGenerationService.class);

    private final PitchOrchestrator orchestrator;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, PipelineResult> resultStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SseEmitter> emitterStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PipelineStage> stageStore = new ConcurrentHashMap<>();

    @Value("${app.pipeline.session-ttl-minutes:60}")
    private int sessionTtlMinutes;

    @Value("${app.pipeline.max-concurrent-sessions:20}")
    private int maxConcurrentSessions;

    @Value("${app.pipeline.sse-timeout-seconds:300}")
    private long sseTimeoutSeconds;

    public PitchGenerationService(PitchOrchestrator orchestrator, ObjectMapper objectMapper) {
        this.orchestrator = orchestrator;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates and registers an SSE emitter for the given session.
     * Must be created BEFORE startGeneration so no events are missed.
     */
    public SseEmitter createEmitter(String sessionId) {
        // TOKEN events are high-frequency: set no timeout override here,
        // rely on the configured timeout. Use long timeout for long streams.
        SseEmitter emitter = new SseEmitter(sseTimeoutSeconds * 1000L);

        emitter.onCompletion(() -> {
            emitterStore.remove(sessionId);
            log.debug("[{}] SSE completed", sessionId);
        });
        emitter.onTimeout(() -> {
            emitterStore.remove(sessionId);
            log.debug("[{}] SSE timed out", sessionId);
        });
        emitter.onError(ex -> {
            emitterStore.remove(sessionId);
            log.debug("[{}] SSE error: {}", sessionId, ex.getMessage());
        });

        emitterStore.put(sessionId, emitter);

        // Race condition: if pipeline already finished, send complete immediately
        PipelineResult existing = resultStore.get(sessionId);
        if (existing != null) {
            sendEvent(emitter, existing.successful()
                    ? PipelineProgress.pipelineComplete(sessionId)
                    : PipelineProgress.error(sessionId, existing.errorMessage()));
            emitter.complete();
        }

        return emitter;
    }

    /**
     * Starts async pipeline execution. Call after createEmitter().
     */
    @Async("pipelineExecutor")
    public void startGeneration(String sessionId, StartupIdeaRequest request) {
        if (resultStore.size() >= maxConcurrentSessions) {
            emitEvent(PipelineProgress.error(sessionId,
                    "Server is at capacity. Please try again shortly."));
            return;
        }

        log.info("[{}] Starting streaming pipeline for: {}", sessionId, request.concept());
        stageStore.put(sessionId, PipelineStage.INITIALIZING);

        PipelineResult result = orchestrator.run(sessionId, request);
        resultStore.put(sessionId, result);

        scheduleEviction(sessionId);

        log.info("[{}] Pipeline finished. Success: {}", sessionId, result.successful());
    }

    /**
     * Starts async refinement pass.
     */
    @Async("pipelineExecutor")
    public void startRefinement(String sessionId, String feedback) {
        PipelineResult previous = resultStore.get(sessionId);
        if (previous == null) {
            emitEvent(PipelineProgress.error(sessionId, "Session not found or expired."));
            return;
        }

        log.info("[{}] Starting refinement for: {}", sessionId, previous.concept());
        stageStore.put(sessionId, PipelineStage.PITCH_CREATION);

        PipelineResult result = orchestrator.refine(sessionId, previous, feedback);
        resultStore.put(sessionId, result);

        log.info("[{}] Refinement finished. Success: {}", sessionId, result.successful());
    }

    public PipelineResult getResult(String sessionId) {
        return resultStore.get(sessionId);
    }

    public PipelineStage getCurrentStage(String sessionId) {
        return stageStore.getOrDefault(sessionId, PipelineStage.INITIALIZING);
    }

    public boolean isRunning(String sessionId) {
        PipelineStage s = stageStore.get(sessionId);
        return s != null && !s.isTerminal();
    }

    // ── Spring Event Listener ─────────────────────────────────────────

    /**
     * Receives all PipelineProgress events published by the orchestrator.
     * Routes each to the correct SSE emitter based on sessionId.
     */
    @EventListener
    public void onPipelineProgress(PipelineProgress progress) {
        // Keep stage tracker current (skip high-frequency TOKEN events for stage
        // tracking)
        if (!progress.isToken()) {
            stageStore.put(progress.sessionId(), progress.stage());
        }
        emitEvent(progress);
    }

    // ── Private helpers ───────────────────────────────────────────────

    private void emitEvent(PipelineProgress progress) {
        SseEmitter emitter = emitterStore.get(progress.sessionId());
        if (emitter == null)
            return;

        sendEvent(emitter, progress);

        if (progress.isCompleted() || progress.isFailed()) {
            emitter.complete();
            emitterStore.remove(progress.sessionId());
        }
    }

    private void sendEvent(SseEmitter emitter, PipelineProgress progress) {
        try {
            // Map EventType to SSE event name (lowercase-hyphen)
            String eventName = switch (progress.eventType()) {
                case STAGE_START -> "stage-start";
                case TOKEN -> "token";
                case STAGE_COMPLETE -> "stage-complete";
                case PIPELINE_COMPLETE -> "pipeline-complete";
                case ERROR -> "error";
            };

            // Payload: compact JSON with only the fields the frontend needs
            String payload = objectMapper.writeValueAsString(Map.of(
                    "sessionId", progress.sessionId(),
                    "stage", progress.stage().name(),
                    "stageName", progress.stage().getDisplayName(),
                    "data", progress.data() != null ? progress.data() : "",
                    "progressPercent", progress.progressPercent()));

            emitter.send(SseEmitter.event().name(eventName).data(payload));

        } catch (IOException ex) {
            log.debug("[{}] SSE send failed (client disconnected?): {}", progress.sessionId(), ex.getMessage());
            emitterStore.remove(progress.sessionId());
        } catch (Exception ex) {
            log.warn("[{}] SSE serialization error: {}", progress.sessionId(), ex.getMessage());
        }
    }

    private void scheduleEviction(String sessionId) {
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(sessionTtlMinutes * 60_000L);
                resultStore.remove(sessionId);
                stageStore.remove(sessionId);
                log.debug("[{}] Session evicted after TTL", sessionId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
}
