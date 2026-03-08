package com.startup.pitch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configures dedicated thread pools for async pipeline execution.
 *
 * Separates pipeline threads from the web server thread pool,
 * preventing slow LLM calls from blocking HTTP request handling.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Primary executor for pipeline orchestration.
     * Core = 4 ensures multiple concurrent sessions run in parallel.
     */
    @Bean("pipelineExecutor")
    public Executor pipelineExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("pipeline-");
        executor.setKeepAliveSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * Separate executor for SSE emitter management.
     * Keeps SSE heartbeats responsive regardless of pipeline load.
     */
    @Bean("sseExecutor")
    public Executor sseExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("sse-");
        executor.initialize();
        return executor;
    }
}
