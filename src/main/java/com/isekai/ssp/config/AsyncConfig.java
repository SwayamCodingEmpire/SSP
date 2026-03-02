package com.isekai.ssp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async configuration for AI processing tasks.
 * AI operations (analysis, translation) run on a dedicated thread pool.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Value("${ssp.async.core-pool-size:4}")
    private int corePoolSize;

    @Value("${ssp.async.max-pool-size:8}")
    private int maxPoolSize;

    @Value("${ssp.async.queue-capacity:100}")
    private int queueCapacity;

    @Bean(name = "aiTaskExecutor")
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("ai-");
        executor.initialize();
        return executor;
    }
}
