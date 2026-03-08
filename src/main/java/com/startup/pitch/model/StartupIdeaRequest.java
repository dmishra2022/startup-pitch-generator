package com.startup.pitch.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Validated user input capturing the startup concept.
 * Intentionally minimal — agents will clarify if needed.
 */
public record StartupIdeaRequest(

    @NotBlank(message = "Please describe your startup idea")
    @Size(min = 10, max = 500, message = "Idea must be between 10 and 500 characters")
    String concept,

    @Size(max = 200, message = "Target market description is too long")
    String targetMarket,

    @Size(max = 300, message = "Problem statement is too long")
    String problemStatement,

    @Size(max = 100, message = "API key is too long")
    String apiKey

) {
    /** Produces a single enriched prompt string for the first agent. */
    public String toEnrichedPrompt() {
        var sb = new StringBuilder("Startup Concept: ").append(concept);
        if (targetMarket != null && !targetMarket.isBlank()) {
            sb.append("\nTarget Market: ").append(targetMarket);
        }
        if (problemStatement != null && !problemStatement.isBlank()) {
            sb.append("\nProblem Statement: ").append(problemStatement);
        }
        return sb.toString();
    }
}
