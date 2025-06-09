package com.pcd.manager.controller;

import com.pcd.manager.model.TrackTrend;
import com.pcd.manager.model.TrackTrendComment;
import com.pcd.manager.model.Tool;
import com.pcd.manager.service.TrackTrendService;
import com.pcd.manager.service.ToolService;
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

@Controller
@RequestMapping("/tracktrend")
public class TrackTrendController {

    private final TrackTrendService trackTrendService;
    private final ToolService toolService;

    @Autowired
    public TrackTrendController(TrackTrendService trackTrendService, ToolService toolService) {
        this.trackTrendService = trackTrendService;
        this.toolService = toolService;
    }

    @GetMapping
    public String listTrackTrends(Model model) {
        List<TrackTrend> trackTrends = trackTrendService.getAllTrackTrends();
        
        // Create a map to store related RMAs for each track trend
        java.util.Map<Long, List<com.pcd.manager.model.Rma>> relatedRmasMap = new java.util.HashMap<>();
        
        // Explicitly load collections for each track trend
        for (TrackTrend tt : trackTrends) {
            try {
                // Load comments 
                List<TrackTrendComment> comments = trackTrendService.getCommentsForTrackTrend(tt.getId());
                tt.setComments(comments);
                
                // Explicitly initialize affected tools collection to prevent LazyInitializationException
                tt.getAffectedTools().size(); // This forces initialization
                
                // Load related RMAs for this Track/Trend
                List<com.pcd.manager.model.Rma> relatedRmas = trackTrendService.getRelatedRmas(tt.getId());
                relatedRmasMap.put(tt.getId(), relatedRmas);
                
            } catch (Exception e) {
                // Log the error but continue processing
                System.err.println("Error loading data for TrackTrend ID " + tt.getId() + ": " + e.getMessage());
                tt.setComments(new ArrayList<>());
                relatedRmasMap.put(tt.getId(), new ArrayList<>());
            }
        }
        
        model.addAttribute("trackTrends", trackTrends);
        model.addAttribute("relatedRmasMap", relatedRmasMap);
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
        model.addAttribute("allTrackTrends", trackTrendService.getAllTrackTrends());
        return "tracktrend/form";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        TrackTrend trackTrend = trackTrendService.getTrackTrendById(id)
                .orElseThrow(() -> new IllegalArgumentException("TrackTrend not found: " + id));
        model.addAttribute("trackTrend", trackTrend);
        model.addAttribute("allTools", trackTrendService.getAllTools());
        model.addAttribute("allTrackTrends", trackTrendService.getAvailableRelatedTrackTrends(id));
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
        model.addAttribute("allTrackTrends", trackTrendService.getAllTrackTrends());
        model.addAttribute("preSelectedTool", tool);
        return "tracktrend/form";
    }

    @GetMapping("/related/{relatedId}/new")
    public String showCreateFormForRelatedTrackTrend(@PathVariable Long relatedId, Model model) {
        TrackTrend trackTrend = new TrackTrend();
        TrackTrend relatedTrackTrend = trackTrendService.getTrackTrendById(relatedId)
                .orElseThrow(() -> new IllegalArgumentException("TrackTrend not found: " + relatedId));
        
        // Pre-select the related track trend
        Set<TrackTrend> relatedTrackTrends = new HashSet<>();
        relatedTrackTrends.add(relatedTrackTrend);
        trackTrend.setRelatedTrackTrends(relatedTrackTrends);
        
        model.addAttribute("trackTrend", trackTrend);
        model.addAttribute("allTools", trackTrendService.getAllTools());
        model.addAttribute("allTrackTrends", trackTrendService.getAvailableRelatedTrackTrends(null));
        model.addAttribute("preSelectedTrackTrend", relatedTrackTrend);
        return "tracktrend/form";
    }

    @PostMapping
    public String saveTrackTrend(
            @ModelAttribute TrackTrend trackTrend,
            @RequestParam(name = "toolIds", required = false) List<Long> toolIds,
            @RequestParam(name = "relatedTrackTrendIds", required = false) List<Long> relatedTrackTrendIds) {
        
        // Handle tool relationships
        Set<Tool> tools = new HashSet<>();
        if (toolIds != null) {
            for (Long toolId : toolIds) {
                toolService.getToolById(toolId).ifPresent(tools::add);
            }
        }
        trackTrend.setAffectedTools(tools);
        
        // Handle related track trend relationships
        Set<TrackTrend> relatedTrackTrends = new HashSet<>();
        if (relatedTrackTrendIds != null) {
            for (Long relatedId : relatedTrackTrendIds) {
                trackTrendService.getTrackTrendById(relatedId).ifPresent(relatedTrackTrends::add);
            }
        }
        trackTrend.setRelatedTrackTrends(relatedTrackTrends);
        
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
} 