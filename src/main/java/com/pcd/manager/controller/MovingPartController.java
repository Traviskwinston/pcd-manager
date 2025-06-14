package com.pcd.manager.controller;

import com.pcd.manager.model.MovingPart;
import com.pcd.manager.model.Tool;
import com.pcd.manager.model.User;
import com.pcd.manager.service.MovingPartService;
import com.pcd.manager.service.ToolService;
import com.pcd.manager.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

@Controller
public class MovingPartController {

    @Autowired
    private MovingPartService movingPartService;

    @Autowired
    private ToolService toolService;

    @Autowired
    private UserService userService;

    @PostMapping("/tools/{id}/moving-parts/add")
    public String addMovingPart(@PathVariable("id") Long toolId,
                             @RequestParam("partName") String partName,
                             @RequestParam("fromToolId") Long fromToolId,
                             @RequestParam("destinationToolIds") List<Long> destinationToolIds,
                             @RequestParam(value = "notes", required = false) String notes,
                             @RequestParam(value = "noteId", required = false) Long noteId,
                             Principal principal,
                             RedirectAttributes redirectAttributes) {
        
        try {
            if (destinationToolIds == null || destinationToolIds.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "At least one destination tool must be selected");
                return "redirect:/tools/" + toolId;
            }
            
            movingPartService.createMovingPart(partName, fromToolId, destinationToolIds, notes, noteId, null);
            redirectAttributes.addFlashAttribute("successMessage", "Moving part recorded successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error recording moving part: " + e.getMessage());
        }
        
        return "redirect:/tools/" + toolId;
    }
    
    @PostMapping("/tools/{id}/moving-parts/{movingPartId}/link-note")
    public String linkNoteToMovingPart(@PathVariable("id") Long toolId,
                                       @PathVariable("movingPartId") Long movingPartId,
                                       @RequestParam("noteId") Long noteId,
                                       RedirectAttributes redirectAttributes) {
        
        Optional<MovingPart> result = movingPartService.linkNoteToMovingPart(movingPartId, noteId);
        
        if (result.isPresent()) {
            redirectAttributes.addFlashAttribute("successMessage", "Note linked to moving part successfully");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Could not link note to moving part");
        }
        
        return "redirect:/tools/" + toolId;
    }
    
    @PostMapping("/tools/{id}/moving-parts/{movingPartId}/delete")
    public String deleteMovingPart(@PathVariable("id") Long toolId,
                                   @PathVariable("movingPartId") Long movingPartId,
                                   RedirectAttributes redirectAttributes) {
        
        try {
            movingPartService.deleteMovingPart(movingPartId);
            redirectAttributes.addFlashAttribute("successMessage", "Moving part record deleted successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting moving part: " + e.getMessage());
        }
        
        return "redirect:/tools/" + toolId;
    }

    @PostMapping("/tools/{id}/moving-parts/{movingPartId}/edit")
    public String editMovingPart(@PathVariable("id") Long toolId,
                                @PathVariable("movingPartId") Long movingPartId,
                                @RequestParam("partName") String partName,
                                @RequestParam("fromToolId") Long fromToolId,
                                @RequestParam("destinationToolIds") List<Long> destinationToolIds,
                                @RequestParam(value = "notes", required = false) String notes,
                                RedirectAttributes redirectAttributes) {
        
        try {
            if (destinationToolIds == null || destinationToolIds.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "At least one destination tool must be selected");
                return "redirect:/tools/" + toolId;
            }
            
            Optional<MovingPart> result = movingPartService.updateMovingPart(movingPartId, partName, fromToolId, destinationToolIds, notes, null);
            
            if (result.isPresent()) {
                redirectAttributes.addFlashAttribute("successMessage", "Moving part updated successfully");
                } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Moving part not found");
            }
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating moving part: " + e.getMessage());
        }
        
        return "redirect:/tools/" + toolId;
    }

    @PostMapping("/tools/{id}/moving-parts/{movingPartId}/add-destination")
    public String addDestinationToMovingPart(@PathVariable("id") Long toolId,
                                           @PathVariable("movingPartId") Long movingPartId,
                                           @RequestParam("newDestinationToolId") Long newDestinationToolId,
                                           RedirectAttributes redirectAttributes) {
        
        try {
            Optional<MovingPart> result = movingPartService.addDestinationToMovingPart(movingPartId, newDestinationToolId);
            
            if (result.isPresent()) {
                redirectAttributes.addFlashAttribute("successMessage", "New destination added to moving part successfully");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Moving part not found");
            }
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error adding destination: " + e.getMessage());
        }
        
        return "redirect:/tools/" + toolId;
    }
} 