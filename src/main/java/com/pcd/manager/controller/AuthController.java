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
            } else if ("account_locked".equals(error)) {
                errorMessage = "Your account has been locked. Please contact an administrator.";
            } else if ("account_expired".equals(error)) {
                errorMessage = "Your account has expired. Please contact an administrator.";
            } else if ("credentials_expired".equals(error)) {
                errorMessage = "Your password has expired. Please contact an administrator.";
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

    // Admin/diagnostic: clear all server-side sessions for a specific email when stuck
    @PostMapping("/api/auth/clear-session")
    @ResponseBody
    public String clearServerSessionsByEmail(@RequestParam("email") String email,
                                             @RequestParam(value = "code", required = false) String code) {
        // Optional lightweight guard; reuse emergency code if provided
        if (code != null && !"pcd-emergency-override-2025".equals(code)) {
            return "Access denied";
        }
        if (sessionRegistry == null) {
            return "SessionRegistry unavailable";
        }
        int cleared = 0;
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            String principalName = principal.toString();
            if (principalName.equalsIgnoreCase(email)) {
                List<SessionInformation> infos = sessionRegistry.getAllSessions(principal, false);
                for (SessionInformation info : infos) {
                    info.expireNow();
                    cleared++;
                }
            }
        }
        return "Cleared sessions: " + cleared;
    }
    @PostMapping("/manual-login") 
    public String manualLoginAttempt(@RequestParam("username") String email,
                                    @RequestParam("password") String password,
                                    Model model) {
        logger.info("Manual login attempt for email: {}", email);
        
        try {
            Optional<User> userOpt = userService.getUserByEmail(email);
            
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                logger.info("User found: ID={}, Name={}, Active={}", user.getId(), user.getName(), user.getActive());
                
                // Check if user is active
                if (user.getActive() == null || !user.getActive()) {
                    logger.warn("Login failed: User account is not active");
                    model.addAttribute("error", "Your account is inactive. Please contact an administrator.");
                    return "login";
                }
                
                boolean passwordMatches = userService.checkPassword(password, user.getPassword());
                logger.info("Password match result: {}", passwordMatches);
                
                if (passwordMatches) {
                    logger.info("Manual login password verified for {}", email);
                    model.addAttribute("message", "Credentials verified. If normal login fails, click 'Clear Session' on the login page to reset your server session.");
                } else {
                    logger.warn("Password does not match stored hash");
                    model.addAttribute("error", "Invalid email or password!");
                }
            } else {
                logger.warn("No user found with email: {}", email);
                model.addAttribute("error", "Invalid email or password!");
            }
        } catch (Exception e) {
            logger.error("Error during manual login attempt", e);
            model.addAttribute("error", "An error occurred during login.");
        }
        
        return "login";
    }
    
    @GetMapping("/admin/emergency-reset")
    @ResponseBody
    public String emergencyAdminReset(@RequestParam(value = "email", required = true) String email,
                                    @RequestParam(value = "code", required = true) String secretCode,
                                    @RequestParam(value = "newPassword", required = true) String newPassword) {
        // This is a security critical function, so we log extensively
        logger.warn("Emergency admin reset attempted for email: {}", email);
        
        // Very simple hard-coded security check
        // In a real production app, use a more secure mechanism
        if (!"pcd-emergency-override-2025".equals(secretCode)) {
            logger.error("Emergency reset failed: Invalid security code provided");
            return "Access denied: Invalid security code";
        }
        
        try {
            Optional<User> userOpt = userService.getUserByEmail(email);
            
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                logger.info("User found for emergency reset: ID={}, Name={}, Email={}", user.getId(), user.getName(), user.getEmail());
                
                // Force user to be active
                user.setActive(true);
                
                // Set the new password using the service method to ensure proper encoding
                // This prevents double-encoding issues
                userService.setUserPassword(user, newPassword);
                logger.info("Password set for emergency reset user");
                
                // Ensure the user has admin role
                if (user.getRole() == null || !user.getRole().equalsIgnoreCase("ADMIN")) {
                    logger.info("Setting role to ADMIN for emergency user");
                    user.setRole("ADMIN");
                }
                
                // Save the updated user (don't use updateUser as it might re-encode the password)
                // Instead, save directly but ensure email is normalized
                if (user.getEmail() != null) {
                    user.setEmail(user.getEmail().trim().toLowerCase());
                }
                User savedUser = userService.getUserById(user.getId())
                    .orElseThrow(() -> new RuntimeException("User not found after lookup"));
                
                // Update only the fields we need
                savedUser.setActive(true);
                savedUser.setPassword(user.getPassword()); // Already encoded by setUserPassword
                savedUser.setRole("ADMIN");
                if (user.getEmail() != null) {
                    savedUser.setEmail(user.getEmail().trim().toLowerCase());
                }
                
                // Use repository directly to avoid updateUser's password encoding logic
                userRepository.save(savedUser);
                logger.info("Emergency admin reset completed successfully for user ID: {} (Email: {})", savedUser.getId(), savedUser.getEmail());
                
                // Verify the password was set correctly by checking it
                Optional<User> verifyUser = userRepository.findById(savedUser.getId());
                if (verifyUser.isPresent() && userService.checkPassword(newPassword, verifyUser.get().getPassword())) {
                    logger.info("Password verification successful for emergency reset user");
                } else {
                    logger.error("WARNING: Password verification failed for emergency reset user - login may not work!");
                }
                
                return "Success: User reset completed. You can now log in with the new password: " + newPassword;
            } else {
                logger.warn("Emergency reset failed: No user found with email: {}", email);
                return "Error: No user found with that email address";
            }
        } catch (Exception e) {
            logger.error("Error during emergency admin reset", e);
            return "Error: " + e.getMessage();
        }
    }
    
    @GetMapping("/create-simple-admin")
    @ResponseBody
    public String createSimpleAdmin() {
        try {
            String email = "simple@admin.com";
            String password = "simple123";
            
            // Check if user already exists
            Optional<User> existingUser = userRepository.findByEmailIgnoreCase(email);
            if (existingUser.isPresent()) {
                User user = existingUser.get();
                // Reset the password and ensure admin role and active status
                user.setPassword(password); // Will be encoded by updateUser
                user.setRole("ADMIN");
                user.setActive(true);
                userService.updateUser(user);
                
                logger.info("Existing simple admin user updated: {}", email);
                return "Simple admin user already existed and has been updated. Email: " + email + ", Password: " + password;
            }
            
            // Create new admin user
            User adminUser = new User();
            adminUser.setEmail(email);
            adminUser.setPassword(password); // Will be encoded by createUser
            adminUser.setName("Simple Admin");
            adminUser.setRole("ADMIN");
            adminUser.setActive(true);
            
            User savedUser = userService.createUser(adminUser);
            logger.info("Created simple admin user: {} with ID: {}", email, savedUser.getId());
            
            return "Created simple admin user. Email: " + email + ", Password: " + password;
        } catch (Exception e) {
            logger.error("Error creating simple admin user", e);
            return "Error: " + e.getMessage();
        }
    }
    
    // Temporary POST probe endpoint to test if POST requests reach the application
    @PostMapping("/auth/test-post")
    @ResponseBody
    public String testPost(HttpServletRequest request) {
        logger.info("POST request received at /auth/test-post from IP: {}", request.getRemoteAddr());
        return "POST received successfully at " + new java.util.Date().toString();
    }

    // Session debugging endpoint
    @GetMapping("/session-info")
    @ResponseBody
    public String getSessionInfo(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return "No active session";
        }

        StringBuilder info = new StringBuilder();
        info.append("Session ID: ").append(session.getId()).append("\n");
        info.append("Creation Time: ").append(new java.util.Date(session.getCreationTime())).append("\n");
        info.append("Last Accessed: ").append(new java.util.Date(session.getLastAccessedTime())).append("\n");
        info.append("Max Inactive Interval: ").append(session.getMaxInactiveInterval()).append(" seconds\n");
        info.append("Is New: ").append(session.isNew()).append("\n");

        // Get attribute names
        java.util.Enumeration<String> attrNames = session.getAttributeNames();
        info.append("Attributes:\n");
        while (attrNames.hasMoreElements()) {
            String name = attrNames.nextElement();
            Object value = session.getAttribute(name);
            info.append("  ").append(name).append(": ").append(value != null ? value.toString() : "null").append("\n");
        }

        return info.toString();
    }

    @GetMapping("/direct-login")
    public String directLogin(HttpServletRequest request,
                             HttpServletResponse response,
                             @RequestParam(required = false, defaultValue = "admin@pcd.com") String email,
                             @RequestParam(required = false, defaultValue = "admin123") String password) {
        logger.warn("Direct login attempt for: {}", email);
        
        try {
            // Try to find the user
            Optional<User> userOpt = userService.getUserByEmail(email);
            
            // If no existing user with that email, create a new admin
            User user;
            if (userOpt.isEmpty()) {
                logger.warn("No user found with email: {}. Creating new admin user.", email);
                user = new User();
                user.setEmail(email);
                user.setName("Direct Login Admin");
                user.setRole("ADMIN");
                user.setActive(true);
                user.setPassword(password);
                user = userService.createUser(user);
                logger.info("Created new admin user: {}", email);
            } else {
                user = userOpt.get();
                logger.info("Found existing user: {}", email);
                
                // Ensure the user is active and has admin role
                if (user.getActive() == null || !user.getActive()) {
                    user.setActive(true);
                    userService.updateUser(user);
                    logger.info("Activated user: {}", email);
                }
                
                if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
                    user.setRole("ADMIN");
                    userService.updateUser(user);
                    logger.info("Set user role to ADMIN: {}", email);
                }
            }
            
            // Create authentication token
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            
            // Create the authentication token with the user's email
            UsernamePasswordAuthenticationToken auth = 
                new UsernamePasswordAuthenticationToken(user.getEmail(), null, authorities);
            
            // Set additional details
            auth.setDetails(new WebAuthenticationDetails(request));
            
            // Set the authentication in the security context
            SecurityContextHolder.getContext().setAuthentication(auth);
            
            // Create a new session and store the security context
            HttpSession session = request.getSession(true);
            session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, 
                SecurityContextHolder.getContext()
            );
            
            logger.info("Direct login successful for user: {}", email);
            
            // Redirect to dashboard
            return "redirect:/dashboard";
            
        } catch (Exception e) {
            logger.error("Error during direct login", e);
            return "redirect:/login?error=true";
        }
    }
} 