package com.pcd.manager.service;

import com.pcd.manager.repository.*;
import com.pcd.manager.model.Passdown;
import com.pcd.manager.model.TrackTrend;
import com.pcd.manager.model.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service for asynchronous data operations
 * Demonstrates how to implement async operations for better performance
 */
@Service
public class AsyncDataService {

    private static final Logger logger = LoggerFactory.getLogger(AsyncDataService.class);

    @Autowired
    private RmaRepository rmaRepository;
    
    @Autowired
    private PassdownRepository passdownRepository;
    
    @Autowired
    private ToolCommentRepository toolCommentRepository;
    
    @Autowired
    private TrackTrendRepository trackTrendRepository;
    
    @Autowired
    private ToolRepository toolRepository;
    
    @Autowired
    private TrackTrendService trackTrendService;

    /**
     * Asynchronously load dashboard data
     * This loads all the data needed for the dashboard in parallel
     */
    @Async("taskExecutor")
    @Transactional(readOnly = true)
    public CompletableFuture<Map<String, Object>> loadDashboardDataAsync() {
        logger.info("Starting async dashboard data loading");
        long startTime = System.currentTimeMillis();
        
        Map<String, Object> dashboardData = new HashMap<>();
        
        try {
            // Load recent passdowns
            LocalDate twoWeeksAgo = LocalDate.now().minusWeeks(2);
            LocalDate today = LocalDate.now();
            List<Passdown> recentPassdowns = passdownRepository.findByDateBetweenOrderByDateDesc(twoWeeksAgo, today);
            
            List<String> passdownUsers = recentPassdowns.stream()
                    .map(pd -> pd.getUser().getName())
                    .distinct()
                    .collect(Collectors.toList());
            List<String> passdownTools = recentPassdowns.stream()
                    .map(pd -> pd.getTool() != null ? pd.getTool().getName() : null)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            
            // Load grid tool data
            List<Object[]> gridToolData = toolRepository.findGridViewData();
            List<Map<String, Object>> allToolsData = gridToolData.stream().map(row -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", row[0]);
                map.put("name", row[1]);
                map.put("model", row[2]);
                map.put("serial", row[3]);
                map.put("status", row[4] != null ? row[4].toString() : "");
                map.put("type", row[5] != null ? row[5].toString() : "");
                map.put("location", row[6]);
                map.put("hasAssignedUsers", row[7]);
                return map;
            }).collect(Collectors.toList());
            
            // Load track trends
            List<TrackTrend> allTrackTrends = trackTrendService.getAllTrackTrendsWithAffectedTools();
            List<Map<String, Object>> formattedTrackTrends = allTrackTrends.stream().map(tt -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", tt.getId());
                map.put("title", tt.getName());
                List<Long> affectedToolIds = tt.getAffectedTools().stream()
                        .map(Tool::getId)
                        .collect(Collectors.toList());
                map.put("affectedTools", affectedToolIds);
                return map;
            }).collect(Collectors.toList());
            
