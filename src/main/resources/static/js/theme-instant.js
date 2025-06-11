/**
 * Instant Theme Application Script
 * Must be loaded synchronously in <head> to prevent FOUC (Flash of Unstyled Content)
 * This script immediately applies the saved theme before any content renders
 */
(function() {
    'use strict';
    
    const STORAGE_KEY = 'pcd-manager-theme';
    const savedTheme = localStorage.getItem(STORAGE_KEY);
    const prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
    const theme = savedTheme || (prefersDark ? 'dark' : 'light');
    
    // Apply theme immediately to prevent flash
    document.documentElement.setAttribute('data-bs-theme', theme);
})(); 