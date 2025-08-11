package com.pcd.manager.controller;

import com.pcd.manager.model.TrackTrend;
import com.pcd.manager.model.TrackTrendComment;
import com.pcd.manager.model.Tool;
import com.pcd.manager.service.TrackTrendService;
import com.pcd.manager.service.ToolService;
import com.pcd.manager.repository.RmaRepository;
import com.pcd.manager.model.Rma;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/tracktrend")
public class TrackTrendController {

    private static final Logger logger = LoggerFactory.getLogger(TrackTrendController.class);
    
    private final TrackTrendService trackTrendService;
    private final ToolService toolService;
    private final RmaRepository rmaRepository;

    @Autowired
    public TrackTrendController(TrackTrendService trackTrendService, ToolService toolService, RmaRepository rmaRepository) {
        this.trackTrendService = trackTrendService;
        this.toolService = toolService;
        this.rmaRepository = rmaRepository;
    }

    @GetMapping
    public String listTrackTrends(Model model) {
        logger.info("=== LOADING TRACK/TREND LIST PAGE (OPTIMIZED) ===");
        
        // Use query with affected tools and comments loaded for list view icons
        List<TrackTrend> trackTrends = trackTrendService.getAllTrackTrendsWithAffectedToolsAndComments();
        logger.info("Loaded {} track/trends for list view", trackTrends.size());
        
        if (trackTrends.isEmpty()) {
            model.addAttribute("trackTrends", trackTrends);
            model.addAttribute("relatedRmasMap", new HashMap<>());
            model.addAttribute("trackTrendCommentsMap", new HashMap<>());
            return "tracktrend/list";
        }
        
        List<Long> trackTrendIds = trackTrends.stream().map(TrackTrend::getId).collect(Collectors.toList());
        
        // OPTIMIZATION: Bulk load related data using lightweight queries
        Map<Long, List<Map<String, Object>>> relatedRmasMap = new HashMap<>();
        Map<Long, Integer> trackTrendCommentsMap = new HashMap<>();
        Map<Long, List<Map<String, Object>>> affectedToolsMap = new HashMap<>();
        Map<Long, List<Map<String, Object>>> commentsDataMap = new HashMap<>();
        
        // Bulk load lightweight RMA counts for each track/trend
        try {
            for (TrackTrend tt : trackTrends) {
                if (tt.getAffectedTools() != null && !tt.getAffectedTools().isEmpty()) {
                    List<Long> toolIds = tt.getAffectedTools().stream()
                            .map(Tool::getId)
                            .collect(Collectors.toList());
                    
                    List<Object[]> rmaData = rmaRepository.findRmaListDataByToolIds(toolIds);
                    List<Map<String, Object>> rmaList = new ArrayList<>();
                    
                    for (Object[] row : rmaData) {
                        Map<String, Object> rmaInfo = new HashMap<>();
                        rmaInfo.put("id", row[0]);
                        rmaInfo.put("rmaNumber", row[1]);
                        rmaInfo.put("status", row[2]);
                        rmaInfo.put("sapNotificationNumber", null); // Add this for template compatibility
                        
                        // Add tool information - create a nested map to match template expectations
                        Long toolId = (Long) row[3]; // tool.id from the query
                        if (toolId != null) {
                            // Find the tool name from the affected tools
                            String toolName = tt.getAffectedTools().stream()
                                    .filter(tool -> tool.getId().equals(toolId))
                                    .map(Tool::getName)
                                    .findFirst()
                                    .orElse("Unknown Tool");
                            
                            Map<String, Object> toolInfo = new HashMap<>();
                            toolInfo.put("name", toolName);
                            rmaInfo.put("tool", toolInfo);
                        } else {
                            rmaInfo.put("tool", null);
                        }
                        
                        rmaList.add(rmaInfo);
                    }
                    
                    relatedRmasMap.put(tt.getId(), rmaList);
                } else {
                    relatedRmasMap.put(tt.getId(), new ArrayList<>());
                }
                
                // Pre-compute affected tools data to avoid template accessing full Tool objects
                List<Map<String, Object>> toolsData = new ArrayList<>();
                if (tt.getAffectedTools() != null) {
                    for (Tool tool : tt.getAffectedTools()) {
                        Map<String, Object> toolData = new HashMap<>();
                        toolData.put("id", tool.getId());
                        toolData.put("name", tool.getName());
                        toolData.put("secondaryName", tool.getSecondaryName());
                        toolsData.add(toolData);
                    }
                }
                affectedToolsMap.put(tt.getId(), toolsData);
                
                // Pre-compute comments data to avoid template accessing full Comment objects
                List<Map<String, Object>> commentsData = new ArrayList<>();
                try {
                    if (tt.getComments() != null) {
                        for (TrackTrendComment comment : tt.getComments()) {
                            Map<String, Object> commentData = new HashMap<>();
                            commentData.put("id", comment.getId());
                            commentData.put("content", comment.getContent());
                            commentData.put("createdDate", comment.getCreatedDate());
                            if (comment.getUser() != null) {
                                commentData.put("userName", comment.getUser().getName());
                            }
                            commentsData.add(commentData);
                        }
                    }
                    trackTrendCommentsMap.put(tt.getId(), commentsData.size());
                } catch (Exception e) {
                    // If lazy loading fails, set empty data
                    trackTrendCommentsMap.put(tt.getId(), 0);
                    logger.warn("Failed to load comments for TrackTrend {}: {}", tt.getId(), e.getMessage());
                }
                commentsDataMap.put(tt.getId(), commentsData);
            }
            logger.info("Loaded related RMA data for {} track/trends", trackTrends.size());
        } catch (Exception e) {
            logger.error("Error loading related RMA data: {}", e.getMessage(), e);
        }
        
        model.addAttribute("trackTrends", trackTrends);
        model.addAttribute("relatedRmasMap", relatedRmasMap);
        model.addAttribute("trackTrendCommentsMap", trackTrendCommentsMap);
        model.addAttribute("affectedToolsMap", affectedToolsMap);
        model.addAttribute("commentsDataMap", commentsDataMap);
        
        logger.info("=== COMPLETED TRACK/TREND LIST PAGE LOADING (OPTIMIZED) ===");
        return "tracktrend/list";
    }

