package com.startup.pitch;

import com.startup.pitch.model.*;
import com.startup.pitch.orchestrator.PitchOrchestrator;
import com.startup.pitch.service.PitchGenerationService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/**
 * Integration tests for the pitch generation pipeline.
 *
 * Tests are skipped if OPENAI_API_KEY is not set (CI environments).
 * For a full end-to-end test, set the env var before running.
 *
 * Run with: OPENAI_API_KEY=sk-... ./mvnw test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "langchain4j.open-ai.chat-model.api-key=${OPENAI_API_KEY:skip-tests}",
        "app.agent.judge.enabled=true"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StartupPitchApplicationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PitchGenerationService generationService;

    private static final String TEST_CONCEPT = "A mobile app that helps urban commuters find and share last-mile transportation options like bikes, scooters, and walking routes";

    @Test
    @Order(1)
    @DisplayName("Application context loads successfully")
    void contextLoads() {
        assertThat(generationService).isNotNull();
    }

    @Test
    @Order(2)
    @DisplayName("Index page renders without errors")
    void indexPageLoads() {
        ResponseEntity<String> response = restTemplate
                .getForEntity("http://localhost:" + port + "/", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("PitchForge");
        assertThat(response.getBody()).contains("Process Startup Concept");
    }

    @Test
    @Order(3)
    @DisplayName("Status endpoint returns proper JSON structure")
    void statusEndpointWorks() {
        ResponseEntity<PitchController.StatusResponse> response = restTemplate
                .getForEntity("http://localhost:" + port + "/status/nonexistent",
                        PitchController.StatusResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(4)
    @DisplayName("Full pipeline produces valid output (requires OPENAI_API_KEY)")
    void fullPipelineProducesValidOutput() throws InterruptedException {
        assumeTrue(
                !System.getenv().getOrDefault("OPENAI_API_KEY", "").isBlank()
                        && !System.getenv("OPENAI_API_KEY").equals("skip-tests"),
                "Skipping: OPENAI_API_KEY not set");

        String sessionId = "test-" + System.currentTimeMillis();
        StartupIdeaRequest request = new StartupIdeaRequest(TEST_CONCEPT, "Urban commuters", null, null);

        generationService.startGeneration(sessionId, request);

        // Wait for pipeline (max 120s)
        PipelineResult result = null;
        for (int i = 0; i < 120; i++) {
            Thread.sleep(1000);
            result = generationService.getResult(sessionId);
            if (result != null)
                break;
        }

        assertThat(result).isNotNull();
        assertThat(result.successful()).isTrue();

        // Validate each stage
        assertThat(result.marketReport()).isNotNull();
        assertThat(result.marketReport().problemDefinition()).isNotBlank();

        assertThat(result.productSpec()).isNotNull();
        assertThat(result.productSpec().coreFeatures()).isNotEmpty();
        assertThat(result.productSpec().coreFeatures().size()).isBetween(3, 6);

        assertThat(result.financialProjection()).isNotNull();
        assertThat(result.financialProjection().yearlyProjections()).hasSize(5);

        assertThat(result.pitchDeck()).isNotNull();
        assertThat(result.pitchDeck().marpMarkdown()).contains("---");
        assertThat(result.pitchDeck().slideCount()).isGreaterThan(0);
    }

    // Import for the controller's inner record
    static class PitchController {
        record StatusResponse(String sessionId, String status, String stage,
                int progress, String errorMessage, boolean redirectToResult) {
        }
    }
}
