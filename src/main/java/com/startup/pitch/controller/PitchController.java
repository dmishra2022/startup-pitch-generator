package com.startup.pitch.controller;

import com.startup.pitch.model.*;
import com.startup.pitch.service.MarkdownService;
import com.startup.pitch.service.PitchGenerationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

/**
 * Web Controller.
 *
 * GET / → Landing page
 * POST /generate → Start pipeline, redirect to progress
 * GET /progress/{id} → Streaming progress page
 * GET /stream/{id} → SSE event stream (text/event-stream)
 * GET /result/{id} → Final results page
 * GET /status/{id} → JSON status (polling fallback)
 * GET /download/{id} → Download Marp Markdown .md file
 */
@Controller
public class PitchController {

    private static final Logger log = LoggerFactory.getLogger(PitchController.class);

    private final PitchGenerationService generationService;
    private final MarkdownService markdownService;

    public PitchController(PitchGenerationService generationService, MarkdownService markdownService) {
        this.generationService = generationService;
        this.markdownService = markdownService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("ideaRequest", new StartupIdeaRequest("", null, null));
        return "index";
    }

    @PostMapping("/generate")
    public String generate(
            @Valid @ModelAttribute("ideaRequest") StartupIdeaRequest request,
            BindingResult bindingResult,
            Model model) {

        if (bindingResult.hasErrors())
            return "index";

        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        log.info("New generation [{}]: {}", sessionId, request.concept());

        // Start pipeline async BEFORE rendering the progress page
        generationService.startGeneration(sessionId, request);

        return "redirect:/progress/" + sessionId;
    }

    @GetMapping("/progress/{sessionId}")
    public String progressPage(@PathVariable String sessionId, Model model) {
        PipelineResult result = generationService.getResult(sessionId);
        if (result != null && result.successful())
            return "redirect:/result/" + sessionId;
        if (result != null && !result.successful()) {
            model.addAttribute("error", result.errorMessage());
            return "error";
        }
        model.addAttribute("sessionId", sessionId);
        return "progress";
    }

    @GetMapping("/result/{sessionId}")
    public String resultPage(@PathVariable String sessionId, Model model) {
        PipelineResult result = generationService.getResult(sessionId);
        if (result == null)
            return "redirect:/progress/" + sessionId;
        if (!result.successful()) {
            model.addAttribute("error", result.errorMessage());
            return "error";
        }
        model.addAttribute("result", result);
        model.addAttribute("pitchDeck", result.pitchDeck());
        model.addAttribute("market", result.marketReport());
        model.addAttribute("product", result.productSpec());
        model.addAttribute("financials", result.financialProjection());

        if (result.pitchDeck() != null) {
            model.addAttribute("renderedPitch", markdownService.render(result.pitchDeck().marpMarkdown()));
        }

        model.addAttribute("overallQuality", String.format("%.1f", result.overallQuality()));
        return "result";
    }

    // ── SSE endpoint ─────────────────────────────────────────────────

    @GetMapping(value = "/stream/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String sessionId) {
        log.debug("[{}] SSE connection opened", sessionId);
        return generationService.createEmitter(sessionId);
    }

    // ── REST endpoints ────────────────────────────────────────────────

    @GetMapping("/status/{sessionId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> status(@PathVariable String sessionId) {
        PipelineResult result = generationService.getResult(sessionId);
        PipelineStage stage = generationService.getCurrentStage(sessionId);

        if (result != null && result.successful()) {
            return ResponseEntity.ok(Map.of(
                    "status", "completed", "stage", stage.name(),
                    "progress", 100, "redirectToResult", true));
        }
        if (result != null && !result.successful()) {
            return ResponseEntity.ok(Map.of(
                    "status", "failed", "stage", stage.name(),
                    "progress", -1, "errorMessage", result.errorMessage() != null ? result.errorMessage() : "",
                    "redirectToResult", false));
        }
        return ResponseEntity.ok(Map.of(
                "status", "running", "stage", stage.name(),
                "progress", stage.getProgressPercent(), "redirectToResult", false));
    }

    @GetMapping("/download/{sessionId}")
    @ResponseBody
    public ResponseEntity<byte[]> downloadDeck(@PathVariable String sessionId) {
        PipelineResult result = generationService.getResult(sessionId);
        if (result == null || result.pitchDeck() == null)
            return ResponseEntity.notFound().build();

        String markdown = result.pitchDeck().marpMarkdown();
        String filename = sanitize(result.concept()) + "-pitch.md";

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.valueOf("text/markdown"))
                .body(markdown.getBytes());
    }

    @PostMapping("/refine/{sessionId}")
    public String refine(
            @PathVariable String sessionId,
            @RequestParam("feedback") String feedback) {

        log.info("Refinement request [{}]: {}", sessionId, feedback);
        generationService.startRefinement(sessionId, feedback);

        return "redirect:/progress/" + sessionId;
    }

    private String sanitize(String s) {
        return s.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .substring(0, Math.min(40, s.length()));
    }
}
