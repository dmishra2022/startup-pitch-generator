# 🚀 PitchForge — Multi-Agent Startup Pitch Generator

> Turn any startup idea into a complete investor pitch deck in ~90 seconds using a 5-agent AI pipeline.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Browser (HTMX + Alpine.js + Tailwind)   │
│  ┌──────────────┐    SSE Stream     ┌─────────────────────────┐ │
│  │  Idea Form   │ ──── POST ──────► │  PitchController        │ │
│  │  (index.html)│                   │  /generate              │ │
│  └──────────────┘                   │  /stream/{id}  (SSE)    │ │
│  ┌──────────────┐ ◄─ events ─────── │  /result/{id}           │ │
│  │  Progress    │                   │  /download/{id}         │ │
│  │  (real-time) │                   └────────┬────────────────┘ │
│  └──────────────┘                            │                   │
│  ┌──────────────┐                            │                   │
│  │  Results     │                   ┌────────▼────────────────┐ │
│  │  (tabbed)    │                   │  PitchGenerationService  │ │
│  └──────────────┘                   │  @Async + SSE Emitter   │ │
└─────────────────────────────────────└────────┬────────────────┘─┘
                                               │
                                    ┌──────────▼────────────┐
                                    │   PitchOrchestrator   │
                                    │   Linear Pipeline     │
                                    └──────────┬────────────┘
                                               │
              ┌────────────┬─────────────┬─────┴──────────┬────────────┐
              ▼            ▼             ▼                 ▼            ▼
     ┌──────────────┐ ┌─────────┐ ┌──────────┐  ┌──────────────┐ ┌────────┐
     │Market Analyst│ │Product  │ │Financial │  │Pitch Creator │ │ Judge  │
     │+ WebSearch   │ │Manager  │ │Analyst   │  │              │ │ Agent  │
     │Tool          │ │         │ │+ Finance │  │              │ │        │
     └──────────────┘ └─────────┘ │Tool      │  └──────────────┘ └────────┘
            │               │     └──────────┘          │              │
            ▼               ▼          ▼                 ▼              ▼
      MarketReport    ProductSpec  FinancialProj     PitchDeck    AgentEval×4
```

### Design Principles

| Principle | Implementation |
|-----------|----------------|
| **No shared memory** | Each agent receives only what it needs — no messy global state |
| **Linear pipeline** | Market → Product → Finance → Pitch → Evaluate |
| **Schema enforcement** | Strict JSON prompts + validation + retry on FinancialAgent |
| **Tool isolation** | Market gets WebResearch; Financial gets Calculator; others are tool-free |
| **Per-agent tuning** | Different temperature per agent (0.3 for JSON, 0.8 for pitch writing) |
| **GraalVM-native** | AOT hints for all records; virtual threads for eviction scheduling |

---

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+
- OpenAI API key (`gpt-4o-mini` or `gpt-4o`)

### Run in JVM mode

```bash
git clone <repo>
cd startup-pitch-generator

export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
```

Open http://localhost:8080

### Build & run as GraalVM Native Image (low memory ~50MB)

```bash
# Requires GraalVM 21+ with native-image installed
# Install: sdk install java 21.0.2-graal

./mvnw -Pnative package

export OPENAI_API_KEY=sk-...
./target/pitch-generator
```

Native binary uses ~50–80MB RAM vs ~300MB for JVM. Startup: <100ms.

---

## Agent Breakdown

### 1. Market Analyst Agent
- **Model config**: temperature=0.6, researchModel
- **Tools**: `WebResearchTool` (market size, competitors, trends, pricing)
- **Output**: `MarketReport` record with 8 structured fields
- **Smart behavior**: Asks one clarifying question if idea is too vague, then proceeds

### 2. Product Manager Agent
- **Model config**: temperature=0.3, structuredModel
- **Tools**: None (works from market context only)
- **Output**: `ProductSpec` with features list, MVP scope, success metrics
- **Rule enforced**: Does NOT rewrite market analysis — only extracts what matters

### 3. Financial Analyst Agent
- **Model config**: temperature=0.3, structuredModel
- **Tools**: `FinancialCalculatorTool` (CAGR, runway, CAC, LTV:CAC, revenue projection)
- **Output**: `FinancialProjection` with exactly 5 yearly entries (validated)
- **Retry policy**: Up to 3 retries on schema violations

### 4. Pitch Creator Agent
- **Model config**: temperature=0.8, creativeModel
- **Tools**: None
- **Output**: `PitchDeck` with Marp Markdown (8-slide fixed structure)
- **Fixed slides**: Title → Problem → Market → Solution → Features → Financials → Why Now → Ask

### 5. Judge Agent
- **Model config**: temperature=0.1, judgeModel (deterministic)
- **Tools**: None
- **Output**: `AgentEvaluation` with 4 scores (clarity/structure/relevance/logic) + notes
- **Can be disabled**: `app.agent.judge.enabled=false`

---

## Configuration

```yaml
# application.yml key settings

