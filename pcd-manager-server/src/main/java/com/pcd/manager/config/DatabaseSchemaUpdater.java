package com.pcd.manager.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class DatabaseSchemaUpdater implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseSchemaUpdater.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            logger.info("Updating passdown comment field to support larger text...");
            
            // Try to modify the column to support larger text
            jdbcTemplate.execute("ALTER TABLE passdowns ALTER COLUMN comment VARCHAR(10000)");
            
            logger.info("Successfully updated passdown comment field size");
        } catch (Exception e) {
            logger.error("Error updating passdown comment field: {}", e.getMessage());
            logger.info("This may be caused by database already in use. The application will continue without schema updates.");
            // Don't propagate the error, allow the application to continue
        }
    }
} 