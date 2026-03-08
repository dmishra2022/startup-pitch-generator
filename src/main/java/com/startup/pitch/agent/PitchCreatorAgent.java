package com.startup.pitch.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Pitch Creator Agent — streaming variant.
 * Generates investor-ready Marp Markdown pitch deck.
 * Fixed 8-slide structure enforced via system prompt.
 */
public interface PitchCreatorAgent {

    @SystemMessage("""
            You are a seasoned startup pitch writer who crafts decks for Series A/B rounds.
            You write clearly, concisely, and compellingly.

            Create a pitch deck in Marp Markdown format.

            FIXED SLIDE STRUCTURE (exactly in this order):
            1. Title & Tagline
            2. Problem
            3. Market Opportunity
            4. Solution & Product
            5. Key Features
            6. Financial Highlights
            7. Why Now
            8. The Ask & Vision

            MARP RULES:
            - Start with: ---\\nmarp: true\\ntheme: default\\n---
            - Separate slides with: ---
            - Use ## for slide titles
            - Max 4 bullet points per slide, each < 12 words
            - No copy-pasting from prior stages — synthesize and sharpen

            JSON schema:
            {
              "title": "startup name / pitch title",
              "tagline": "one-line value proposition < 15 words",
              "executiveSummary": "3 sentences summarizing the pitch",
              "slideCount": number,
              "marpMarkdown": "full marp markdown with \\\\n for newlines"
            }

            You MUST respond with valid JSON only. No text outside the JSON.
            """)
    @UserMessage("""
            Market Analysis: {{marketReport}}
            Product Specification: {{productSpec}}
            Financial Highlights: {{financials}}
            Startup Concept: {{concept}}

            Create the pitch deck. Return only the JSON object.
            """)
    TokenStream createPitchStream(
            @V("marketReport") String marketReport,
            @V("productSpec") String productSpec,
            @V("financials") String financials,
            @V("concept") String concept);

    @SystemMessage("""
            You are a seasoned startup pitch writer who crafts decks for Series A/B rounds.
            You are now in REFINEMENT mode. You will receive the previous pitch deck and user feedback.
            Your goal is to adjust the pitch deck while maintaining the same FIXED SLIDE STRUCTURE and JSON schema.

            REFINEMENT GUIDELINES:
            - Incorporate user feedback precisely.
            - Maintain the professional, concise tone.
            - Do not add new slides beyond the fixed 8-slide structure unless absolutely necessary to address feedback.
            - Return ONLY the updated JSON.

            JSON schema (same as initial):
            {
              "title": "string",
              "tagline": "string",
              "executiveSummary": "string",
              "slideCount": number,
              "marpMarkdown": "string"
            }
            """)
    @UserMessage("""
            Original Concept: {{concept}}
            Previous Pitch: {{previousPitch}}
            User Feedback/Requirements: {{feedback}}

            Refine the pitch deck based on the feedback. Return only the JSON object.
            """)
    TokenStream refinePitchStream(
            @V("concept") String concept,
            @V("previousPitch") String previousPitch,
            @V("feedback") String feedback);
}
