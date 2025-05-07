package com.pcd.manager.controller;

import com.pcd.manager.model.TrackTrend;
import com.pcd.manager.model.Tool;
import com.pcd.manager.service.TrackTrendService;
import com.pcd.manager.service.ToolService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.HashSet;

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
        model.addAttribute("trackTrends", trackTrends);
        return "tracktrend/list";
    }

    @GetMapping("/{id}")
    public String viewTrackTrend(@PathVariable Long id, Model model) {
        TrackTrend tt = trackTrendService.getTrackTrendById(id)
                .orElseThrow(() -> new IllegalArgumentException("TrackTrend not found: " + id));
        model.addAttribute("trackTrend", tt);
        return "tracktrend/details";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("trackTrend", new TrackTrend());
        model.addAttribute("allTools", trackTrendService.getAllTools());
        return "tracktrend/form";
    }

    @PostMapping
    public String saveTrackTrend(@ModelAttribute TrackTrend trackTrend,
                                 @RequestParam(name = "toolIds", required = false) List<Long> toolIds) {
        Set<Tool> tools = new HashSet<>();
        if (toolIds != null) {
            for (Long toolId : toolIds) {
                toolService.getToolById(toolId).ifPresent(tools::add);
            }
        }
        trackTrend.setAffectedTools(tools);
        trackTrendService.saveTrackTrend(trackTrend);
        return "redirect:/tracktrend";
    }
} 