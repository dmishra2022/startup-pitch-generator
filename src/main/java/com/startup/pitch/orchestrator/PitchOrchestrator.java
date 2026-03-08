package com.startup.pitch.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.startup.pitch.agent.*;
import com.startup.pitch.model.*;
import dev.langchain4j.service.TokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Streaming Linear Pipeline Orchestrator.
 *
 * Each agent call:
 * 1. Emits STAGE_START → frontend clears streaming buffer, activates stage
 * 2. Streams tokens → each token published as TOKEN event → SSE → typewriter UI
 * 3. Accumulates text → CompletableFuture resolves when stream completes
 * 4. Parses JSON → accumulated text → Jackson → typed record
 * 5. Emits STAGE_COMPLETE → frontend swaps typewriter for structured summary
 * card
 *
 * One LLM call per agent. No double-calling. Streaming is additive, not extra
 * cost.
 *
 * Pipeline order:
 * Market Analysis → Product Definition → Financial Projection → Pitch Creation
 * → Evaluation
 */
@Component
public class PitchOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PitchOrchestrator.class);

    private static final long STREAM_TIMEOUT_SECONDS = 120;

    private final MarketAnalystAgent marketAnalyst;
    private final ProductManagerAgent productManager;
    private final FinancialAnalystAgent financialAnalyst;
    private final PitchCreatorAgent pitchCreator;
    private final JudgeAgent judge;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Value("${app.agent.judge.enabled:true}")
    private boolean judgeEnabled;

    @Value("${app.agent.financial-analyst.max-retries:3}")
    private int financialMaxRetries;

    public PitchOrchestrator(
            MarketAnalystAgent marketAnalyst,
            ProductManagerAgent productManager,
            FinancialAnalystAgent financialAnalyst,
            PitchCreatorAgent pitchCreator,
            JudgeAgent judge,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper) {
        this.marketAnalyst = marketAnalyst;
        this.productManager = productManager;
        this.financialAnalyst = financialAnalyst;
        this.pitchCreator = pitchCreator;
        this.judge = judge;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * Entry point. Runs the full streaming pipeline on the calling thread.
     * Called from @Async method in PitchGenerationService.
     */
    public PipelineResult run(String sessionId, StartupIdeaRequest request) {
        var builder = PipelineResult.builder(sessionId, request.concept());

        try {
            // ── Stage 1: Market Analysis ──────────────────────────────
            emit(PipelineProgress.stageStart(sessionId, PipelineStage.MARKET_ANALYSIS));

            String marketJson = streamAndAccumulate(sessionId, PipelineStage.MARKET_ANALYSIS,
                    () -> marketAnalyst.analyzeMarketStream(request.toEnrichedPrompt()));

            MarketReport marketReport = parse(marketJson, MarketReport.class, "MarketReport");
            if (marketReport.isRefused()) {
                log.warn("[{}] Pipeline refused: {}", sessionId, marketReport.refusal());
                throw new IllegalArgumentException(marketReport.refusal());
            }
            builder.marketReport(marketReport);
            emit(PipelineProgress.stageComplete(sessionId, PipelineStage.MARKET_ANALYSIS,
                    buildMarketSummary(marketReport)));
            log.info("[{}] Market analysis complete", sessionId);

            // ── Stage 2: Product Definition ───────────────────────────
            emit(PipelineProgress.stageStart(sessionId, PipelineStage.PRODUCT_DEFINITION));
            String marketSummary = toJson(marketReport);

            String productJson = streamAndAccumulate(sessionId, PipelineStage.PRODUCT_DEFINITION,
                    () -> productManager.defineProductStream(marketSummary, request.concept()));

            ProductSpec productSpec = parse(productJson, ProductSpec.class, "ProductSpec");
            builder.productSpec(productSpec);
            emit(PipelineProgress.stageComplete(sessionId, PipelineStage.PRODUCT_DEFINITION,
                    buildProductSummary(productSpec)));
            log.info("[{}] Product definition complete", sessionId);

            // ── Stage 3: Financial Projection ─────────────────────────
            emit(PipelineProgress.stageStart(sessionId, PipelineStage.FINANCIAL_PROJECTION));
            String productSummary = toJson(productSpec);

            FinancialProjection financials = streamFinancialsWithRetry(
                    sessionId, marketSummary, productSummary);
            builder.financialProjection(financials);
            emit(PipelineProgress.stageComplete(sessionId, PipelineStage.FINANCIAL_PROJECTION,
                    buildFinancialSummary(financials)));
            log.info("[{}] Financial projection complete. Break-even: Year {}",
                    sessionId, financials.breakEvenYear());

            // ── Stage 4: Pitch Creation ───────────────────────────────
            emit(PipelineProgress.stageStart(sessionId, PipelineStage.PITCH_CREATION));
            String financialSummary = toJson(financials);

            String pitchJson = streamAndAccumulate(sessionId, PipelineStage.PITCH_CREATION,
                    () -> pitchCreator.createPitchStream(
                            marketSummary, productSummary, financialSummary, request.concept()));

            PitchDeck pitchDeck = parse(pitchJson, PitchDeck.class, "PitchDeck");
            builder.pitchDeck(pitchDeck);
            emit(PipelineProgress.stageComplete(sessionId, PipelineStage.PITCH_CREATION,
                    buildPitchSummary(pitchDeck)));
            log.info("[{}] Pitch deck created: {} slides", sessionId, pitchDeck.slideCount());

            // ── Stage 5: Evaluation ───────────────────────────────────
            if (judgeEnabled) {
                emit(PipelineProgress.stageStart(sessionId, PipelineStage.EVALUATION));

                // Judge is non-streaming: fast, tiny, schema-critical
                builder.marketEvaluation(judge.evaluate("Market Analysis", marketSummary));
                builder.productEvaluation(judge.evaluate("Product Definition", productSummary));
                builder.financialEvaluation(judge.evaluate("Financial Projection", financialSummary));
                builder.pitchEvaluation(judge.evaluate("Pitch Deck", pitchDeck.executiveSummary()));

                emit(PipelineProgress.stageComplete(sessionId, PipelineStage.EVALUATION,
                        "Quality evaluation complete"));
                log.info("[{}] Evaluation complete", sessionId);
            }

            emit(PipelineProgress.pipelineComplete(sessionId));
            log.info("[{}] Pipeline completed successfully", sessionId);
            return builder.success();

        } catch (Exception ex) {
            log.error("[{}] Pipeline failed: {}", sessionId, ex.getMessage(), ex);
            emit(PipelineProgress.error(sessionId, ex.getMessage()));
            return builder.failure(ex.getMessage());
        }
    }

    /**
     * Refines an existing pitch deck based on user feedback.
     */
    public PipelineResult refine(String sessionId, PipelineResult previous, String feedback) {
        var builder = PipelineResult.builder(sessionId, previous.concept())
                .marketReport(previous.marketReport())
                .productSpec(previous.productSpec())
                .financialProjection(previous.financialProjection());

        try {
            log.info("[{}] Starting pitch refinement with feedback: {}", sessionId, feedback);
            emit(PipelineProgress.stageStart(sessionId, PipelineStage.PITCH_CREATION));

            String previousPitchStr = toJson(previous.pitchDeck());
            String pitchJson = streamAndAccumulate(sessionId, PipelineStage.PITCH_CREATION,
                    () -> pitchCreator.refinePitchStream(previous.concept(), previousPitchStr, feedback));

            PitchDeck refinedDeck = parse(pitchJson, PitchDeck.class, "PitchDeck");
            builder.pitchDeck(refinedDeck);
            emit(PipelineProgress.stageComplete(sessionId, PipelineStage.PITCH_CREATION,
                    "Pitch refinement complete"));

            if (judgeEnabled) {
                emit(PipelineProgress.stageStart(sessionId, PipelineStage.EVALUATION));
                builder.marketEvaluation(previous.marketEvaluation());
                builder.productEvaluation(previous.productEvaluation());
                builder.financialEvaluation(previous.financialEvaluation());
                builder.pitchEvaluation(judge.evaluate("Refined Pitch Deck", refinedDeck.executiveSummary()));

                emit(PipelineProgress.stageComplete(sessionId, PipelineStage.EVALUATION,
                        "Quality re-evaluation complete"));
            }

            emit(PipelineProgress.pipelineComplete(sessionId));
            return builder.success();

        } catch (Exception ex) {
            log.error("[{}] Refinement failed: {}", sessionId, ex.getMessage());
            emit(PipelineProgress.error(sessionId, "Refinement failed: " + ex.getMessage()));
            return builder.failure(ex.getMessage());
        }
    }

    // ── Core streaming primitive ──────────────────────────────────────

    /**
     * Executes a streaming agent call and:
     * - Publishes each token as a TOKEN SSE event
     * - Accumulates the full response text
     * - Blocks until the stream is complete (or timeout)
     * - Returns the full accumulated text for JSON parsing
     */
    private String streamAndAccumulate(
            String sessionId,
            PipelineStage stage,
            Supplier<TokenStream> tokenStreamSupplier) throws Exception {

        StringBuilder accumulated = new StringBuilder();
        CompletableFuture<String> completion = new CompletableFuture<>();

        TokenStream tokenStream = tokenStreamSupplier.get();

        tokenStream
                .onNext(token -> {
                    accumulated.append(token);
                    // Publish every token as a lightweight SSE event
                    emit(PipelineProgress.token(sessionId, stage, token));
                })
                .onComplete(response -> {
                    log.debug("[{}] Stream complete for stage {}. Total chars: {}",
                            sessionId, stage, accumulated.length());
                    completion.complete(accumulated.toString());
                })
                .onError(error -> {
                    log.warn("[{}] Stream error for stage {}: {}", sessionId, stage, error.getMessage());
                    completion.completeExceptionally(error);
                })
                .start();

        return completion.get(STREAM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Runs the financial stage with retry logic.
     * Retries on JSON schema violations (yearlyProjections must be exactly 5
     * entries).
     */
    private FinancialProjection streamFinancialsWithRetry(
            String sessionId, String marketSummary, String productSummary) throws Exception {

        Exception lastException = null;
        for (int attempt = 1; attempt <= financialMaxRetries; attempt++) {
            try {
                String json = streamAndAccumulate(sessionId, PipelineStage.FINANCIAL_PROJECTION,
                        () -> financialAnalyst.projectFinancialsStream(marketSummary, productSummary));
                FinancialProjection result = parse(json, FinancialProjection.class, "FinancialProjection");
                validateFinancials(result);
                return result;
            } catch (Exception ex) {
                lastException = ex;
                log.warn("[{}] Financial attempt {}/{} failed: {}",
                        sessionId, attempt, financialMaxRetries, ex.getMessage());
                if (attempt < financialMaxRetries) {
                    // Brief pause before retry — don't spam the API
                    Thread.sleep(500);
                }
            }
        }
        throw new IllegalStateException(
                "Financial projection failed after " + financialMaxRetries + " attempts", lastException);
    }

    // ── JSON parsing utilities ────────────────────────────────────────

    /**
     * Parses the accumulated streaming text into a typed record.
     * Handles LLM quirks: markdown fences, leading/trailing text, BOM chars.
     */
    private <T> T parse(String rawText, Class<T> type, String stageName) {
        String cleaned = cleanJson(rawText);
        try {
            return objectMapper.readValue(cleaned, type);
        } catch (Exception ex) {
            log.warn("Failed to parse {} JSON. Raw text length: {}. Cleaned: {}...",
                    stageName, rawText.length(),
                    cleaned.substring(0, Math.min(200, cleaned.length())));
            throw new IllegalStateException("Failed to parse " + stageName + " output: " + ex.getMessage(), ex);
        }
    }

    /**
     * Strips markdown code fences and extracts the JSON object/array.
     * Handles: ```json ... ```, ``` ... ```, leading text before {, trailing text
     * after }.
     */
    private String cleanJson(String raw) {
        if (raw == null)
            return "{}";
        String text = raw.strip();

        // Remove markdown code fences
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline != -1)
                text = text.substring(firstNewline + 1);
            if (text.endsWith("```"))
                text = text.substring(0, text.lastIndexOf("```"));
            text = text.strip();
        }

        // Find the JSON object boundaries
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            text = text.substring(start, end + 1);
        }

        return text.strip();
    }

    private void validateFinancials(FinancialProjection f) {
        if (f == null)
            throw new IllegalStateException("FinancialProjection is null");
        if (f.yearlyProjections() == null || f.yearlyProjections().size() != 5) {
            throw new IllegalStateException(
                    "FinancialProjection must have exactly 5 years, got: "
                            + (f.yearlyProjections() == null ? "null" : f.yearlyProjections().size()));
        }
    }

    // ── Summary builders for STAGE_COMPLETE events ────────────────────

    private String buildMarketSummary(MarketReport r) {
        return String.format("TAM/SAM/SOM: %s | Business model: %s",
                truncate(r.tamSamSom(), 80), truncate(r.pricingAndBusinessModel(), 60));
    }

    private String buildProductSummary(ProductSpec s) {
        int featureCount = s.coreFeatures() != null ? s.coreFeatures().size() : 0;
        return String.format("%d core features | MVP: %s",
                featureCount, truncate(s.mvpScope(), 80));
    }

    private String buildFinancialSummary(FinancialProjection f) {
        return String.format("Investment: $%,.0f | Break-even: Year %d | Model: %s",
                f.requiredInvestmentUSD(), f.breakEvenYear(), f.revenueModel());
    }

    private String buildPitchSummary(PitchDeck p) {
        return String.format("\"%s\" — %d slides | %s",
                p.title(), p.slideCount(), truncate(p.tagline(), 60));
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }

    private String truncate(String s, int max) {
        if (s == null)
            return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private void emit(PipelineProgress event) {
        eventPublisher.publishEvent(event);
    }
}
