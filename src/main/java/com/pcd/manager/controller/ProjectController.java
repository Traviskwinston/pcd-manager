package com.pcd.manager.controller;

import com.pcd.manager.model.Project;
import com.pcd.manager.service.ProjectService;
import com.pcd.manager.service.LocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final LocationService locationService;

    @Autowired
    public ProjectController(ProjectService projectService, LocationService locationService) {
        this.projectService = projectService;
        this.locationService = locationService;
    }

    @GetMapping
    public String listProjects(Model model) {
        List<Project> projects = projectService.getAllProjects();
        model.addAttribute("projects", projects);
        return "projects/list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        Project project = new Project();
        
        // Set default location if available
        locationService.getDefaultLocation().ifPresent(project::setLocation);
        
        model.addAttribute("project", project);
        model.addAttribute("locations", locationService.getAllLocations());
        return "projects/form";
    }

    @GetMapping("/{id}")
    public String showProject(@PathVariable Long id, Model model) {
        projectService.getProjectById(id).ifPresent(project -> model.addAttribute("project", project));
        return "projects/details";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        projectService.getProjectById(id).ifPresent(project -> model.addAttribute("project", project));
        model.addAttribute("locations", locationService.getAllLocations());
        return "projects/form";
    }

    @PostMapping
    public String saveProject(@ModelAttribute Project project) {
        projectService.saveProject(project);
        return "redirect:/projects";
    }

    @PostMapping("/{id}/delete")
    public String deleteProject(@PathVariable Long id) {
        projectService.deleteProject(id);
        return "redirect:/projects";
    }
} 