package com.pcd.manager.util;

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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

/**
 * Utility for cleaning up orphaned file records at application startup
 */
@Component
public class EntityCleanupUtil {

    private static final Logger logger = LoggerFactory.getLogger(EntityCleanupUtil.class);
    
    @PersistenceContext
    private EntityManager entityManager;
    
    private final RmaDocumentRepository documentRepository;
    private final RmaPictureRepository pictureRepository;
    
    @Value("${app.cleanup.orphaned-files:true}")
    private boolean cleanupOrphanedFiles;
    
    @Autowired
    public EntityCleanupUtil(
            RmaDocumentRepository documentRepository,
            RmaPictureRepository pictureRepository) {
        this.documentRepository = documentRepository;
        this.pictureRepository = pictureRepository;
    }
    
    /**
     * Runs cleanup operations when the application starts
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void cleanupOrphanedEntities() {
        if (!cleanupOrphanedFiles) {
            logger.info("Orphaned file cleanup is disabled. Set app.cleanup.orphaned-files=true to enable.");
            return;
        }
        
        logger.info("Starting orphaned entity cleanup");
        
        // Clean up orphaned document entities
        cleanupOrphanedDocuments();
        
        // Clean up orphaned picture entities
        cleanupOrphanedPictures();
        
        // Flush changes
        entityManager.flush();
        
        logger.info("Orphaned entity cleanup completed");
    }
    
    /**
     * Cleans up orphaned document entities
     */
    @Transactional
    public void cleanupOrphanedDocuments() {
        // Query for documents with null RMA reference
        List<Long> orphanedDocIds = entityManager.createQuery(
                "SELECT d.id FROM RmaDocument d WHERE d.rma IS NULL", Long.class)
                .getResultList();
        
        if (!orphanedDocIds.isEmpty()) {
            logger.info("Found {} orphaned document entities to clean up", orphanedDocIds.size());
            
            for (Long id : orphanedDocIds) {
                try {
                    documentRepository.deleteById(id);
                    logger.info("Deleted orphaned document with ID: {}", id);
                } catch (Exception e) {
                    logger.error("Error deleting orphaned document with ID {}: {}", id, e.getMessage(), e);
                }
            }
            
            documentRepository.flush();
        } else {
            logger.info("No orphaned document entities found");
        }
    }
    
    /**
     * Cleans up orphaned picture entities
     */
    @Transactional
    public void cleanupOrphanedPictures() {
        // Query for pictures with null RMA reference
        List<Long> orphanedPicIds = entityManager.createQuery(
                "SELECT p.id FROM RmaPicture p WHERE p.rma IS NULL", Long.class)
                .getResultList();
        
        if (!orphanedPicIds.isEmpty()) {
            logger.info("Found {} orphaned picture entities to clean up", orphanedPicIds.size());
            
            for (Long id : orphanedPicIds) {
                try {
                    pictureRepository.deleteById(id);
                    logger.info("Deleted orphaned picture with ID: {}", id);
                } catch (Exception e) {
                    logger.error("Error deleting orphaned picture with ID {}: {}", id, e.getMessage(), e);
                }
            }
            
            pictureRepository.flush();
        } else {
            logger.info("No orphaned picture entities found");
        }
    }
} 