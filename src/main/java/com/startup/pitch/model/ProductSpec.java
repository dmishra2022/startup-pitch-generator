package com.startup.pitch.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Structured output from the Product Manager Agent.
 * Derived from the MarketReport — does NOT rewrite market analysis.
 */
public record ProductSpec(

    @JsonProperty("productOverview")
    String productOverview,

    @JsonProperty("valueProposition")
    String valueProposition,

    @JsonProperty("coreFeatures")
    List<String> coreFeatures,

    @JsonProperty("technicalConstraints")
    String technicalConstraints,

    @JsonProperty("mvpScope")
    String mvpScope,

    @JsonProperty("successMetrics")
    String successMetrics

) {}
