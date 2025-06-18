package com.pcd.manager.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(WebConfig.class);

    @Value("${app.upload.dir:${user.home}/uploads}")
    private String uploadDir;
    
    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    /**
     * Configure multipart resolver for file uploads
     */
    @Bean
    public MultipartResolver multipartResolver() {
        StandardServletMultipartResolver resolver = new StandardServletMultipartResolver();
        logger.info("Configured StandardServletMultipartResolver for file uploads");
        return resolver;
    }
    
    /**
     * Add fallback controller mapping for paths that might be treated as resources
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Add any view controller mappings here if needed
        logger.info("Configured view controllers");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Configure standard static resources with more specific patterns
        registry.addResourceHandler("/css/**", "/js/**", "/images/**", "/fonts/**", "/webjars/**")
                .addResourceLocations("classpath:/static/css/", 
                                     "classpath:/static/js/", 
                                     "classpath:/static/images/", 
                                     "classpath:/static/fonts/", 
                                     "classpath:/META-INF/resources/webjars/")
                .setCachePeriod("dev".equals(activeProfile) ? 0 : 3600);
        
        // Ensure the upload directory exists
        try {
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
                logger.info("Created upload directory: {}", uploadPath);
            }
            
            // Make sure subdirectories exist for different uploads
            String[] subdirs = {"pictures", "documents", "rma-pictures", "rma-documents", "reference-documents"};
            for (String subdir : subdirs) {
                Path subdirPath = uploadPath.resolve(subdir);
                if (!Files.exists(subdirPath)) {
                    Files.createDirectories(subdirPath);
                    logger.info("Created upload subdirectory: {}", subdirPath);
                }
            }
            
            String uploadAbsolutePath = uploadPath.toFile().getAbsolutePath();
            
            // Ensure path ends with file separator
            if (!uploadAbsolutePath.endsWith(File.separator)) {
                uploadAbsolutePath += File.separator;
            }
            
            logger.info("Configuring resource handler for uploads at: {}", uploadAbsolutePath);
            
            // Set appropriate caching period based on profile
            int cachePeriod = "dev".equals(activeProfile) ? 0 : 3600;
            
            registry.addResourceHandler("/uploads/**")
                    .addResourceLocations("file:" + uploadAbsolutePath)
                    .setCachePeriod(cachePeriod);
            
            logger.info("Resource handlers configured with cache period: {} seconds", cachePeriod);
        } catch (Exception e) {
            logger.error("Error configuring upload directory resource handler", e);
        }
    }
} 