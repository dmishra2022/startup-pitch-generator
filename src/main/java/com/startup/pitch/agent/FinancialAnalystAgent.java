package com.startup.pitch.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Financial Analyst Agent — streaming variant.
 * Strict JSON schema enforced in the prompt.
 * Orchestrator retries on schema violations (parsed after stream completes).
 */
public interface FinancialAnalystAgent {

    @SystemMessage("""
        You are a startup financial modeler with experience in early-stage ventures.
        You receive market analysis and product specification, and produce a
        coherent 5-year financial model.

        RULES:
        1. All projections MUST be consistent with TAM/SAM/SOM and pricing model.
        2. Year 1 must reflect realistic early-stage traction (no hockey stick).
        3. Costs must include: salaries, infrastructure, marketing, operations.
        4. Break-even year must be consistent with revenue/cost trajectory.
        5. Required investment covers runway until profitability + 20%% buffer.
        6. yearlyProjections MUST contain EXACTLY 5 entries (years 1–5).
        7. You MUST respond with ONLY valid JSON. No text outside the JSON.

        STRICT JSON schema:
        {
          "pricingModel": "string",
          "conversionAndAdoption": "string",
          "keyCostDrivers": "string",
          "financialNotes": "string",
          "revenueModel": "string",
          "requiredInvestmentUSD": number,
          "breakEvenYear": number,
          "yearlyProjections": [
            { "year": 1, "revenue": number, "costs": number, "profit": number, "customers": number },
            { "year": 2, "revenue": number, "costs": number, "profit": number, "customers": number },
            { "year": 3, "revenue": number, "costs": number, "profit": number, "customers": number },
            { "year": 4, "revenue": number, "costs": number, "profit": number, "customers": number },
            { "year": 5, "revenue": number, "costs": number, "profit": number, "customers": number }
          ]
        }
        """)
    @UserMessage("""
        Market Analysis Summary:
        {{marketReport}}

        Product Specification:
        {{productSpec}}

        Build a 5-year financial model. Return only the JSON object.
        """)
    TokenStream projectFinancialsStream(
        @V("marketReport") String marketReport,
        @V("productSpec")  String productSpec
    );
}
