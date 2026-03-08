package com.startup.pitch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Startup Pitch Generator — Multi-Agent AI Application
 *
 * Architecture:
 *   MarketAnalystAgent → ProductManagerAgent → FinancialAnalystAgent
 *       → PitchCreatorAgent → JudgeAgent
 *
 * Tech stack:
 *   - Spring Boot 3.4 (Web, Thymeleaf, Async, Cache)
 *   - LangChain4j 0.36 (AI Services, Tools)
 *   - OpenAI GPT-4o-mini (via LangChain4j)
 *   - HTMX + Alpine.js + Tailwind CSS (frontend)
 *   - GraalVM Native Image (low memory footprint)
 *
 * Run JVM mode: ./mvnw spring-boot:run -DOPENAI_API_KEY=sk-...
 * Build native:  ./mvnw -Pnative package
 * Run native:    ./target/pitch-generator
 */
@SpringBootApplication
@EnableAsync
@EnableCaching
public class StartupPitchApplication {

    public static void main(String[] args) {
        SpringApplication.run(StartupPitchApplication.class, args);
    }
}