            // Add all data to result map
            dashboardData.put("recentPassdowns", recentPassdowns);
            dashboardData.put("passdownUsers", passdownUsers);
            dashboardData.put("passdownTools", passdownTools);
            dashboardData.put("allToolsData", allToolsData);
            dashboardData.put("formattedTrackTrends", formattedTrackTrends);
            
        } catch (Exception e) {
            logger.error("Error in async dashboard data loading: {}", e.getMessage(), e);
        }
        
        long duration = System.currentTimeMillis() - startTime;
        logger.info("Completed async dashboard data loading in {}ms", duration);
        
        return CompletableFuture.completedFuture(dashboardData);
    }

    /**
     * Asynchronously load RMA data for multiple tools
     * This runs in parallel with other data loading operations
     */
    @Async("databaseExecutor")
    @Transactional(readOnly = true)
    public CompletableFuture<Map<Long, List<Map<String, Object>>>> loadRmaDataAsync(List<Long> toolIds) {
        logger.info("Starting async RMA data loading for {} tools", toolIds.size());
        long startTime = System.currentTimeMillis();
        
        Map<Long, List<Map<String, Object>>> toolRmasMap = new HashMap<>();
        
        try {
            List<Object[]> rmaData = rmaRepository.findRmaListDataByToolIds(toolIds);
            logger.info("Found {} RMA records from async query", rmaData.size());
            
            for (Object[] row : rmaData) {
                Long rmaId = (Long) row[0];
                String rmaNumber = (String) row[1];
                Object status = row[2];
                Long toolId = (Long) row[3];
                
                Map<String, Object> rmaInfo = new HashMap<>();
                rmaInfo.put("id", rmaId);
                rmaInfo.put("rmaNumber", rmaNumber);
                rmaInfo.put("status", status);
                
                toolRmasMap.computeIfAbsent(toolId, k -> new java.util.ArrayList<>()).add(rmaInfo);
            }
            
            // Ensure all tools have entries (even if empty)
            for (Long toolId : toolIds) {
                toolRmasMap.computeIfAbsent(toolId, k -> new java.util.ArrayList<>());
            }
            
        } catch (Exception e) {
            logger.error("Error in async RMA data loading: {}", e.getMessage(), e);
        }
        
        long duration = System.currentTimeMillis() - startTime;
        logger.info("Completed async RMA data loading in {}ms", duration);
        
        return CompletableFuture.completedFuture(toolRmasMap);
    }

    /**
     * Asynchronously load Passdown data for multiple tools
     */
    @Async("databaseExecutor")
    @Transactional(readOnly = true)
    public CompletableFuture<Map<Long, List<Map<String, Object>>>> loadPassdownDataAsync(List<Long> toolIds) {
        logger.info("Starting async Passdown data loading for {} tools", toolIds.size());
        long startTime = System.currentTimeMillis();
        
        Map<Long, List<Map<String, Object>>> toolPassdownsMap = new HashMap<>();
        
        try {
            List<Object[]> passdownData = passdownRepository.findPassdownListDataByToolIds(toolIds);
            logger.info("Found {} Passdown records from async query", passdownData.size());
            
            for (Object[] row : passdownData) {
                Long passdownId = (Long) row[0];
                Object date = row[1];
                String userName = (String) row[2];
                String comment = (String) row[3];
                Long toolId = (Long) row[4];
                
                Map<String, Object> passdownInfo = new HashMap<>();
                passdownInfo.put("id", passdownId);
                passdownInfo.put("date", date);
                passdownInfo.put("userName", userName);
                passdownInfo.put("comment", comment);
                
                toolPassdownsMap.computeIfAbsent(toolId, k -> new java.util.ArrayList<>()).add(passdownInfo);
            }
            
            // Ensure all tools have entries (even if empty)
            for (Long toolId : toolIds) {
                toolPassdownsMap.computeIfAbsent(toolId, k -> new java.util.ArrayList<>());
            }
            
        } catch (Exception e) {
            logger.error("Error in async Passdown data loading: {}", e.getMessage(), e);
        }
        
        long duration = System.currentTimeMillis() - startTime;
        logger.info("Completed async Passdown data loading in {}ms", duration);
        
        return CompletableFuture.completedFuture(toolPassdownsMap);
    }

    /**
     * Asynchronously load Comment data for multiple tools
     */
    @Async("databaseExecutor")
    @Transactional(readOnly = true)
    public CompletableFuture<Map<Long, List<Map<String, Object>>>> loadCommentDataAsync(List<Long> toolIds) {
        logger.info("Starting async Comment data loading for {} tools", toolIds.size());
        long startTime = System.currentTimeMillis();
        
        Map<Long, List<Map<String, Object>>> toolCommentsMap = new HashMap<>();
        
        try {
            List<Object[]> commentData = toolCommentRepository.findCommentListDataByToolIds(toolIds);
            logger.info("Found {} Comment records from async query", commentData.size());
            
            for (Object[] row : commentData) {
                Long commentId = (Long) row[0];
                Object createdDate = row[1];
                String userName = (String) row[2];
                String content = (String) row[3];
                Long toolId = (Long) row[4];
                
                Map<String, Object> commentInfo = new HashMap<>();
                commentInfo.put("id", commentId);
                commentInfo.put("createdDate", createdDate);
                commentInfo.put("userName", userName);
                commentInfo.put("content", content);
                
                toolCommentsMap.computeIfAbsent(toolId, k -> new java.util.ArrayList<>()).add(commentInfo);
            }
            
            // Ensure all tools have entries (even if empty)
            for (Long toolId : toolIds) {
                toolCommentsMap.computeIfAbsent(toolId, k -> new java.util.ArrayList<>());
            }
            
        } catch (Exception e) {
            logger.error("Error in async Comment data loading: {}", e.getMessage(), e);
        }
        
        long duration = System.currentTimeMillis() - startTime;
        logger.info("Completed async Comment data loading in {}ms", duration);
        
        return CompletableFuture.completedFuture(toolCommentsMap);
    }

    /**
     * Asynchronously load Track/Trend data for multiple tools
     */
    @Async("databaseExecutor")
    @Transactional(readOnly = true)
    public CompletableFuture<Map<Long, List<Map<String, Object>>>> loadTrackTrendDataAsync(List<Long> toolIds) {
        logger.info("Starting async Track/Trend data loading for {} tools", toolIds.size());
        long startTime = System.currentTimeMillis();
        
        Map<Long, List<Map<String, Object>>> toolTrackTrendsMap = new HashMap<>();
        
        try {
            List<Object[]> trackTrendData = trackTrendRepository.findTrackTrendListDataByToolIds(toolIds);
            logger.info("Found {} Track/Trend records from async query", trackTrendData.size());
            
            for (Object[] row : trackTrendData) {
                Long trackTrendId = (Long) row[0];
                String name = (String) row[1];
                Long toolId = (Long) row[2];
                
                Map<String, Object> trackTrendInfo = new HashMap<>();
                trackTrendInfo.put("id", trackTrendId);
                trackTrendInfo.put("name", name);
                
                toolTrackTrendsMap.computeIfAbsent(toolId, k -> new java.util.ArrayList<>()).add(trackTrendInfo);
            }
            
            // Ensure all tools have entries (even if empty)
            for (Long toolId : toolIds) {
                toolTrackTrendsMap.computeIfAbsent(toolId, k -> new java.util.ArrayList<>());
            }
            
        } catch (Exception e) {
            logger.error("Error in async Track/Trend data loading: {}", e.getMessage(), e);
        }
        
        long duration = System.currentTimeMillis() - startTime;
        logger.info("Completed async Track/Trend data loading in {}ms", duration);
        
        return CompletableFuture.completedFuture(toolTrackTrendsMap);
    }

    /**
     * Load all tool-related data asynchronously in parallel
     * This method demonstrates how to coordinate multiple async operations
     */
    public CompletableFuture<Map<String, Map<Long, List<Map<String, Object>>>>> loadAllToolDataAsync(List<Long> toolIds) {
        logger.info("Starting parallel async data loading for {} tools", toolIds.size());
        long startTime = System.currentTimeMillis();
        
        // Start all async operations in parallel
        CompletableFuture<Map<Long, List<Map<String, Object>>>> rmaFuture = loadRmaDataAsync(toolIds);
        CompletableFuture<Map<Long, List<Map<String, Object>>>> passdownFuture = loadPassdownDataAsync(toolIds);
        CompletableFuture<Map<Long, List<Map<String, Object>>>> commentFuture = loadCommentDataAsync(toolIds);
        CompletableFuture<Map<Long, List<Map<String, Object>>>> trackTrendFuture = loadTrackTrendDataAsync(toolIds);
        
        // Combine all results when they complete
        return CompletableFuture.allOf(rmaFuture, passdownFuture, commentFuture, trackTrendFuture)
            .thenApply(v -> {
                Map<String, Map<Long, List<Map<String, Object>>>> result = new HashMap<>();
                
                try {
                    result.put("rmas", rmaFuture.get());
                    result.put("passdowns", passdownFuture.get());
                    result.put("comments", commentFuture.get());
                    result.put("trackTrends", trackTrendFuture.get());
                    
                    long duration = System.currentTimeMillis() - startTime;
                    logger.info("Completed parallel async data loading in {}ms", duration);
                    
                } catch (Exception e) {
                    logger.error("Error combining async results: {}", e.getMessage(), e);
                }
                
                return result;
            });
    }
} 