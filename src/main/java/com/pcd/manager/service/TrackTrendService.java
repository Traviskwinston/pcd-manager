package com.pcd.manager.service;

import com.pcd.manager.model.TrackTrend;
import com.pcd.manager.model.TrackTrendComment;
import com.pcd.manager.model.Tool;
import com.pcd.manager.model.User;
import com.pcd.manager.repository.TrackTrendRepository;
import com.pcd.manager.repository.TrackTrendCommentRepository;
import com.pcd.manager.repository.ToolRepository;
import com.pcd.manager.repository.UserRepository;
import com.pcd.manager.repository.RmaRepository;
import com.pcd.manager.model.Rma;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

@Service
@Transactional
public class TrackTrendService {

    private final TrackTrendRepository trackTrendRepository;
    private final TrackTrendCommentRepository trackTrendCommentRepository;
    private final ToolRepository toolRepository;
    private final UserRepository userRepository;
    private final RmaRepository rmaRepository;
    private static final Logger logger = LoggerFactory.getLogger(TrackTrendService.class);

    @Autowired
    public TrackTrendService(TrackTrendRepository trackTrendRepository, 
                            TrackTrendCommentRepository trackTrendCommentRepository,
                            ToolRepository toolRepository,
                            UserRepository userRepository,
                            RmaRepository rmaRepository) {
        this.trackTrendRepository = trackTrendRepository;
        this.trackTrendCommentRepository = trackTrendCommentRepository;
        this.toolRepository = toolRepository;
        this.userRepository = userRepository;
        this.rmaRepository = rmaRepository;
    }

