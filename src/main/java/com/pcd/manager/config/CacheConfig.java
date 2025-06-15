package com.pcd.manager.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    private static final Logger logger = LoggerFactory.getLogger(CacheConfig.class);

    /**
     * Production cache manager using Caffeine for better performance and memory management
     */
    @Bean
    @Profile("prod")
    public CacheManager productionCacheManager() {
        logger.info("Configuring Caffeine cache manager for production");
        
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        
        // Configure different cache strategies for different data types
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(1000)  // Limit cache size for 2GB RAM
            .expireAfterWrite(30, TimeUnit.MINUTES)  // Expire after 30 minutes
            .expireAfterAccess(15, TimeUnit.MINUTES) // Expire after 15 minutes of no access
            .recordStats()); // Enable cache statistics
            
        // Pre-define cache names for better organization
        cacheManager.setCacheNames(
            Arrays.asList(
                "tools-list",           // Tools list data (5 min)
                "rma-list",            // RMA list data (5 min)  
                "tracktrend-list",     // TrackTrend list data (10 min)
                "locations-list",      // Locations list (30 min - rarely changes)
                "users-list",          // Users list (30 min - rarely changes)
                "tool-details",        // Individual tool details (10 min)
                "rma-details",         // Individual RMA details (5 min)
                "dashboard-data",      // Dashboard aggregated data (2 min)
                "lightweight-counts",  // Comment/RMA/etc counts (3 min)
                "dropdown-data"        // Dropdown/select data (15 min)
            )
        );
        
        logger.info("Configured {} cache regions for production", cacheManager.getCacheNames().size());
        return cacheManager;
    }

    /**
     * Development cache manager using simple concurrent maps
     */
    @Bean
    @Profile("dev")
    public CacheManager developmentCacheManager() {
        logger.info("Configuring simple cache manager for development");
        
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        
        // Same cache names as production for consistency
        cacheManager.setCacheNames(
            Arrays.asList(
                "tools-list", "rma-list", "tracktrend-list", "locations-list", "users-list",
                "tool-details", "rma-details", "dashboard-data", "lightweight-counts", "dropdown-data"
            )
        );
        
        logger.info("Configured {} cache regions for development", cacheManager.getCacheNames().size());
        return cacheManager;
    }
} 
