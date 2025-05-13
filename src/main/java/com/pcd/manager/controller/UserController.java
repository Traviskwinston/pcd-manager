package com.pcd.manager.controller;

import com.pcd.manager.model.User;
import com.pcd.manager.repository.LocationRepository;
import com.pcd.manager.repository.ToolRepository;
import com.pcd.manager.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/users")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class UserController {

    private final UserService userService;
    private final LocationRepository locationRepository;
    private final ToolRepository toolRepository;

    @Autowired
    public UserController(UserService userService, LocationRepository locationRepository, ToolRepository toolRepository) {
        this.userService = userService;
        this.locationRepository = locationRepository;
        this.toolRepository = toolRepository;
    }

    @GetMapping
    public String listUsers(Model model) {
        List<User> users = userService.getAllUsers();
        model.addAttribute("users", users);
        return "users/list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("user", new User());
        model.addAttribute("locations", locationRepository.findAll());
        model.addAttribute("tools", toolRepository.findAll());
        return "users/form";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            User user = userService.getUserById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + id));
            model.addAttribute("user", user);
            model.addAttribute("locations", locationRepository.findAll());
            model.addAttribute("tools", toolRepository.findAll());
            return "users/form";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error finding user: " + e.getMessage());
            return "redirect:/users";
        }
    }

    @PostMapping
    public String saveUser(@ModelAttribute User user, RedirectAttributes redirectAttributes) {
        try {
            // Check if it's a new user or an update
            if (user.getId() == null) {
                userService.createUser(user);
                redirectAttributes.addFlashAttribute("message", "User created successfully");
            } else {
                userService.updateUser(user);
                redirectAttributes.addFlashAttribute("message", "User updated successfully");
            }
            return "redirect:/users";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error saving user: " + e.getMessage());
            redirectAttributes.addFlashAttribute("user", user);
            return "redirect:/users" + (user.getId() != null ? "/edit/" + user.getId() : "/new");
        }
    }

    @PostMapping("/{id}/delete")
    public String deleteUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            // Check if user exists first
            userService.getUserById(id)
                .orElseThrow(() -> new RuntimeException("Cannot delete: User not found with ID: " + id));
                
            userService.deleteUser(id);
            redirectAttributes.addFlashAttribute("message", "User deleted successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error deleting user: " + e.getMessage());
        }
        return "redirect:/users";
    }
} 