    public List<TrackTrend> getAllTrackTrends() {
        // Use custom method to fetch with join to eagerly load relationships
        List<TrackTrend> trackTrends = trackTrendRepository.findAllWithAffectedTools();
        // Sort in memory
        return trackTrends.stream()
            .sorted(Comparator.comparing(TrackTrend::getName, String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());
    }

    public Optional<TrackTrend> getTrackTrendById(Long id) {
        return trackTrendRepository.findById(id);
    }

    public TrackTrend saveTrackTrend(TrackTrend trackTrend) {
        return trackTrendRepository.save(trackTrend);
    }

    public void deleteTrackTrend(Long id) {
        trackTrendRepository.deleteById(id);
    }

    /**
     * Fetch all tools for selection in forms.
     */
    public List<Tool> getAllTools() {
        return toolRepository.findAll().stream()
            .sorted(Comparator.comparing(Tool::getName, String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());
    }

    /**
     * Get all TrackTrends that include the given tool
     */
    public List<TrackTrend> getTrackTrendsByToolId(Long toolId) {
        return trackTrendRepository.findByAffectedToolsId(toolId).stream()
            .sorted(Comparator.comparing(TrackTrend::getName, String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());
    }
    
    /**
     * Bulk load TrackTrends for multiple tools (optimization for list views)
     */
    public List<TrackTrend> getTrackTrendsByToolIds(List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            logger.info("getTrackTrendsByToolIds called with null or empty toolIds");
            return new ArrayList<>();
        }
        logger.info("getTrackTrendsByToolIds called with {} tool IDs: {}", toolIds.size(), toolIds);
        
        try {
            // Use the new method that eagerly loads affectedTools to avoid lazy loading issues
            List<TrackTrend> result = trackTrendRepository.findByAffectedToolsIdInWithAffectedTools(toolIds).stream()
                .sorted(Comparator.comparing(TrackTrend::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
                
            logger.info("Repository query returned {} TrackTrends", result.size());
            for (TrackTrend tt : result) {
                logger.info("TrackTrend: {} has {} affected tools", 
                           tt.getName(), 
                           tt.getAffectedTools() != null ? tt.getAffectedTools().size() : 0);
            }
            
            return result;
        } catch (Exception e) {
            logger.error("Error in bulk query with eager loading, falling back to individual queries: {}", e.getMessage(), e);
            
            // Fallback: use individual queries (less efficient but should work)
            Set<TrackTrend> allTrackTrends = new HashSet<>();
            for (Long toolId : toolIds) {
                try {
                    List<TrackTrend> trackTrendsForTool = trackTrendRepository.findByAffectedToolsId(toolId);
                    allTrackTrends.addAll(trackTrendsForTool);
                } catch (Exception ex) {
                    logger.error("Error loading track/trends for tool {}: {}", toolId, ex.getMessage());
                }
            }
            
            logger.info("Fallback method returned {} unique TrackTrends", allTrackTrends.size());
            return allTrackTrends.stream()
                .sorted(Comparator.comparing(TrackTrend::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
        }
    }
    
    /**
     * Get all TrackTrends that are related to the given TrackTrend
     */
    public List<TrackTrend> getRelatedTrackTrends(Long trackTrendId) {
        return trackTrendRepository.findByRelatedTrackTrendsId(trackTrendId).stream()
            .sorted(Comparator.comparing(TrackTrend::getName, String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());
    }
    
    /**
     * Get all available TrackTrends to be related (excluding the current one)
     */
    public List<TrackTrend> getAvailableRelatedTrackTrends(Long currentTrackTrendId) {
        if (currentTrackTrendId == null) {
            return trackTrendRepository.findAll().stream()
                .sorted(Comparator.comparing(TrackTrend::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
        }
        
        // Get all track trends and filter out the current one
        return trackTrendRepository.findAll().stream()
            .filter(tt -> !tt.getId().equals(currentTrackTrendId))
            .sorted(Comparator.comparing(TrackTrend::getName, String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());
    }
    
    /**
     * Add a comment to a TrackTrend
     */
    public TrackTrendComment addComment(Long trackTrendId, String content, String userEmail) {
        TrackTrend trackTrend = trackTrendRepository.findById(trackTrendId)
            .orElseThrow(() -> new IllegalArgumentException("TrackTrend not found: " + trackTrendId));
        
        User user = userRepository.findByEmailIgnoreCase(userEmail)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));
        
        TrackTrendComment comment = new TrackTrendComment();
        comment.setContent(content);
        comment.setTrackTrend(trackTrend);
        comment.setUser(user);
        comment.setCreatedDate(LocalDateTime.now());
        
        TrackTrendComment savedComment = trackTrendCommentRepository.save(comment);
        
        // Add the comment to the TrackTrend's comment list
        trackTrend.getComments().add(savedComment);
        trackTrendRepository.save(trackTrend);
        
        return savedComment;
    }
    
    /**
     * Get all comments for a TrackTrend
     */
    public List<TrackTrendComment> getCommentsForTrackTrend(Long trackTrendId) {
        return trackTrendCommentRepository.findByTrackTrendIdOrderByCreatedDateDesc(trackTrendId);
    }
    
    /**
     * Get all RMAs related to the tools in this TrackTrend
     */
    public List<Rma> getRelatedRmas(Long trackTrendId) {
        TrackTrend trackTrend = trackTrendRepository.findById(trackTrendId)
            .orElseThrow(() -> new IllegalArgumentException("TrackTrend not found: " + trackTrendId));
        
        Set<Tool> affectedTools = trackTrend.getAffectedTools();
        if (affectedTools.isEmpty()) {
            return List.of(); // Return empty list if no tools
        }
        
        // Get all RMAs for the affected tools
        List<Long> toolIds = affectedTools.stream()
            .map(Tool::getId)
            .collect(Collectors.toList());
        
        return rmaRepository.findByToolIdIn(toolIds).stream()
            .sorted(Comparator.comparing(Rma::getId).reversed()) // Most recent first
            .collect(Collectors.toList());
    }

    @Transactional
    public boolean linkFileToTrackTrend(Long trackTrendId, String filePath, String originalFileName, 
                                        String fileType, String sourceEntityType, Long sourceEntityId) {
        logger.info("Attempting to link file {} (type: {}) to TrackTrend ID: {}. Original source: {} {}", 
                    filePath, fileType, trackTrendId, sourceEntityType, sourceEntityId);

        Optional<TrackTrend> ttOpt = trackTrendRepository.findById(trackTrendId);
        if (ttOpt.isEmpty()) {
            logger.warn("TrackTrend not found with ID: {}", trackTrendId);
            return false;
        }
        TrackTrend trackTrend = ttOpt.get();

        if (filePath == null || filePath.isBlank()) {
            logger.warn("File path is null or blank, cannot link to TrackTrend {}", trackTrendId);
            return false;
        }

        // Initialize collections if they are null - though with @ElementCollection and default initialization in model, this might not be strictly needed.
        if (trackTrend.getDocumentPaths() == null) trackTrend.setDocumentPaths(new HashSet<>());
        if (trackTrend.getDocumentNames() == null) trackTrend.setDocumentNames(new HashMap<>());
        if (trackTrend.getPicturePaths() == null) trackTrend.setPicturePaths(new HashSet<>());
        if (trackTrend.getPictureNames() == null) trackTrend.setPictureNames(new HashMap<>());

        if ("document".equalsIgnoreCase(fileType)) {
            if (trackTrend.getDocumentPaths().contains(filePath)) {
                logger.info("Document {} already linked to TrackTrend {}. Ensuring name is updated.", filePath, trackTrendId);
                trackTrend.getDocumentNames().put(filePath, originalFileName); // Update name
            } else {
                trackTrend.getDocumentPaths().add(filePath);
                trackTrend.getDocumentNames().put(filePath, originalFileName);
                logger.info("Linked document {} to TrackTrend {}", filePath, trackTrendId);
            }
        } else if ("picture".equalsIgnoreCase(fileType)) {
            if (trackTrend.getPicturePaths().contains(filePath)) {
                logger.info("Picture {} already linked to TrackTrend {}. Ensuring name is updated.", filePath, trackTrendId);
                trackTrend.getPictureNames().put(filePath, originalFileName); // Update name
            } else {
                trackTrend.getPicturePaths().add(filePath);
                trackTrend.getPictureNames().put(filePath, originalFileName);
                logger.info("Linked picture {} to TrackTrend {}", filePath, trackTrendId);
            }
        } else {
            logger.warn("Unsupported file type '{}' for linking to TrackTrend.", fileType);
            return false;
        }

        try {
            trackTrendRepository.save(trackTrend);
            return true;
        } catch (Exception e) {
            logger.error("Error saving TrackTrend {} after attempting to link file {}: {}", trackTrendId, filePath, e.getMessage(), e);
            return false;
        }
    }

    /**
     * OPTIMIZATION: Bulk gets lightweight track/trend data for multiple tools to avoid loading full objects
     * Returns only essential fields: id, name, toolId
     * @param toolIds The list of tool IDs
     * @return List of Object arrays with lightweight track/trend data
     */
    public List<Object[]> findTrackTrendListDataByToolIds(List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        logger.debug("Bulk getting lightweight track/trend data for {} tool IDs", toolIds.size());
        List<Object[]> trackTrendData = trackTrendRepository.findTrackTrendListDataByToolIds(toolIds);
        logger.debug("Found {} lightweight track/trend records for {} tool IDs", trackTrendData.size(), toolIds.size());
        return trackTrendData;
    }
} 