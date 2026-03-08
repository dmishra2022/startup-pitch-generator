package com.startup.pitch.config;

import com.startup.pitch.agent.*;
import com.startup.pitch.tools.FinancialCalculatorTool;
import com.startup.pitch.tools.WebResearchTool;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LangChain4j Configuration — streaming-first.
 *
 * All main agents use OpenAiStreamingChatModel so every token
 * is pushed to the SSE stream the moment it arrives from OpenAI.
 *
 * Streaming model → TokenStream → SSE token events → frontend typewriter.
 *
 * Only the JudgeAgent uses a standard ChatModel: it produces tiny
 * fixed-schema JSON (~200 tokens) where streaming adds no UX value.
 *
 * Per-agent temperature tuning is preserved:
 *   - research (0.6)   → market analysis
 *   - structured (0.3) → product + financial (schema-critical)
 *   - creative (0.8)   → pitch writing
 *   - judge (0.1)      → deterministic scoring
 */
@Configuration
public class LangChain4jConfig {

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String apiKey;

    @Value("${langchain4j.open-ai.chat-model.model-name:gpt-4o-mini}")
    private String modelName;

    // ── Streaming Models ──────────────────────────────────────────────

    @Bean("streamingResearchModel")
    public OpenAiStreamingChatModel streamingResearchModel() {
        return OpenAiStreamingChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .temperature(0.6)
            .maxTokens(3000)
            .timeout(Duration.ofSeconds(90))
            .build();
    }

    @Bean("streamingStructuredModel")
    public OpenAiStreamingChatModel streamingStructuredModel() {
        return OpenAiStreamingChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .temperature(0.3)
            .maxTokens(4096)
            .timeout(Duration.ofSeconds(120))
            .build();
    }

    @Bean("streamingCreativeModel")
    public OpenAiStreamingChatModel streamingCreativeModel() {
        return OpenAiStreamingChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .temperature(0.8)
            .maxTokens(4096)
            .timeout(Duration.ofSeconds(120))
            .build();
    }

    // ── Non-Streaming Model (Judge only) ──────────────────────────────

    @Bean("judgeModel")
    public OpenAiChatModel judgeModel() {
        return OpenAiChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .temperature(0.1)
            .maxTokens(512)
            .timeout(Duration.ofSeconds(30))
            .build();
    }

    // ── Agent Beans ───────────────────────────────────────────────────

    /**
     * Market Analyst: streaming model + WebResearchTool.
     * Returns TokenStream for live token-by-token SSE delivery.
     */
    @Bean
    public MarketAnalystAgent marketAnalystAgent(
            OpenAiStreamingChatModel streamingResearchModel,
            WebResearchTool webResearchTool) {
        return AiServices.builder(MarketAnalystAgent.class)
            .streamingChatLanguageModel(streamingResearchModel)
            .tools(webResearchTool)
            .build();
    }

    /**
     * Product Manager: streaming structured model, no tools.
     */
    @Bean
    public ProductManagerAgent productManagerAgent(
            OpenAiStreamingChatModel streamingStructuredModel) {
        return AiServices.builder(ProductManagerAgent.class)
            .streamingChatLanguageModel(streamingStructuredModel)
            .build();
    }

    /**
     * Financial Analyst: streaming structured model + FinancialCalculatorTool.
     * Calculator prevents arithmetic hallucination in the JSON output.
     */
    @Bean
    public FinancialAnalystAgent financialAnalystAgent(
            OpenAiStreamingChatModel streamingStructuredModel,
            FinancialCalculatorTool financialCalculatorTool) {
        return AiServices.builder(FinancialAnalystAgent.class)
            .streamingChatLanguageModel(streamingStructuredModel)
            .tools(financialCalculatorTool)
            .build();
    }

    /**
     * Pitch Creator: streaming creative model, no tools.
     * Higher temperature produces more compelling pitch language.
     */
    @Bean
    public PitchCreatorAgent pitchCreatorAgent(
            OpenAiStreamingChatModel streamingCreativeModel) {
        return AiServices.builder(PitchCreatorAgent.class)
            .streamingChatLanguageModel(streamingCreativeModel)
            .build();
    }

    /**
     * Judge Agent: non-streaming, deterministic scoring.
     * Uses ChatModel (not StreamingChatModel) — tiny output, no streaming needed.
     */
    @Bean
    public JudgeAgent judgeAgent(OpenAiChatModel judgeModel) {
        return AiServices.builder(JudgeAgent.class)
            .chatLanguageModel(judgeModel)
            .build();
    }
}
