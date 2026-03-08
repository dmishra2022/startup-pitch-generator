package com.startup.pitch.model;

import java.time.Instant;

/**
 * Unified SSE event for the entire pipeline lifecycle.
 *
 * eventType drives what the frontend does:
 *   STAGE_START       → clear streaming buffer, show active stage
 *   TOKEN             → append token.data to the live text panel
 *   STAGE_COMPLETE    → stage done; structured summary in data field
 *   PIPELINE_COMPLETE → all agents finished, redirect to result page
 *   ERROR             → show error panel
 */
public record PipelineProgress(
    String sessionId,
    EventType eventType,
    PipelineStage stage,
    String data,
    int progressPercent,
    Instant timestamp
) {
    public enum EventType { STAGE_START, TOKEN, STAGE_COMPLETE, PIPELINE_COMPLETE, ERROR }

    public static PipelineProgress stageStart(String sessionId, PipelineStage stage) {
        return new PipelineProgress(sessionId, EventType.STAGE_START, stage,
            stage.getDescription(), stage.getProgressPercent(), Instant.now());
    }

    public static PipelineProgress token(String sessionId, PipelineStage stage, String token) {
        return new PipelineProgress(sessionId, EventType.TOKEN, stage,
            token, stage.getProgressPercent(), Instant.now());
    }

    public static PipelineProgress stageComplete(String sessionId, PipelineStage stage, String summary) {
        return new PipelineProgress(sessionId, EventType.STAGE_COMPLETE, stage,
            summary, stage.getProgressPercent(), Instant.now());
    }

    public static PipelineProgress pipelineComplete(String sessionId) {
        return new PipelineProgress(sessionId, EventType.PIPELINE_COMPLETE, PipelineStage.COMPLETED,
            "Pipeline complete", 100, Instant.now());
    }

    public static PipelineProgress error(String sessionId, String message) {
        return new PipelineProgress(sessionId, EventType.ERROR, PipelineStage.FAILED,
            message, -1, Instant.now());
    }

    public boolean isCompleted() { return eventType == EventType.PIPELINE_COMPLETE; }
    public boolean isFailed()    { return eventType == EventType.ERROR; }
    public boolean isToken()     { return eventType == EventType.TOKEN; }
}
