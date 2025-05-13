package com.pcd.manager.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class DatabaseSchemaUpdater implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseSchemaUpdater.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private Environment env;

    @Override
    public void run(String... args) {
        try {
            logger.info("Checking database type for schema update...");
            String dbUrl = env.getProperty("spring.datasource.url", "");
            boolean isH2Database = dbUrl.contains("h2");
            
            logger.info("Updating passdown comment field to support larger text...");
            
            // SQL syntax depends on database type
            String alterSql;
            if (isH2Database) {
                alterSql = "ALTER TABLE passdowns ALTER COLUMN comment VARCHAR(10000)";
            } else {
                alterSql = "ALTER TABLE passdowns ALTER COLUMN comment TYPE VARCHAR(10000)";
            }
            
            jdbcTemplate.execute(alterSql);
            
            logger.info("Successfully updated passdown comment field size");
        } catch (Exception e) {
            logger.error("Error updating passdown comment field: {}", e.getMessage());
            logger.info("This may be caused by database already in use. The application will continue without schema updates.");
            // Don't propagate the error, allow the application to continue
        }
    }
} 