    @GetMapping("/{id}")
    public String viewTrackTrend(@PathVariable Long id, Model model) {
        TrackTrend tt = trackTrendService.getTrackTrendById(id)
                .orElseThrow(() -> new IllegalArgumentException("TrackTrend not found: " + id));
        model.addAttribute("trackTrend", tt);
        
        // Add comments to the model
        List<TrackTrendComment> comments = trackTrendService.getCommentsForTrackTrend(id);
        model.addAttribute("comments", comments);
        
        // Add related RMAs instead of related Track/Trends
        try {
            List<com.pcd.manager.model.Rma> relatedRmas = trackTrendService.getRelatedRmas(id);
            model.addAttribute("relatedRmas", relatedRmas);
        } catch (Exception e) {
            model.addAttribute("relatedRmas", new ArrayList<>());
        }
        
        return "tracktrend/details";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("trackTrend", new TrackTrend());
        model.addAttribute("allTools", trackTrendService.getAllTools());
        // Provide RMAs for association selection
        model.addAttribute("allRmas", rmaRepository.findAllOrderedByWrittenDateDesc());
        return "tracktrend/form";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        TrackTrend trackTrend = trackTrendService.getTrackTrendById(id)
                .orElseThrow(() -> new IllegalArgumentException("TrackTrend not found: " + id));
        model.addAttribute("trackTrend", trackTrend);
        model.addAttribute("allTools", trackTrendService.getAllTools());
        // Provide RMAs for association selection
        model.addAttribute("allRmas", rmaRepository.findAllOrderedByWrittenDateDesc());
        return "tracktrend/form";
    }

    @GetMapping("/tool/{toolId}/new")
    public String showCreateFormForTool(@PathVariable Long toolId, Model model) {
        TrackTrend trackTrend = new TrackTrend();
        Tool tool = toolService.getToolById(toolId)
                .orElseThrow(() -> new IllegalArgumentException("Tool not found: " + toolId));
        
        // Pre-select the tool
        Set<Tool> selectedTools = new HashSet<>();
        selectedTools.add(tool);
        trackTrend.setAffectedTools(selectedTools);
        
        model.addAttribute("trackTrend", trackTrend);
        model.addAttribute("allTools", trackTrendService.getAllTools());
        model.addAttribute("allRmas", rmaRepository.findAllOrderedByWrittenDateDesc());
        model.addAttribute("preSelectedTool", tool);
        return "tracktrend/form";
    }

