package com.startup.pitch.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Quality evaluation scores returned by the Judge Agent.
 * Scores 1–5 per dimension + short notes for iterative improvement.
 */
public record AgentEvaluation(

    @JsonProperty("clarityScore")
    int clarityScore,

    @JsonProperty("structureScore")
    int structureScore,

    @JsonProperty("relevanceScore")
    int relevanceScore,

    @JsonProperty("logicScore")
    int logicScore,

    @JsonProperty("notes")
    String notes,

    @JsonProperty("stageName")
    String stageName

) {
    /** Average score across all 4 dimensions. */
    public double averageScore() {
        return (clarityScore + structureScore + relevanceScore + logicScore) / 4.0;
    }

    public String qualityLabel() {
        double avg = averageScore();
        if (avg >= 4.5) return "Excellent";
        if (avg >= 3.5) return "Good";
        if (avg >= 2.5) return "Fair";
        return "Needs Improvement";
    }

    /** Quality color for UI rendering. */
    public String qualityColor() {
        double avg = averageScore();
        if (avg >= 4.5) return "emerald";
        if (avg >= 3.5) return "blue";
        if (avg >= 2.5) return "amber";
        return "red";
    }
}
