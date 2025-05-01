package com.pcd.manager.controller;

import com.pcd.manager.model.Project;
import com.pcd.manager.service.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService projectService;

    @Autowired
    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public String listProjects(Model model) {
        List<Project> projects = projectService.getAllProjects();
        model.addAttribute("projects", projects);
        return "projects/list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("project", new Project());
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