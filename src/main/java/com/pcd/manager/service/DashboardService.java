package com.pcd.manager.service;

import com.pcd.manager.model.Passdown;
import com.pcd.manager.model.TrackTrend;
import com.pcd.manager.repository.PassdownRepository;
import com.pcd.manager.repository.ToolRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final Logger logger = LoggerFactory.getLogger(DashboardService.class);
    
    private final PassdownRepository passdownRepository;
    private final ToolRepository toolRepository;
    private final TrackTrendService trackTrendService;

    @Autowired
    public DashboardService(PassdownRepository passdownRepository, 
                           ToolRepository toolRepository,
                           TrackTrendService trackTrendService) {
        this.passdownRepository = passdownRepository;
        this.toolRepository = toolRepository;
        this.trackTrendService = trackTrendService;
    }

    /**
     * Get cached dashboard data including recent passdowns and filter data
     */
    @Cacheable(value = "dashboard-data", key = "'recent-passdowns-and-filters'")
    public Map<String, Object> getDashboardData() {
        logger.info("Fetching dashboard data (cacheable)");
        
        Map<String, Object> dashboardData = new HashMap<>();
        
        // Get passdowns from the past 2 weeks
        LocalDate twoWeeksAgo = LocalDate.now().minusWeeks(2);
        LocalDate today = LocalDate.now();
        List<Passdown> recentPassdowns = passdownRepository.findByDateBetweenOrderByDateDesc(twoWeeksAgo, today);
        logger.info("Fetched {} passdowns from past 2 weeks ({} to {}) for dashboard.", recentPassdowns.size(), twoWeeksAgo, today);

        // Prepare filter dropdown data: distinct users and tools
        List<String> passdownUsers = recentPassdowns.stream()
                .map(pd -> pd.getUser().getName())
                .distinct()
                .collect(Collectors.toList());
        List<String> passdownTools = recentPassdowns.stream()
                .map(pd -> pd.getTool() != null ? pd.getTool().getName() : null)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        dashboardData.put("recentPassdowns", recentPassdowns);
        dashboardData.put("passdownUsers", passdownUsers);
        dashboardData.put("passdownTools", passdownTools);
        
        return dashboardData;
    }

    /**
     * Get cached lightweight tool data for grid display
     */
    @Cacheable(value = "dashboard-data", key = "'grid-tool-data'")
    public List<Map<String, Object>> getGridToolData() {
        logger.info("Fetching grid tool data (cacheable)");
        
        List<Object[]> gridToolData = toolRepository.findGridViewData();
        return gridToolData.stream().map(row -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", row[0]); // t.id
            map.put("name", row[1]); // t.name
            map.put("model", row[2]); // t.model1
            map.put("serial", row[3]); // t.serialNumber1
            map.put("status", row[4] != null ? row[4].toString() : ""); // t.status
            map.put("type", row[5] != null ? row[5].toString() : ""); // t.toolType
            map.put("location", row[6]); // l.name
            map.put("hasAssignedUsers", row[7]); // hasAssignedUsers boolean
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * Get cached track trend data for filters
     */
    @Cacheable(value = "dashboard-data", key = "'track-trend-filters'")
    public List<Map<String, Object>> getTrackTrendFilters() {
        logger.info("Fetching track trend filters (cacheable)");
        
        List<TrackTrend> allTrackTrends = trackTrendService.getAllTrackTrendsWithAffectedTools();
        logger.info("Fetched {} track/trend items for filters.", allTrackTrends.size());
        
        // Convert TrackTrend data for JavaScript, including affected tools
        return allTrackTrends.stream().map(tt -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", tt.getId());
            map.put("title", tt.getName());
            
            // Extract affected tool IDs for issue filtering
            List<Long> affectedToolIds = tt.getAffectedTools().stream()
                    .map(tool -> tool.getId())
                    .collect(Collectors.toList());
            map.put("affectedTools", affectedToolIds);
            
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * Asynchronously warm up dashboard caches
     * This can be called during application startup or periodically
     */
    @Async("cacheExecutor")
    public CompletableFuture<Void> warmUpDashboardCachesAsync() {
        logger.info("Starting async dashboard cache warming");
        long startTime = System.currentTimeMillis();
        
        try {
            // Warm up all dashboard caches in parallel
            CompletableFuture<Map<String, Object>> dashboardDataFuture = 
                CompletableFuture.supplyAsync(() -> getDashboardData());
            CompletableFuture<List<Map<String, Object>>> gridDataFuture = 
                CompletableFuture.supplyAsync(() -> getGridToolData());
            CompletableFuture<List<Map<String, Object>>> filtersFuture = 
                CompletableFuture.supplyAsync(() -> getTrackTrendFilters());
            
            // Wait for all cache warming operations to complete
            CompletableFuture.allOf(dashboardDataFuture, gridDataFuture, filtersFuture).get();
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Completed async dashboard cache warming in {}ms", duration);
            
        } catch (Exception e) {
            logger.error("Error during async dashboard cache warming: {}", e.getMessage(), e);
        }
        
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Asynchronously refresh specific dashboard cache
     */
    @Async("cacheExecutor")
    public CompletableFuture<Void> refreshDashboardCacheAsync(String cacheType) {
        logger.info("Starting async refresh of dashboard cache: {}", cacheType);
        long startTime = System.currentTimeMillis();
        
        try {
            switch (cacheType.toLowerCase()) {
                case "dashboard-data":
                    getDashboardData();
                    break;
                case "grid-data":
                    getGridToolData();
                    break;
                case "filters":
                    getTrackTrendFilters();
                    break;
                case "all":
                    warmUpDashboardCachesAsync().get();
                    break;
                default:
                    logger.warn("Unknown cache type for refresh: {}", cacheType);
                    return CompletableFuture.completedFuture(null);
            }
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Completed async refresh of {} cache in {}ms", cacheType, duration);
            
        } catch (Exception e) {
            logger.error("Error during async cache refresh for {}: {}", cacheType, e.getMessage(), e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
} 
