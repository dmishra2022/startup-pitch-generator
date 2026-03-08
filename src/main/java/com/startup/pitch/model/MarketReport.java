package com.startup.pitch.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Structured output from the Market Analyst Agent.
 * All fields are populated by the LLM and parsed from JSON.
 */
public record MarketReport(

        @JsonProperty("problemDefinition") String problemDefinition,

        @JsonProperty("targetUsers") String targetUsers,

        @JsonProperty("demandSignals") String demandSignals,

        @JsonProperty("tamSamSom") String tamSamSom,

        @JsonProperty("competitorsAndPositioning") String competitorsAndPositioning,

        @JsonProperty("risksAndGaps") String risksAndGaps,

        @JsonProperty("pricingAndBusinessModel") String pricingAndBusinessModel,

        @JsonProperty("clarifyingQuestion") String clarifyingQuestion,

        @JsonProperty("refusal") String refusal

) {
    /** True when the analyst needs more info before proceeding. */
    public boolean needsClarification() {
        return clarifyingQuestion != null && !clarifyingQuestion.isBlank();
    }

    /** True when the input was refused by the analyst. */
    public boolean isRefused() {
        return refusal != null && !refusal.isBlank();
    }
}
