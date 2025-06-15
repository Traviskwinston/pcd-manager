package com.pcd.manager.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for asynchronous operations
 * Enables @Async annotations and configures thread pools for different types of operations
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    private static final Logger logger = LoggerFactory.getLogger(AsyncConfig.class);

    /**
     * General purpose async executor for lightweight operations
     * Used for: cache operations, notifications, logging
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("Async-General-");
        executor.setRejectedExecutionHandler((r, executor1) -> {
            logger.warn("General async task rejected, running synchronously");
            r.run();
        });
        executor.initialize();
        logger.info("Initialized general async executor: core={}, max={}, queue={}", 
                   4, 8, 100);
        return executor;
    }

    /**
     * Database operations executor for bulk queries and data loading
     * Used for: bulk data loading, dashboard aggregation, report generation
     */
    @Bean(name = "databaseExecutor")
    public Executor databaseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("Async-DB-");
        executor.setRejectedExecutionHandler((r, executor1) -> {
            logger.warn("Database async task rejected, running synchronously");
            r.run();
        });
        executor.initialize();
        logger.info("Initialized database async executor: core={}, max={}, queue={}", 
                   3, 6, 50);
        return executor;
    }

    /**
     * File operations executor for file transfers, uploads, and I/O operations
     * Used for: file transfers, document processing, image operations
     */
    @Bean(name = "fileExecutor")
    public Executor fileExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("Async-File-");
        executor.setRejectedExecutionHandler((r, executor1) -> {
            logger.warn("File async task rejected, running synchronously");
            r.run();
        });
        executor.initialize();
        logger.info("Initialized file async executor: core={}, max={}, queue={}", 
                   2, 4, 25);
        return executor;
    }

    /**
     * Cache operations executor for cache warming and maintenance
     * Used for: cache warming, cache eviction, cache statistics
     */
    @Bean(name = "cacheExecutor")
    public Executor cacheExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("Async-Cache-");
        executor.setRejectedExecutionHandler((r, executor1) -> {
            logger.warn("Cache async task rejected, running synchronously");
            r.run();
        });
        executor.initialize();
        logger.info("Initialized cache async executor: core={}, max={}, queue={}", 
                   2, 3, 20);
        return executor;
    }
} 