package com.pcd.manager.controller;

import com.pcd.manager.model.Tool;
import com.pcd.manager.model.Passdown;
import com.pcd.manager.model.Location;
import com.pcd.manager.service.ToolService;
import com.pcd.manager.service.PassdownService;
import com.pcd.manager.service.LocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.pcd.manager.model.User;
import com.pcd.manager.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class AuthController {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    
    private final UserService userService;
    private final ToolService toolService;
    private final PassdownService passdownService;
    private final LocationService locationService;
    
    @Autowired
    public AuthController(UserService userService, ToolService toolService, PassdownService passdownService, LocationService locationService) {
        this.userService = userService;
        this.toolService = toolService;
        this.passdownService = passdownService;
        this.locationService = locationService;
    }
    
    @GetMapping("/login")
    public String login(@RequestParam(value = "error", required = false) String error,
                        @RequestParam(value = "logout", required = false) String logout,
                        Model model) {
        if (error != null) {
            model.addAttribute("error", "Invalid email or password!");
        }

        if (logout != null) {
            model.addAttribute("message", "You have been logged out successfully.");
        }
        
        // Add default location to model for the navigation fragment
        Optional<Location> defaultLocation = locationService.getDefaultLocation();
        defaultLocation.ifPresent(location -> model.addAttribute("defaultLocation", location));

        return "login";
    }
} 