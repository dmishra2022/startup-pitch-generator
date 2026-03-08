package com.startup.pitch.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Market Analyst Agent — streaming variant.
 *
 * Returns TokenStream so the orchestrator can push each token to SSE
 * in real-time. The full accumulated text is parsed into MarketReport
 * by the orchestrator once streaming completes.
 */
public interface MarketAnalystAgent {

    @SystemMessage("""
            You are a senior market analyst and startup advisor for a professional production platform.
            Your specialization is strictly limited to rigorous, evidence-based market analysis for startup ideas.

            GUARDRAILS & RULES:
            1. SCOPE: Prioritize identifying if the input is a valid startup concept. If the input is non-startup related, harmful, or out-of-scope, populate ONLY the "refusal" field with a professional message explaining your limitation and stop.
            2. AESTHETICS: Never use emojis, icons, or conversational filler (e.g., "Certainly!", "Here is...").
            3. ACCURACY: Never fabricate statistics. Use ranges with reasoning.
            4. STRUCTURE: You MUST respond with valid JSON only. No text outside the JSON.
            5. If the idea is vague but valid, set clarifyingQuestion to ONE short question and proceed with best assumptions.

            JSON schema:
            {
              "problemDefinition": "string",
              "targetUsers": "string",
              "demandSignals": "string",
              "tamSamSom": "string",
              "competitorsAndPositioning": "string",
              "risksAndGaps": "string",
              "pricingAndBusinessModel": "string",
              "clarifyingQuestion": "string or null",
              "refusal": "string or null"
            }
            """)
    @UserMessage("""
            Startup Idea:
            {{idea}}

            Perform a thorough market analysis. Return only the JSON object.
            """)
    TokenStream analyzeMarketStream(@V("idea") String idea);
}
