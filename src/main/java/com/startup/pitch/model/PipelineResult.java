package com.startup.pitch.model;

import java.time.LocalDateTime;

/**
 * Aggregates all agent outputs for a single pipeline run.
 * Stored in-memory and retrieved by session ID.
 */
public record PipelineResult(

    String sessionId,
    String concept,

    // Agent outputs — null until each stage completes
    MarketReport marketReport,
    ProductSpec productSpec,
    FinancialProjection financialProjection,
    PitchDeck pitchDeck,

    // Quality evaluations (one per agent output)
    AgentEvaluation marketEvaluation,
    AgentEvaluation productEvaluation,
    AgentEvaluation financialEvaluation,
    AgentEvaluation pitchEvaluation,

    // Metadata
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    boolean successful,
    String errorMessage

) {
    /** Duration in seconds, or -1 if not yet complete. */
    public long durationSeconds() {
        if (completedAt == null || startedAt == null) return -1;
        return java.time.Duration.between(startedAt, completedAt).getSeconds();
    }

    /** Average quality score across all evaluated stages. */
    public double overallQuality() {
        double total = 0;
        int count = 0;
        if (marketEvaluation != null)    { total += marketEvaluation.averageScore();    count++; }
        if (productEvaluation != null)   { total += productEvaluation.averageScore();   count++; }
        if (financialEvaluation != null) { total += financialEvaluation.averageScore(); count++; }
        if (pitchEvaluation != null)     { total += pitchEvaluation.averageScore();     count++; }
        return count == 0 ? 0.0 : total / count;
    }

    /** Builder for gradual assembly across pipeline stages. */
    public static Builder builder(String sessionId, String concept) {
        return new Builder(sessionId, concept);
    }

    public static final class Builder {
        private final String sessionId;
        private final String concept;
        private MarketReport marketReport;
        private ProductSpec productSpec;
        private FinancialProjection financialProjection;
        private PitchDeck pitchDeck;
        private AgentEvaluation marketEvaluation;
        private AgentEvaluation productEvaluation;
        private AgentEvaluation financialEvaluation;
        private AgentEvaluation pitchEvaluation;
        private final LocalDateTime startedAt = LocalDateTime.now();
        private LocalDateTime completedAt;
        private boolean successful;
        private String errorMessage;

        private Builder(String sessionId, String concept) {
            this.sessionId = sessionId;
            this.concept   = concept;
        }

        public Builder marketReport(MarketReport r)             { this.marketReport = r; return this; }
        public Builder productSpec(ProductSpec s)               { this.productSpec = s; return this; }
        public Builder financialProjection(FinancialProjection f){ this.financialProjection = f; return this; }
        public Builder pitchDeck(PitchDeck p)                   { this.pitchDeck = p; return this; }
        public Builder marketEvaluation(AgentEvaluation e)      { this.marketEvaluation = e; return this; }
        public Builder productEvaluation(AgentEvaluation e)     { this.productEvaluation = e; return this; }
        public Builder financialEvaluation(AgentEvaluation e)   { this.financialEvaluation = e; return this; }
        public Builder pitchEvaluation(AgentEvaluation e)       { this.pitchEvaluation = e; return this; }

        public PipelineResult success() {
            this.successful   = true;
            this.completedAt  = LocalDateTime.now();
            return build();
        }

        public PipelineResult failure(String message) {
            this.successful    = false;
            this.errorMessage  = message;
            this.completedAt   = LocalDateTime.now();
            return build();
        }

        private PipelineResult build() {
            return new PipelineResult(
                sessionId, concept,
                marketReport, productSpec, financialProjection, pitchDeck,
                marketEvaluation, productEvaluation, financialEvaluation, pitchEvaluation,
                startedAt, completedAt, successful, errorMessage
            );
        }
    }
}
