package com.pcd.manager.config;

import com.pcd.manager.service.DashboardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Startup cache warmer that asynchronously warms up caches when the application starts
 * This improves first-user experience by pre-loading frequently accessed data
 */
@Component
public class StartupCacheWarmer {

    private static final Logger logger = LoggerFactory.getLogger(StartupCacheWarmer.class);
    
    @Autowired
    private DashboardService dashboardService;

    /**
     * Warm up caches asynchronously when the application is ready
     * This runs in the background and doesn't delay application startup
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async("cacheExecutor")
    public void warmUpCachesOnStartup() {
        logger.info("Starting async cache warming on application startup");
        long startTime = System.currentTimeMillis();
        
        try {
            // Warm up dashboard caches in the background
            dashboardService.warmUpDashboardCachesAsync().get();
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Completed startup cache warming in {}ms", duration);
            
        } catch (Exception e) {
            logger.error("Error during startup cache warming: {}", e.getMessage(), e);
        }
    }

    /**
     * Refresh caches periodically (can be called by scheduled tasks)
     */
    @Async("cacheExecutor")
    public void refreshCaches() {
        logger.info("Starting scheduled cache refresh");
        long startTime = System.currentTimeMillis();
        
        try {
            // Refresh all dashboard caches
            dashboardService.refreshDashboardCacheAsync("all").get();
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Completed scheduled cache refresh in {}ms", duration);
            
        } catch (Exception e) {
            logger.error("Error during scheduled cache refresh: {}", e.getMessage(), e);
        }
    }
} 