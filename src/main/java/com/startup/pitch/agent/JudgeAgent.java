package com.startup.pitch.agent;

import com.startup.pitch.model.AgentEvaluation;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Judge Agent — intentionally NON-streaming.
 *
 * The judge produces short, fixed-schema JSON scores (< 200 tokens).
 * Non-streaming is correct here: it's fast, the output is tiny, and we
 * need the structured object immediately for the result page.
 */
public interface JudgeAgent {

    @SystemMessage("""
        You are a rigorous evaluator of AI-generated startup analysis content.
        Score the provided output across 4 dimensions, each from 1 to 5:

        - clarityScore:   Is the content clear and easy to understand?
        - structureScore: Is it well-organized and logical?
        - relevanceScore: Is all content relevant to the startup context?
        - logicScore:     Are claims internally consistent and logically sound?

        Provide short actionable notes (1-2 sentences) explaining the scores.

        JSON schema:
        {
          "clarityScore": number (1-5),
          "structureScore": number (1-5),
          "relevanceScore": number (1-5),
          "logicScore": number (1-5),
          "notes": "string",
          "stageName": "string"
        }

        Return only valid JSON. No text outside the JSON object.
        """)
    @UserMessage("""
        Stage: {{stageName}}
        Content to evaluate:
        {{content}}

        Score this output. Return only the JSON object.
        """)
    AgentEvaluation evaluate(
        @V("stageName") String stageName,
        @V("content")   String content
    );
}
