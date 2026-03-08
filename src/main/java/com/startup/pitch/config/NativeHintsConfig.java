package com.startup.pitch.config;

import com.startup.pitch.model.*;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * GraalVM Native Image reflection and serialization hints.
 *
 * Spring Boot 3.x AOT processes most hints automatically, but
 * we explicitly register:
 * - Domain model records (Jackson deserialization)
 * - LangChain4j output parser classes
 * - Thymeleaf template processing classes
 *
 * Compile with: ./mvnw -Pnative package
 * Run: ./target/pitch-generator
 */
@Configuration
@ImportRuntimeHints(NativeHintsConfig.PitchGeneratorHints.class)
public class NativeHintsConfig {

    static class PitchGeneratorHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            // Register all domain records for Jackson reflection
            var modelClasses = new Class<?>[] {
                    StartupIdeaRequest.class,
                    MarketReport.class,
                    ProductSpec.class,
                    YearlyFinancials.class,
                    FinancialProjection.class,
                    PitchDeck.class,
                    AgentEvaluation.class,
                    PipelineProgress.class,
                    PipelineResult.class,
                    PipelineStage.class,
                    org.thymeleaf.templatemode.TemplateMode.class,
                    org.thymeleaf.context.Context.class,
                    org.thymeleaf.context.IContext.class
            };

            for (Class<?> clazz : modelClasses) {
                hints.reflection().registerType(clazz,
                        MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_PUBLIC_METHODS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.PUBLIC_FIELDS,
                        MemberCategory.DECLARED_FIELDS);
            }

            // Thymeleaf template resources
            hints.resources().registerPattern("templates/**");
            hints.resources().registerPattern("static/**");

            // Application configuration
            hints.resources().registerPattern("application.yml");
            hints.resources().registerPattern("application.yaml");
            hints.resources().registerPattern("application.properties");
        }
    }
}
