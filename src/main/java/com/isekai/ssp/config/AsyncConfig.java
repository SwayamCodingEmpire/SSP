package com.isekai.ssp.config;

import org.springframework.boot.task.SimpleAsyncTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Async configuration for AI processing tasks.
 *
 * All AI operations (analysis, translation, RAG embedding calls) are I/O-bound —
 * they block waiting on HTTP responses from OpenAI / Anthropic / pgvector.
 * Virtual threads park during I/O at near-zero cost, freeing the underlying
 * platform thread for other work. No pool size or queue capacity to tune.
 *
 * spring.threads.virtual.enabled=true also enables virtual threads for Spring MVC,
 * WebFlux, WebSocket, and JPA bootstrap via the auto-configured applicationTaskExecutor.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "aiTaskExecutor")
    public SimpleAsyncTaskExecutor aiTaskExecutor(SimpleAsyncTaskExecutorBuilder builder) {
        return builder
                .virtualThreads(true)
                .threadNamePrefix("ai-vt-")
                .build();
    }
}