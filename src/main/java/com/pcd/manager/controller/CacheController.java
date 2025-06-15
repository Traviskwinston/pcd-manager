package com.pcd.manager.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.cache.caffeine.CaffeineCache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/admin/cache")
public class CacheController {

    private static final Logger logger = LoggerFactory.getLogger(CacheController.class);
    
    @Autowired(required = false)
    private CacheManager cacheManager;

    /**
     * Display cache statistics page
     */
    @GetMapping
    public String cacheStats(Model model) {
        if (cacheManager == null) {
            model.addAttribute("error", "Cache manager not available");
            return "admin/cache-stats";
        }

        Map<String, Map<String, Object>> cacheStats = new HashMap<>();
        
        for (String cacheName : cacheManager.getCacheNames()) {
            try {
                org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
                Map<String, Object> stats = new HashMap<>();
                
                if (cache instanceof CaffeineCache) {
                    CaffeineCache caffeineCache = (CaffeineCache) cache;
                    com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
                    CacheStats cacheStatsObj = nativeCache.stats();
                    
                    stats.put("hitCount", cacheStatsObj.hitCount());
                    stats.put("missCount", cacheStatsObj.missCount());
                    stats.put("hitRate", String.format("%.2f%%", cacheStatsObj.hitRate() * 100));
                    stats.put("evictionCount", cacheStatsObj.evictionCount());
                    stats.put("estimatedSize", nativeCache.estimatedSize());
                    stats.put("averageLoadTime", String.format("%.2f ms", cacheStatsObj.averageLoadPenalty() / 1_000_000.0));
                } else {
                    stats.put("type", cache.getClass().getSimpleName());
                    stats.put("hitCount", "N/A");
                    stats.put("missCount", "N/A");
                    stats.put("hitRate", "N/A");
                    stats.put("evictionCount", "N/A");
                    stats.put("estimatedSize", "N/A");
                    stats.put("averageLoadTime", "N/A");
                }
                
                cacheStats.put(cacheName, stats);
            } catch (Exception e) {
                logger.error("Error getting stats for cache {}: {}", cacheName, e.getMessage());
                Map<String, Object> errorStats = new HashMap<>();
                errorStats.put("error", e.getMessage());
                cacheStats.put(cacheName, errorStats);
            }
        }
        
        model.addAttribute("cacheStats", cacheStats);
        return "admin/cache-stats";
    }

    /**
     * Get cache statistics as JSON
     */
    @GetMapping("/api/stats")
    @ResponseBody
    public Map<String, Object> getCacheStatsJson() {
        Map<String, Object> response = new HashMap<>();
        
        if (cacheManager == null) {
            response.put("error", "Cache manager not available");
            return response;
        }

        Map<String, Map<String, Object>> cacheStats = new HashMap<>();
        
        for (String cacheName : cacheManager.getCacheNames()) {
            try {
                org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
                Map<String, Object> stats = new HashMap<>();
                
                if (cache instanceof CaffeineCache) {
                    CaffeineCache caffeineCache = (CaffeineCache) cache;
                    com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
                    CacheStats cacheStatsObj = nativeCache.stats();
                    
                    stats.put("hitCount", cacheStatsObj.hitCount());
                    stats.put("missCount", cacheStatsObj.missCount());
                    stats.put("hitRate", cacheStatsObj.hitRate());
                    stats.put("evictionCount", cacheStatsObj.evictionCount());
                    stats.put("estimatedSize", nativeCache.estimatedSize());
                    stats.put("averageLoadTime", cacheStatsObj.averageLoadPenalty());
                } else {
                    stats.put("type", cache.getClass().getSimpleName());
                }
                
                cacheStats.put(cacheName, stats);
            } catch (Exception e) {
                logger.error("Error getting stats for cache {}: {}", cacheName, e.getMessage());
                Map<String, Object> errorStats = new HashMap<>();
                errorStats.put("error", e.getMessage());
                cacheStats.put(cacheName, errorStats);
            }
        }
        
        response.put("caches", cacheStats);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    /**
     * Clear all caches
     */
    @GetMapping("/clear")
    @ResponseBody
    public Map<String, Object> clearAllCaches() {
        Map<String, Object> response = new HashMap<>();
        
        if (cacheManager == null) {
            response.put("error", "Cache manager not available");
            return response;
        }

        int clearedCount = 0;
        for (String cacheName : cacheManager.getCacheNames()) {
            try {
                org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    cache.clear();
                    clearedCount++;
                    logger.info("Cleared cache: {}", cacheName);
                }
            } catch (Exception e) {
                logger.error("Error clearing cache {}: {}", cacheName, e.getMessage());
            }
        }
        
        response.put("success", true);
        response.put("clearedCaches", clearedCount);
        response.put("message", "Cleared " + clearedCount + " caches");
        logger.info("Manually cleared {} caches", clearedCount);
        
        return response;
    }
} 
