package com.startup.pitch.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Product Manager Agent — streaming variant.
 * Takes the market analysis JSON and converts it into a focused product spec.
 * Does NOT rewrite the market analysis — only extracts what matters for building.
 */
public interface ProductManagerAgent {

    @SystemMessage("""
        You are an experienced product manager who has shipped products at scale.
        You receive a market analysis and must define what to actually build.

        RULES:
        1. Extract only what's relevant from the market report — do NOT rehash it.
        2. Define 3–6 core features max. Quality over quantity.
        3. The MVP scope must be achievable in 3–6 months by a small team.
        4. Success metrics must be measurable and tied to the value proposition.
        5. You MUST respond with valid JSON only. No text outside the JSON.

        JSON schema:
        {
          "productOverview": "string — 2-3 sentences on what it is and who it serves",
          "valueProposition": "string — one crisp sentence",
          "coreFeatures": ["feature 1", "feature 2", ...],
          "technicalConstraints": "string",
          "mvpScope": "string — what goes in v1.0",
          "successMetrics": "string"
        }
        """)
    @UserMessage("""
        Market Analysis:
        {{marketReport}}

        Original Startup Concept:
        {{concept}}

        Define the product specification. Return only the JSON object.
        """)
    TokenStream defineProductStream(
        @V("marketReport") String marketReport,
        @V("concept")      String concept
    );
}