    @GetMapping("/related/{relatedId}/new")
    public String showCreateFormForRelatedTrackTrend(@PathVariable Long relatedId, Model model) {
        // Repurpose to pre-select a related RMA if desired in future; for now load normal create with RMAs
        TrackTrend trackTrend = new TrackTrend();
        model.addAttribute("trackTrend", trackTrend);
        model.addAttribute("allTools", trackTrendService.getAllTools());
        model.addAttribute("allRmas", rmaRepository.findAllOrderedByWrittenDateDesc());
        return "tracktrend/form";
    }

    @PostMapping
    public String saveTrackTrend(
            @ModelAttribute TrackTrend trackTrend,
            @RequestParam(name = "toolIds", required = false) List<Long> toolIds,
            @RequestParam(name = "relatedRmaIds", required = false) List<Long> relatedRmaIds) {
        
        // Handle tool relationships
        Set<Tool> tools = new HashSet<>();
        if (toolIds != null) {
            for (Long toolId : toolIds) {
                toolService.getToolById(toolId).ifPresent(tools::add);
            }
        }
        trackTrend.setAffectedTools(tools);
        
        // Handle related RMA relationships
        Set<Rma> relatedRmas = new HashSet<>();
        if (relatedRmaIds != null) {
            for (Long rmaId : relatedRmaIds) {
                rmaRepository.findById(rmaId).ifPresent(relatedRmas::add);
            }
        }
        trackTrend.setRelatedRmas(relatedRmas);
        
        trackTrendService.saveTrackTrend(trackTrend);
        return "redirect:/tracktrend";
    }
    
    @PostMapping("/{id}/comment")
    public String addComment(
            @PathVariable Long id,
            @RequestParam("content") String content,
            Authentication authentication) {
        
        if (authentication == null) {
            throw new IllegalStateException("User must be authenticated to add comments");
        }
        
        String userEmail = authentication.getName();
        trackTrendService.addComment(id, content, userEmail);
        
        return "redirect:/tracktrend/" + id;
    }
    
    @GetMapping("/{id}/post-comment")
    public String getPostComment(@PathVariable Long id) {
        return "redirect:/tracktrend/" + id;
    }
    
    @PostMapping("/{id}/post-comment")
    public String postCommentEndpoint(
            @PathVariable Long id,
            @RequestParam("content") String content,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        
        try {
            if (authentication == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to post comments");
                return "redirect:/tracktrend/" + id;
            }
            
            String userEmail = authentication.getName();
            TrackTrendComment comment = trackTrendService.addComment(id, content, userEmail);
            redirectAttributes.addFlashAttribute("success", "Comment added successfully");
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error adding comment: " + e.getMessage());
        }
        
        return "redirect:/tracktrend/" + id;
    }
    
    /**
     * Add a tool to a Track/Trend
     */
    @PostMapping("/add-tool")
    public String addToolToTrackTrend(@RequestParam Long toolId, 
                                     @RequestParam Long trackTrendId, 
                                     RedirectAttributes redirectAttributes) {
        try {
            logger.info("Adding tool {} to Track/Trend {}", toolId, trackTrendId);
            
            // Get the tool and track/trend
            Tool tool = toolService.getToolById(toolId)
                .orElseThrow(() -> new RuntimeException("Tool not found with id: " + toolId));
            
            TrackTrend trackTrend = trackTrendService.getTrackTrendById(trackTrendId)
                .orElseThrow(() -> new RuntimeException("Track/Trend not found with id: " + trackTrendId));
            
            // Check if tool is already in this track/trend
            if (trackTrend.getAffectedTools().contains(tool)) {
                redirectAttributes.addFlashAttribute("warning", "Tool is already associated with this Track/Trend");
                return "redirect:/tools/" + toolId;
            }
            
            // Add the tool to the track/trend
            trackTrend.getAffectedTools().add(tool);
            trackTrendService.saveTrackTrend(trackTrend);
            
            redirectAttributes.addFlashAttribute("success", 
                "Tool successfully added to Track/Trend: " + trackTrend.getName());
            
        } catch (Exception e) {
            logger.error("Error adding tool to Track/Trend", e);
            redirectAttributes.addFlashAttribute("error", "Error adding tool to Track/Trend: " + e.getMessage());
        }
        
        return "redirect:/tools/" + toolId;
    }
} 