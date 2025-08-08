/**
 * Dark Mode Theme Toggle for PCD Manager
 * Handles theme switching between light and dark modes with persistence
 */

(function() {
    'use strict';

    // Theme management object
    const ThemeManager = {
        // Constants
        STORAGE_KEY: 'pcd-manager-theme',
        LIGHT_THEME: 'light',
        DARK_THEME: 'dark',
        
        // DOM elements
        toggleButton: null,
        sunIcon: null,
        moonIcon: null,
        
        // Initialize the theme manager
        init: function() {
            this.createToggleButton();
            this.loadSavedTheme();
            this.attachEventListeners();
        },
        
        // Create the theme toggle button and add it to the navigation
        createToggleButton: function() {
            // Avoid duplicates if already created
            const existing = document.querySelector('.navbar .container .theme-toggle');
            if (existing) {
                this.toggleButton = existing;
                this.sunIcon = existing.querySelector('.bi-sun-fill');
                this.moonIcon = existing.querySelector('.bi-moon-fill');
                return;
            }

            // Find navbar container robustly
            let navbar = document.querySelector('.navbar .container');
            if (!navbar) {
                const nav = document.querySelector('nav.navbar');
                navbar = nav || document.body;
            }
            if (!navbar) return;
            
            // Prefer inserting before profile dropdown; otherwise append to end
            const profileDropdown = navbar.querySelector('.dropdown');
            
            // Create the toggle button
            this.toggleButton = document.createElement('button');
            this.toggleButton.className = 'btn theme-toggle me-2';
            this.toggleButton.setAttribute('type', 'button');
            this.toggleButton.setAttribute('aria-label', 'Toggle dark mode');
            this.toggleButton.setAttribute('title', 'Toggle dark mode');
            
            // Create icons
            this.sunIcon = document.createElement('i');
            this.sunIcon.className = 'bi bi-sun-fill';
            this.sunIcon.style.display = 'none';
            
            this.moonIcon = document.createElement('i');
            this.moonIcon.className = 'bi bi-moon-fill';
            
            // Add icons to button
            this.toggleButton.appendChild(this.sunIcon);
            this.toggleButton.appendChild(this.moonIcon);
            
            // Insert button before profile dropdown if available; else append
            if (profileDropdown && profileDropdown.parentNode) {
                profileDropdown.parentNode.insertBefore(this.toggleButton, profileDropdown);
            } else {
                navbar.appendChild(this.toggleButton);
            }
        },
        
        // Load the saved theme from localStorage
        loadSavedTheme: function() {
            const savedTheme = localStorage.getItem(this.STORAGE_KEY);
            const preferredTheme = savedTheme || this.getPreferredTheme();
            
            this.setTheme(preferredTheme);
        },
        
        // Get the user's preferred theme based on system preference
        getPreferredTheme: function() {
            if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
                return this.DARK_THEME;
            }
            return this.LIGHT_THEME;
        },
        
        // Set the theme
        setTheme: function(theme) {
            document.documentElement.setAttribute('data-bs-theme', theme);
            this.updateToggleButton(theme);
            this.saveTheme(theme);
        },
        
        // Update the toggle button appearance
        updateToggleButton: function(theme) {
            if (!this.toggleButton || !this.sunIcon || !this.moonIcon) return;
            
            if (theme === this.DARK_THEME) {
                this.sunIcon.style.display = 'inline-block';
                this.moonIcon.style.display = 'none';
                this.toggleButton.setAttribute('title', 'Switch to light mode');
                this.toggleButton.setAttribute('aria-label', 'Switch to light mode');
            } else {
                this.sunIcon.style.display = 'none';
                this.moonIcon.style.display = 'inline-block';
                this.toggleButton.setAttribute('title', 'Switch to dark mode');
                this.toggleButton.setAttribute('aria-label', 'Switch to dark mode');
            }
        },
        
        // Save theme to localStorage
        saveTheme: function(theme) {
            localStorage.setItem(this.STORAGE_KEY, theme);
        },
        
        // Toggle the theme
        toggleTheme: function() {
            const currentTheme = document.documentElement.getAttribute('data-bs-theme');
            const newTheme = currentTheme === this.DARK_THEME ? this.LIGHT_THEME : this.DARK_THEME;
            this.setTheme(newTheme);
        },
        
        // Attach event listeners
        attachEventListeners: function() {
            // Toggle button click
            if (this.toggleButton) {
                this.toggleButton.addEventListener('click', () => {
                    this.toggleTheme();
                });
            }
            
            // Listen for system theme changes
            if (window.matchMedia) {
                const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
                mediaQuery.addEventListener('change', (e) => {
                    // Only update if user hasn't manually set a preference
                    const savedTheme = localStorage.getItem(this.STORAGE_KEY);
                    if (!savedTheme) {
                        const newTheme = e.matches ? this.DARK_THEME : this.LIGHT_THEME;
                        this.setTheme(newTheme);
                    }
                });
            }
            
            // Keyboard shortcut (Ctrl/Cmd + Shift + D)
            document.addEventListener('keydown', (e) => {
                if ((e.ctrlKey || e.metaKey) && e.shiftKey && e.key === 'D') {
                    e.preventDefault();
                    this.toggleTheme();
                }
            });
        }
    };
    
    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            ThemeManager.init();
        });
    } else {
        ThemeManager.init();
    }
    
    // Expose theme manager globally for debugging
    window.ThemeManager = ThemeManager;
    
})();

// Additional utility functions for theme-aware components
window.PcdThemeUtils = {
    // Check if dark mode is active
    isDarkMode: function() {
        return document.documentElement.getAttribute('data-bs-theme') === 'dark';
    },
    
    // Get current theme
    getCurrentTheme: function() {
        return document.documentElement.getAttribute('data-bs-theme') || 'light';
    },
    
    // Add theme change listener
    onThemeChange: function(callback) {
        if (typeof callback !== 'function') return;
        
        const observer = new MutationObserver((mutations) => {
            mutations.forEach((mutation) => {
                if (mutation.type === 'attributes' && mutation.attributeName === 'data-bs-theme') {
                    const newTheme = document.documentElement.getAttribute('data-bs-theme');
                    callback(newTheme, mutation.oldValue);
                }
            });
        });
        
        observer.observe(document.documentElement, {
            attributes: true,
            attributeFilter: ['data-bs-theme'],
            attributeOldValue: true
        });
        
        return observer;
    }
}; 