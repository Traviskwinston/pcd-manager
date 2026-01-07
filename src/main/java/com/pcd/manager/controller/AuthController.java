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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionInformation;

import com.pcd.manager.model.User;
import com.pcd.manager.service.UserService;
import com.pcd.manager.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    private final UserRepository userRepository;
    
    @Autowired
    public AuthController(UserService userService, ToolService toolService, 
                         PassdownService passdownService, LocationService locationService,
                         UserRepository userRepository) {
        this.userService = userService;
        this.toolService = toolService;
        this.passdownService = passdownService;
        this.locationService = locationService;
        this.userRepository = userRepository;
    }

    @Autowired(required = false)
    private SessionRegistry sessionRegistry;
    
    @GetMapping("/login")
    public String login(@RequestParam(value = "error", required = false) String error,
                        @RequestParam(value = "logout", required = false) String logout,
                        @RequestParam(value = "timeout", required = false) String timeout,
                        @RequestParam(value = "expired", required = false) String expired,
                        Model model) {
        if (error != null) {
            String errorMessage = "Invalid email or password!"; // Default message
            if ("bad_credentials".equals(error)) {
                errorMessage = "Invalid email or password!";
            } else if ("user_not_found".equals(error)) {
                errorMessage = "No account found with that email address.";
            } else if ("account_disabled".equals(error)) {
                errorMessage = "Your account has been disabled. Please contact an administrator.";
            }
            model.addAttribute("error", errorMessage);
            logger.warn("Login error occurred: {}", error);
        }

        if (logout != null) {
            model.addAttribute("message", "You have been logged out successfully.");
        }

        if (timeout != null) {
            model.addAttribute("error", "Your session has expired due to inactivity. Please log in again.");
            logger.info("Session timeout redirect handled");
        }

        if (expired != null) {
            model.addAttribute("error", "Your session has expired. Please log in again.");
            logger.info("Session expired redirect handled");
        }
        
        // Add default location to model for the navigation fragment
        Optional<Location> defaultLocation = locationService.getDefaultLocation();
        defaultLocation.ifPresent(location -> model.addAttribute("defaultLocation", location));

        return "login";
    }
    
    // Clear only the current HTTP session (client-initiated), keeping other auth state untouched
    @PostMapping("/api/auth/clear-my-session")
    @ResponseBody
    public String clearMySession(HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
            return "OK";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

} 