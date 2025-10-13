package com.pcd.manager.controller;

import com.pcd.manager.model.CustomLocation;
import com.pcd.manager.model.Location;
import com.pcd.manager.model.MovingPart;
import com.pcd.manager.model.User;
import com.pcd.manager.service.CustomLocationService;
import com.pcd.manager.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
public class CustomLocationController {

    @Autowired
    private CustomLocationService customLocationService;

    @Autowired
    private UserService userService;

    /**
     * Show custom location detail page by ID
     */
    @GetMapping("/custom-locations/{id}")
    public String showCustomLocationDetailById(@PathVariable Long id,
                                          @AuthenticationPrincipal UserDetails userDetails,
                                          Model model) {
        return showCustomLocationDetail(id, userDetails, model);
    }

    /**
     * Show custom location detail page by name (pretty URL)
     */
    @GetMapping("/Storage/{name}")
    public String showCustomLocationDetailByName(@PathVariable String name,
                                                @AuthenticationPrincipal UserDetails userDetails,
                                                Model model) {
        User currentUser = userService.getUserByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Find or create custom location by name within the user's active site
        CustomLocation customLocation = customLocationService.findByNameAndLocation(name, currentUser.getActiveSite())
                .orElseGet(() -> {
                    // Create new custom location if it doesn't exist
                    return customLocationService.findOrCreateCustomLocation(name, currentUser.getActiveSite());
                });
        
        return showCustomLocationDetail(customLocation.getId(), userDetails, model);
    }

    /**
     * Internal method to show custom location detail page
     */
    private String showCustomLocationDetail(Long id,
                                          UserDetails userDetails,
                                          Model model) {
        User currentUser = userService.getUserByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        model.addAttribute("currentUser", currentUser);

        CustomLocation customLocation = customLocationService.getCustomLocationById(id)
                .orElseThrow(() -> new RuntimeException("Custom location not found"));

        model.addAttribute("customLocation", customLocation);

        // Get moving parts (incoming and outgoing)
        Map<String, List<MovingPart>> movingParts = customLocationService.getMovingPartsForCustomLocation(id);
        model.addAttribute("incomingMovingParts", movingParts.get("incoming"));
        model.addAttribute("outgoingMovingParts", movingParts.get("outgoing"));

        // Get current parts at this location
        List<Map<String, Object>> currentParts = customLocationService.getCurrentPartsAtLocation(id);
        model.addAttribute("currentParts", currentParts);

        return "custom-locations/details";
    }

    /**
     * Update custom location basic info (AJAX)
     */
    @PostMapping("/custom-locations/{id}/update-basic-info")
    @ResponseBody
    public Map<String, Object> updateBasicInfo(@PathVariable Long id,
                                               @RequestParam String name,
                                               @RequestParam String description) {
        try {
            CustomLocation customLocation = customLocationService.getCustomLocationById(id)
                    .orElseThrow(() -> new RuntimeException("Custom location not found"));

            String oldName = customLocation.getName();
            customLocation.setName(name);
            customLocation.setDescription(description);
            
            // Update the custom location AND all MovingParts that reference it by name
            customLocationService.updateCustomLocationAndReferences(id, customLocation, oldName);

            return Map.of("success", true, "message", "Custom location updated successfully");
        } catch (Exception e) {
            return Map.of("success", false, "message", "Error updating custom location: " + e.getMessage());
        }
    }

    /**
     * Delete a custom location (Admin only)
     */
    @PostMapping("/custom-locations/{id}/delete")
    public String deleteCustomLocation(@PathVariable Long id,
                                      @AuthenticationPrincipal UserDetails userDetails,
                                      RedirectAttributes redirectAttributes) {
        User currentUser = userService.getUserByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!currentUser.getRole().equals("Admin")) {
            redirectAttributes.addFlashAttribute("error", "Only admins can delete custom locations");
            return "redirect:/custom-locations/" + id;
        }

        try {
            customLocationService.deleteCustomLocation(id);
            redirectAttributes.addFlashAttribute("success", "Custom location deleted successfully");
            return "redirect:/tools"; // Redirect back to tools page
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error deleting custom location: " + e.getMessage());
            return "redirect:/custom-locations/" + id;
        }
    }
}