langchain4j:
  open-ai:
    chat-model:
      api-key: ${OPENAI_API_KEY}
      model-name: gpt-4o-mini   # Change to gpt-4o for higher quality
      temperature: 0.7
      max-tokens: 4096

app:
  pipeline:
    session-ttl-minutes: 60      # Auto-evict results after 1 hour
    max-concurrent-sessions: 20  # Prevent overload
    sse-timeout-seconds: 300
  agent:
    financial-analyst:
      max-retries: 3             # Retry financial schema on failure
    judge:
      enabled: true              # Set false to skip evaluation stage
```

---

## Project Structure

```
src/main/java/com/startup/pitch/
├── StartupPitchApplication.java      # Main entry point
├── config/
│   ├── LangChain4jConfig.java        # Agent bean wiring (explicit, not scanned)
│   ├── AsyncConfig.java              # Thread pools: pipeline + SSE
│   └── NativeHintsConfig.java        # GraalVM reflection hints
├── model/                            # Immutable Java records
│   ├── StartupIdeaRequest.java       # Validated user input
│   ├── MarketReport.java
│   ├── ProductSpec.java
│   ├── YearlyFinancials.java         # Nested in FinancialProjection
│   ├── FinancialProjection.java
│   ├── PitchDeck.java
│   ├── AgentEvaluation.java
│   ├── PipelineProgress.java         # SSE event
│   ├── PipelineResult.java           # Full pipeline output (with Builder)
│   └── PipelineStage.java            # Enum with displayName, progress%
├── agent/                            # LangChain4j AI Service interfaces
│   ├── MarketAnalystAgent.java
│   ├── ProductManagerAgent.java
│   ├── FinancialAnalystAgent.java
│   ├── PitchCreatorAgent.java
│   └── JudgeAgent.java
├── tools/                            # LangChain4j @Tool methods
│   ├── WebResearchTool.java
│   └── FinancialCalculatorTool.java
├── orchestrator/
│   └── PitchOrchestrator.java        # Linear pipeline: runs in @Async thread
├── service/
│   └── PitchGenerationService.java   # Session mgmt + SSE emitter registry
└── controller/
    └── PitchController.java          # Web endpoints + download
```

---

## Frontend Stack

- **Thymeleaf** — server-side HTML rendering, no JS framework required
- **HTMX** — form submission and SSE without writing fetch() calls
- **Alpine.js** — lightweight reactive UI for tabs, progress tracking
- **Tailwind CSS** (CDN) — utility-first styling
- **SSE** — `EventSource` → `SseEmitter` for real-time pipeline progress

---

## Adding a Real Search API

Replace `WebResearchTool` methods with actual HTTP calls:

```java
// Using Tavily (recommended for AI agents)
@Tool("Search for market size data for a given market category")
public String searchMarketSize(String marketCategory) {
    return tavilyClient.search(TavilySearchRequest.builder()
        .query("market size " + marketCategory + " TAM SAM")
        .maxResults(5)
        .build())
        .toFormattedString();
}
```

Popular options: `Tavily`, `Brave Search API`, `SerpAPI`, `You.com`

---

## Extending the Pipeline

Adding a new agent (e.g., Growth Strategist):

1. Create `GrowthStrategistAgent.java` interface with `@SystemMessage` + `@UserMessage`
2. Add `GrowthStrategy` record to `model/`
3. Register `@Bean` in `LangChain4jConfig.java`
4. Add a new stage to `PipelineStage` enum
5. Inject and call in `PitchOrchestrator.run()`
6. Add evaluation call + store result in `PipelineResult`
7. Add UI tab in `result.html`

---

## Testing

```bash
# Unit tests (no API key needed)
./mvnw test

# Integration tests (requires API key)
OPENAI_API_KEY=sk-... ./mvnw test

# Native image test
./mvnw -Pnative test
```

---

## License

MIT
