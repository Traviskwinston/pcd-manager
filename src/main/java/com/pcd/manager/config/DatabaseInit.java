package com.pcd.manager.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseInit {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseInit.class);
    
    private final JdbcTemplate jdbcTemplate;
    private final Environment env;
    private boolean isH2Database;
    
    @Autowired
    public DatabaseInit(JdbcTemplate jdbcTemplate, Environment env) {
        this.jdbcTemplate = jdbcTemplate;
        this.env = env;
    }
    
    @PostConstruct
    public void init() {
        try {
            // Determine database type
            String dbUrl = env.getProperty("spring.datasource.url", "");
            isH2Database = dbUrl.contains("h2");
            logger.info("Database type detected: {}", isH2Database ? "H2" : "PostgreSQL");
            
            logger.info("Running database schema update for Tool status enum");
            
            // First check if the status column exists
            boolean statusColumnExists = columnExists("tools", "status");
            
            if (!statusColumnExists) {
                logger.info("Status column does not exist yet, skipping migration");
                return;
            }
            
            // Check if there are any tools with invalid status values
            Integer invalidStatusCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tools WHERE status NOT IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED')",
                Integer.class
            );
            
            if (invalidStatusCount == null || invalidStatusCount == 0) {
                logger.info("No tools with invalid status values found, skipping status migration");
                return;
            }
            
            logger.info("Found {} tools with invalid status values, migrating...", invalidStatusCount);
            
            // Make the status column nullable temporarily
            String alterNullableSQL = isH2Database 
                ? "ALTER TABLE tools ALTER COLUMN status NULL"
                : "ALTER TABLE tools ALTER COLUMN status DROP NOT NULL";
            jdbcTemplate.execute(alterNullableSQL);
            
            // Update the status for tools with old status values
            try {
                // First try with a safer approach - check if specific values exist
                checkAndUpdateStatus("IN_USE", "IN_PROGRESS");
                checkAndUpdateStatus("MAINTENANCE", "IN_PROGRESS");
                checkAndUpdateStatus("DAMAGED", "COMPLETED");
                checkAndUpdateStatus("LOST", "COMPLETED");
                checkAndUpdateStatus("AVAILABLE", "NOT_STARTED");
            } catch (Exception e) {
                logger.warn("Error updating specific status values: {}", e.getMessage());
                
                // Fallback: update any remaining invalid statuses
                try {
                    jdbcTemplate.execute(
                        "UPDATE tools SET status = 'NOT_STARTED' WHERE status NOT IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED')"
                    );
                    logger.info("Updated remaining invalid status values to NOT_STARTED");
                } catch (Exception ex) {
                    logger.error("Error updating remaining invalid status values", ex);
                }
            }
            
            // Make the column required again with the default value
            String alterRequiredSQL = isH2Database
                ? "ALTER TABLE tools ALTER COLUMN status VARCHAR(255) NOT NULL DEFAULT 'NOT_STARTED'"
                : "ALTER TABLE tools ALTER COLUMN status SET NOT NULL; ALTER TABLE tools ALTER COLUMN status SET DEFAULT 'NOT_STARTED'";
            jdbcTemplate.execute(alterRequiredSQL);
            
            logger.info("Tool status enum update completed successfully");
            
            // Update Tool Type enum
            logger.info("Running database schema update for Tool type enum");
            
            try {
                // Check if the tool_type column exists
                boolean toolTypeColumnExists = columnExists("tools", "tool_type");
                
                if (toolTypeColumnExists) {
                    // Drop existing tool_type column constraints
                    String alterTypeNullableSQL = isH2Database
                        ? "ALTER TABLE tools ALTER COLUMN tool_type NULL"
                        : "ALTER TABLE tools ALTER COLUMN tool_type DROP NOT NULL";
                    jdbcTemplate.execute(alterTypeNullableSQL);
                    
                    // Update any invalid tool types to a valid value
                    jdbcTemplate.execute(
                        "UPDATE tools SET tool_type = 'SLURRY' WHERE tool_type NOT IN ('CHEMBLEND', 'SLURRY')"
                    );
                    
                    logger.info("Tool type enum update completed successfully");
                } else {
                    logger.info("Tool type column does not exist yet, skipping migration");
                }
            } catch (Exception e) {
                logger.error("Error updating tool_type enum: {}", e.getMessage(), e);
            }
        } catch (Exception e) {
            logger.error("Error updating database schema: {}", e.getMessage());
            logger.error("This may be caused by database already in use. The application will continue without schema updates.");
            // Don't rethrow, allow the application to continue
        }
    }
    
    private void checkAndUpdateStatus(String oldStatus, String newStatus) {
        // Check if any tools have the old status
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tools WHERE status = ?",
            Integer.class,
            oldStatus
        );
        
        if (count != null && count > 0) {
            // Update the status for tools with this old status
            jdbcTemplate.update(
                "UPDATE tools SET status = ? WHERE status = ?",
                newStatus,
                oldStatus
            );
            logger.info("Updated {} tools with status '{}' to '{}'", count, oldStatus, newStatus);
        }
    }
    
    private boolean columnExists(String tableName, String columnName) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class,
                tableName.toUpperCase(),
                columnName.toUpperCase()
            );
            
            return count != null && count > 0;
        } catch (DataAccessException e) {
            logger.warn("Error checking if column exists: {}", e.getMessage());
            return false;
        }
    }
} 