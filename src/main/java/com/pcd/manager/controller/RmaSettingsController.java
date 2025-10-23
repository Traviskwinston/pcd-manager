package com.pcd.manager.controller;

import com.pcd.manager.model.ReturnAddress;
import com.pcd.manager.service.ReturnAddressService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for RMA settings (admin only)
 */
@Controller
@RequestMapping("/settings/rma")
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class RmaSettingsController {
    
    @Autowired
    private ReturnAddressService returnAddressService;
    
    /**
     * Show RMA settings page with list of return addresses
     */
    @GetMapping
    public String showRmaSettings(Model model) {
        List<ReturnAddress> addresses = returnAddressService.getAllReturnAddresses();
        model.addAttribute("addresses", addresses);
        return "rma/settings";
    }
    
    /**
     * Show form to create a new return address
     */
    @GetMapping("/address/new")
    public String showNewAddressForm(Model model) {
        model.addAttribute("address", new ReturnAddress());
        model.addAttribute("isEdit", false);
        return "rma/address-form";
    }
    
    /**
     * Show form to edit an existing return address
     */
    @GetMapping("/address/{id}/edit")
    public String showEditAddressForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        return returnAddressService.getReturnAddressById(id)
            .map(address -> {
                model.addAttribute("address", address);
                model.addAttribute("isEdit", true);
                return "rma/address-form";
            })
            .orElseGet(() -> {
                redirectAttributes.addFlashAttribute("errorMessage", "Address not found");
                return "redirect:/settings/rma";
            });
    }
    
    /**
     * Save a return address (create or update)
     */
    @PostMapping("/address/save")
    public String saveAddress(@ModelAttribute ReturnAddress address, RedirectAttributes redirectAttributes) {
        try {
            // Check for duplicate names
            if (returnAddressService.existsByName(address.getName(), address.getId())) {
                redirectAttributes.addFlashAttribute("errorMessage", "An address with this name already exists");
                return "redirect:/settings/rma/address/" + (address.getId() != null ? address.getId() + "/edit" : "new");
            }
            
            ReturnAddress saved = returnAddressService.saveReturnAddress(address);
            redirectAttributes.addFlashAttribute("successMessage", "Address saved successfully");
            log.info("Saved return address: {} (ID: {})", saved.getName(), saved.getId());
        } catch (Exception e) {
            log.error("Error saving return address", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error saving address: " + e.getMessage());
        }
        return "redirect:/settings/rma";
    }
    
    /**
     * Delete a return address
     */
    @PostMapping("/address/{id}/delete")
    public String deleteAddress(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            returnAddressService.deleteReturnAddress(id);
            redirectAttributes.addFlashAttribute("successMessage", "Address deleted successfully");
            log.info("Deleted return address ID: {}", id);
        } catch (Exception e) {
            log.error("Error deleting return address", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting address: " + e.getMessage());
        }
        return "redirect:/settings/rma";
    }
    
    /**
     * API endpoint to get all return addresses (for dropdowns)
     */
    @GetMapping("/api/addresses")
    @ResponseBody
    public List<ReturnAddress> getAddresses() {
        return returnAddressService.getAllReturnAddresses();
    }
}

