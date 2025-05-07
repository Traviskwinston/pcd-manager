package com.pcd.manager.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String redirectToLogin() {
        // Explicitly redirect any request to the root path to the login page
        return "redirect:/login";
    }
} 