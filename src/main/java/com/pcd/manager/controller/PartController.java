package com.pcd.manager.controller;

import com.pcd.manager.model.Part;
import com.pcd.manager.service.PartService;
import com.pcd.manager.service.LocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/parts")
public class PartController {

    private final PartService partService;
    private final LocationService locationService;

    @Autowired
    public PartController(PartService partService, LocationService locationService) {
        this.partService = partService;
        this.locationService = locationService;
    }

    @GetMapping
    public String listParts(Model model) {
        List<Part> parts = partService.getAllParts();
        model.addAttribute("parts", parts);
        return "parts/list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        Part part = new Part();
        
        // Set default location if available
        locationService.getDefaultLocation().ifPresent(part::setLocation);
        
        model.addAttribute("part", part);
        model.addAttribute("locations", locationService.getAllLocations());
        return "parts/form";
    }

    @GetMapping("/{id}")
    public String showPart(@PathVariable Long id, Model model) {
        partService.getPartById(id).ifPresent(part -> model.addAttribute("part", part));
        return "parts/details";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        partService.getPartById(id).ifPresent(part -> model.addAttribute("part", part));
        model.addAttribute("locations", locationService.getAllLocations());
        return "parts/form";
    }

    @PostMapping
    public String savePart(@ModelAttribute Part part) {
        partService.savePart(part);
        return "redirect:/parts";
    }

    @PostMapping("/{id}/delete")
    public String deletePart(@PathVariable Long id) {
        partService.deletePart(id);
        return "redirect:/parts";
    }
} 