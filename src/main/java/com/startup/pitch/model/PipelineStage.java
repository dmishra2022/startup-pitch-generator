package com.startup.pitch.model;

/**
 * Represents each stage in the pitch generation pipeline.
 * Stages are ordered - each stage feeds into the next.
 */
public enum PipelineStage {

    INITIALIZING("Initializing", "Setting up the pipeline...", 0),
    MARKET_ANALYSIS("Market Analysis", "Researching market landscape, competitors, and demand signals...", 20),
    PRODUCT_DEFINITION("Product Definition", "Translating market insights into a focused product spec...", 40),
    FINANCIAL_PROJECTION("Financial Projection", "Modeling 5-year revenue, costs, and investment requirements...", 60),
    PITCH_CREATION("Pitch Creation", "Crafting an investor-ready pitch deck...", 80),
    EVALUATION("Evaluation", "Running quality checks on all outputs...", 90),
    COMPLETED("Completed", "Your pitch deck is ready!", 100),
    FAILED("Failed", "Pipeline encountered an error.", -1);

    private final String displayName;
    private final String description;
    private final int progressPercent;

    PipelineStage(String displayName, String description, int progressPercent) {
        this.displayName = displayName;
        this.description = description;
        this.progressPercent = progressPercent;
    }

    public String getDisplayName()    { return displayName; }
    public String getDescription()    { return description; }
    public int getProgressPercent()   { return progressPercent; }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }

    public boolean isSuccessful() {
        return this == COMPLETED;
    }
}
