package com.pcd.manager.util;

import com.pcd.manager.model.RmaDocument;
import com.pcd.manager.model.RmaPicture;
import com.pcd.manager.repository.RmaDocumentRepository;
import com.pcd.manager.repository.RmaPictureRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.List;

/**
 * Utility for migrating absolute file paths to relative paths in the database
 */
@Component
public class PathMigrationUtil {

    private static final Logger logger = LoggerFactory.getLogger(PathMigrationUtil.class);
    
    private final RmaDocumentRepository documentRepository;
    private final RmaPictureRepository pictureRepository;
    
    @Value("${app.upload.dir:${user.home}/uploads}")
    private String uploadDir;
    
    @Value("${app.migration.path-fix:false}")
    private boolean runPathFix;
    
    @Autowired
    public PathMigrationUtil(
            RmaDocumentRepository documentRepository,
            RmaPictureRepository pictureRepository) {
        this.documentRepository = documentRepository;
        this.pictureRepository = pictureRepository;
    }
    
    /**
     * Runs path migration when the application starts
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrateAbsolutePathsToRelative() {
        if (!runPathFix) {
            logger.info("Path migration is disabled. Set app.migration.path-fix=true to enable.");
            return;
        }
        
        logger.info("Starting file path migration");
        migrateDocumentPaths();
        migratePicturePaths();
        logger.info("File path migration completed");
    }
    
    /**
     * Migrates document paths from absolute to relative
     */
    @Transactional
    public void migrateDocumentPaths() {
        List<RmaDocument> documents = documentRepository.findAll();
        int fixedCount = 0;
        
        logger.info("Found {} documents to check", documents.size());
        
        for (RmaDocument document : documents) {
            String path = document.getFilePath();
            if (path != null && path.contains(":/")) {
                String relativePath = convertToRelativePath(path);
                
                if (relativePath != null) {
                    document.setFilePath(relativePath);
                    documentRepository.save(document);
                    fixedCount++;
                    logger.info("Converted document path: {} -> {}", path, relativePath);
                }
            }
        }
        
        logger.info("Fixed {} document paths", fixedCount);
    }
    
    /**
     * Migrates picture paths from absolute to relative
     */
    @Transactional
    public void migratePicturePaths() {
        List<RmaPicture> pictures = pictureRepository.findAll();
        int fixedCount = 0;
        
        logger.info("Found {} pictures to check", pictures.size());
        
        for (RmaPicture picture : pictures) {
            String path = picture.getFilePath();
            if (path != null && path.contains(":/")) {
                String relativePath = convertToRelativePath(path);
                
                if (relativePath != null) {
                    picture.setFilePath(relativePath);
                    pictureRepository.save(picture);
                    fixedCount++;
                    logger.info("Converted picture path: {} -> {}", path, relativePath);
                }
            }
        }
        
        logger.info("Fixed {} picture paths", fixedCount);
    }
    
    /**
     * Converts an absolute path to a relative path
     * 
     * @param absolutePath The absolute path to convert
     * @return The relative path, or null if conversion failed
     */
    private String convertToRelativePath(String absolutePath) {
        if (absolutePath == null) {
            return null;
        }
        
        // Find "uploads/" in the path
        int uploadsIndex = absolutePath.lastIndexOf("uploads" + File.separator);
        if (uploadsIndex == -1) {
            // Try with forward slash
            uploadsIndex = absolutePath.lastIndexOf("uploads/");
            if (uploadsIndex == -1) {
                logger.warn("Could not find 'uploads/' in path: {}", absolutePath);
                return null;
            }
        }
        
        // Extract the path after "uploads/"
        String relativePath = absolutePath.substring(uploadsIndex + "uploads".length() + 1);
        
        // Normalize separators to forward slashes for consistent storage
        relativePath = relativePath.replace('\\', '/');
        
        return relativePath;
    }
} 