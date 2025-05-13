
import com.pcd.manager.model.Location;
import com.pcd.manager.model.User;
import com.pcd.manager.repository.LocationRepository;
import com.pcd.manager.repository.ToolRepository;
import com.pcd.manager.service.LocationService;
import com.pcd.manager.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequestMapping("/profile")
public class ProfileController {

    private final UserService userService;
    private final LocationRepository locationRepository;
    private final ToolRepository toolRepository;
    private final LocationService locationService;

    @Autowired
    public ProfileController(UserService userService, LocationRepository locationRepository, 
                             ToolRepository toolRepository, LocationService locationService) {
        this.userService = userService;
        this.locationRepository = locationRepository;
        this.toolRepository = toolRepository;
        this.locationService = locationService;
    }

    @GetMapping
    public String viewProfile(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        
        User currentUser = userService.getUserByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Get default location if not set
        if (currentUser.getDefaultLocation() == null && currentUser.getActiveSite() == null) {
            Optional<Location> defaultLocation = locationService.getDefaultLocation();
            defaultLocation.ifPresent(location -> {
                currentUser.setDefaultLocation(location);
                currentUser.setActiveSite(location);
            });
        }
        
        // Ensure data is properly loaded and synced
        currentUser.syncNameFields();
        
        // Make sure assigned tools are initialized
        if (currentUser.getAssignedTools() == null || currentUser.getAssignedTools().isEmpty()) {
            userService.loadUserTools(currentUser);
        }
        
        // Add default location info for the template
        Optional<Location> defaultLocation = locationService.getDefaultLocation();
        model.addAttribute("defaultLocationExists", defaultLocation.isPresent());
        defaultLocation.ifPresent(location -> model.addAttribute("defaultLocation", location));
        
        model.addAttribute("user", currentUser);
        model.addAttribute("activeTab", "profile");
        return "users/profile";
    }
    
    @GetMapping("/edit")
    public String showEditProfileForm(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        
        User currentUser = userService.getUserByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Get default location if not set
        if (currentUser.getDefaultLocation() == null && currentUser.getActiveSite() == null) {
            Optional<Location> defaultLocation = locationService.getDefaultLocation();
            defaultLocation.ifPresent(location -> {
                currentUser.setDefaultLocation(location);
                currentUser.setActiveSite(location);
            });
        }
        
        // Ensure data is properly loaded and synced
        currentUser.syncNameFields();
        
        // Make sure assigned tools are initialized
        if (currentUser.getAssignedTools() == null || currentUser.getAssignedTools().isEmpty()) {
            userService.loadUserTools(currentUser);
        }
        
        // Add default location info for the template
        Optional<Location> defaultLocation = locationService.getDefaultLocation();
        model.addAttribute("defaultLocationExists", defaultLocation.isPresent());
        defaultLocation.ifPresent(location -> model.addAttribute("defaultLocation", location));
        
        model.addAttribute("user", currentUser);
        model.addAttribute("locations", locationRepository.findAll());
        model.addAttribute("tools", toolRepository.findAll());
        model.addAttribute("activeTab", "profile");
        return "users/profileEdit";
    }
    
    @PostMapping("/update")
    public String updateProfile(@ModelAttribute User user, RedirectAttributes redirectAttributes) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            
            User currentUser = userService.getUserByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Preserve the role from the current user
            user.setRole(currentUser.getRole());
            
            // Load assigned tools from current user to prevent losing them
            user.setAssignedTools(currentUser.getAssignedTools());
            
            // Update name based on first name and last name
            if (user.getFirstName() != null || user.getLastName() != null) {
                StringBuilder fullName = new StringBuilder();
                if (user.getFirstName() != null) {
                    fullName.append(user.getFirstName());
                }
                if (user.getLastName() != null && !user.getLastName().isEmpty()) {
                    if (fullName.length() > 0) {
                        fullName.append(" ");
                    }
                    fullName.append(user.getLastName());
                }
                user.setName(fullName.toString());
            }
            
            // Sync defaultLocation to activeSite
            if (user.getDefaultLocation() != null) {
                user.setActiveSite(user.getDefaultLocation());
            } else if (user.getActiveSite() != null) {
                user.setDefaultLocation(user.getActiveSite());
            }
            
            userService.updateUser(user);
            redirectAttributes.addFlashAttribute("message", "Profile updated successfully");
            return "redirect:/profile";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating profile: " + e.getMessage());
            return "redirect:/profile/edit";
        }
    }
} 