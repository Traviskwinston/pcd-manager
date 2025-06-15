package com.pcd.manager.service;

import com.pcd.manager.model.Rma;
import com.pcd.manager.model.RmaDocument;
import com.pcd.manager.model.RmaPicture;
import com.pcd.manager.repository.RmaDocumentRepository;
import com.pcd.manager.repository.RmaPictureRepository;
import com.pcd.manager.repository.RmaRepository;
import com.pcd.manager.util.UploadUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Service for handling file transfers between RMAs
 * Core synchronous operations with async wrapper available via AsyncFileTransferService
 */
@Service
public class FileTransferService {

    private static final Logger logger = LoggerFactory.getLogger(FileTransferService.class);
    
    private final RmaRepository rmaRepository;
    private final RmaDocumentRepository rmaDocumentRepository;
    private final RmaPictureRepository rmaPictureRepository;
    private final UploadUtils uploadUtils;
    
    @Autowired
    public FileTransferService(
            RmaRepository rmaRepository,
            RmaDocumentRepository rmaDocumentRepository,
            RmaPictureRepository rmaPictureRepository,
            UploadUtils uploadUtils) {
        this.rmaRepository = rmaRepository;
        this.rmaDocumentRepository = rmaDocumentRepository;
        this.rmaPictureRepository = rmaPictureRepository;
        this.uploadUtils = uploadUtils;
    }
    
    /**
     * Transfer multiple files between RMAs (SYNCHRONOUS VERSION)
     * For async operations, use AsyncFileTransferService.transferMultipleFilesAsync()
     * 
     * @param fileIds List of file IDs to transfer
     * @param fileTypes List of file types (document/picture)
     * @param sourceRmaId Source RMA ID
     * @param targetRmaIds List of target RMA IDs
     * @return Map containing success/failure information
     */
    @Transactional
    public Map<String, Object> transferMultipleFiles(
            List<Long> fileIds, 
            List<String> fileTypes,
            Long sourceRmaId,
            List<Long> targetRmaIds) {
        
        logger.info("Starting SYNC batch transfer of {} files from RMA ID: {}", 
                fileIds.size(), sourceRmaId);
        long startTime = System.currentTimeMillis();
        
        Map<String, Object> result = transferMultipleFilesSync(fileIds, fileTypes, sourceRmaId, targetRmaIds);
        
        long duration = System.currentTimeMillis() - startTime;
        logger.info("Completed SYNC batch transfer in {}ms", duration);
        
        return result;
    }
    
    /**
     * Synchronous batch transfer implementation
     */
    private Map<String, Object> transferMultipleFilesSync(
            List<Long> fileIds, 
            List<String> fileTypes,
            Long sourceRmaId,
            List<Long> targetRmaIds) {
        
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> transferResults = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;
        
        logger.info("Starting SYNC batch transfer of {} files from RMA ID: {}", 
                fileIds.size(), sourceRmaId);
        
        // Create a Set to track already processed files to prevent duplicates
        Set<String> processedFiles = new HashSet<>();
        
        for (int i = 0; i < fileIds.size(); i++) {
            Long fileId = fileIds.get(i);
            String fileType = fileTypes.get(i);
            Long targetRmaId = targetRmaIds.get(i);
            
            // Create a unique identifier for this transfer to prevent duplicates
            String transferKey = fileType + "_" + fileId + "_" + targetRmaId;
            
            Map<String, Object> transferResult = new HashMap<>();
            transferResult.put("fileId", fileId);
            transferResult.put("fileType", fileType);
            transferResult.put("sourceRmaId", sourceRmaId);
            transferResult.put("targetRmaId", targetRmaId);
            
            // Skip if we've already processed this exact transfer
            if (processedFiles.contains(transferKey)) {
                logger.warn("Skipping duplicate transfer request: {}", transferKey);
                transferResult.put("success", false);
                transferResult.put("message", "Duplicate transfer request skipped");
                transferResults.add(transferResult);
                failureCount++;
                continue;
            }
            
            try {
                // Add to processed set before attempting transfer
                processedFiles.add(transferKey);
                
                boolean success = false;
                
                // Process one type at a time with clear persistence context between operations
                if ("document".equalsIgnoreCase(fileType)) {
                    success = transferDocument(fileId, targetRmaId);
                    // Force flush to ensure changes are committed
                    rmaDocumentRepository.flush();
                    rmaRepository.flush();
                } else if ("picture".equalsIgnoreCase(fileType)) {
                    success = transferPicture(fileId, targetRmaId);
                    // Force flush to ensure changes are committed
                    rmaPictureRepository.flush();
                    rmaRepository.flush();
                }
                
                transferResult.put("success", success);
                if (success) {
                    successCount++;
                    transferResult.put("message", "File transferred successfully");
                } else {
                    failureCount++;
                    transferResult.put("message", "File transfer failed - see logs for details");
                }
            } catch (Exception e) {
                logger.error("Error transferring file ID: {} of type: {} to RMA ID: {}: {}", 
                        fileId, fileType, targetRmaId, e.getMessage(), e);
                transferResult.put("success", false);
                transferResult.put("message", "Error: " + e.getMessage());
                failureCount++;
            }
            
            transferResults.add(transferResult);
        }
        
        result.put("totalFiles", fileIds.size());
        result.put("successCount", successCount);
        result.put("failureCount", failureCount);
        result.put("transfers", transferResults);
        
        logger.info("SYNC batch transfer completed. Success: {}, Failures: {}", 
                successCount, failureCount);
        
        return result;
    }
    
    /**
     * Transfer a document to another RMA
     * 
     * @param documentId The document ID
     * @param targetRmaId The target RMA ID
     * @return true if successful, false otherwise
     */
    @Transactional
    public boolean transferDocument(Long documentId, Long targetRmaId) {
        logger.info("Transferring document with ID {} to RMA {}", documentId, targetRmaId);
        
        // Find document
        Optional<RmaDocument> documentOpt = rmaDocumentRepository.findById(documentId);
        if (documentOpt.isEmpty()) {
            logger.warn("Document with ID {} not found", documentId);
            return false;
        }
        
        RmaDocument document = documentOpt.get();
        
        // Find target RMA
        Optional<Rma> targetRmaOpt = rmaRepository.findById(targetRmaId);
        if (targetRmaOpt.isEmpty()) {
            logger.warn("Target RMA with ID {} not found", targetRmaId);
            return false;
        }
        
        Rma targetRma = targetRmaOpt.get();
        Rma sourceRma = document.getRma();
        
        if (sourceRma == null) {
            logger.warn("Document {} is not associated with any RMA", documentId);
            return false;
        }
        
        if (sourceRma.getId().equals(targetRma.getId())) {
            logger.warn("Source and target RMAs are the same: {}", targetRmaId);
            return false;
        }
        
        // Store important information to create a copy
        String fileName = document.getFileName();
        String filePath = document.getFilePath();
        String fileType = document.getFileType();
        Long fileSize = document.getFileSize();
        Long originalDocId = document.getId();
        
        try {
            // Check if document is already in target RMA to prevent duplicates
            boolean alreadyInTarget = false;
            if (targetRma.getDocuments() != null) {
                alreadyInTarget = targetRma.getDocuments().stream()
                    .anyMatch(d -> (d.getFilePath() != null && d.getFilePath().equals(filePath)));
            }
            
            if (alreadyInTarget) {
                logger.warn("Document with same file path is already in target RMA {}", targetRmaId);
                return false;
            }
            
            // Log the state before modification
            logger.info("Before transfer - Source RMA {} has {} documents", 
                sourceRma.getId(), sourceRma.getDocuments().size());
            logger.info("Before transfer - Target RMA {} has {} documents", 
                targetRma.getId(), targetRma.getDocuments().size());
            
            // 1. Remove from source RMA's collection first
            sourceRma.getDocuments().removeIf(d -> d.getId().equals(originalDocId));
            rmaRepository.saveAndFlush(sourceRma);
            logger.info("Removed document from source RMA - now has {} documents", 
                sourceRma.getDocuments().size());
                
            // 2. Delete the original document entity to avoid Hibernate issues
            rmaDocumentRepository.deleteById(originalDocId);
            rmaDocumentRepository.flush();
            logger.info("Deleted original document from database");
            
            // 3. Create a completely new document entity for the target RMA
            RmaDocument newDocument = new RmaDocument();
            newDocument.setFileName(fileName);
            newDocument.setFilePath(filePath);
            newDocument.setFileType(fileType);
            newDocument.setFileSize(fileSize);
            newDocument.setRma(targetRma);
            
            // 4. Save the new document
            RmaDocument savedDocument = rmaDocumentRepository.saveAndFlush(newDocument);
            logger.info("Created new document entry with ID {} in target RMA {}", 
                    savedDocument.getId(), targetRmaId);
            
            // 5. Add to target RMA's collection
            if (targetRma.getDocuments() == null) {
                targetRma.setDocuments(new ArrayList<>());
            }
            targetRma.getDocuments().add(savedDocument);
            rmaRepository.saveAndFlush(targetRma);
            logger.info("Added document to target RMA - now has {} documents", 
                targetRma.getDocuments().size());
            
            // 6. Clear persistence context to avoid issues
            rmaDocumentRepository.flush();
            rmaRepository.flush();
            
            logger.info("Successfully transferred document to RMA {}", targetRmaId);
            return true;
        } catch (Exception e) {
            logger.error("Error transferring document with ID {} to RMA {}: {}", 
                    documentId, targetRmaId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Transfer a picture to another RMA
     * 
     * @param pictureId The picture ID
     * @param targetRmaId The target RMA ID
     * @return true if successful, false otherwise
     */
    @Transactional
    public boolean transferPicture(Long pictureId, Long targetRmaId) {
        logger.info("Transferring picture with ID {} to RMA {}", pictureId, targetRmaId);
        
        // Find picture
        Optional<RmaPicture> pictureOpt = rmaPictureRepository.findById(pictureId);
        if (pictureOpt.isEmpty()) {
            logger.warn("Picture with ID {} not found", pictureId);
            return false;
        }
        
        RmaPicture picture = pictureOpt.get();
        
        // Find target RMA
        Optional<Rma> targetRmaOpt = rmaRepository.findById(targetRmaId);
        if (targetRmaOpt.isEmpty()) {
            logger.warn("Target RMA with ID {} not found", targetRmaId);
            return false;
        }
        
        Rma targetRma = targetRmaOpt.get();
        Rma sourceRma = picture.getRma();
        
        if (sourceRma == null) {
            logger.warn("Picture {} is not associated with any RMA", pictureId);
            return false;
        }
        
        if (sourceRma.getId().equals(targetRma.getId())) {
            logger.warn("Source and target RMAs are the same: {}", targetRmaId);
            return false;
        }
        
        // Store important information to create a copy
        String fileName = picture.getFileName();
        String filePath = picture.getFilePath();
        String fileType = picture.getFileType();
        Long fileSize = picture.getFileSize();
        Long originalPicId = picture.getId();
        
        try {
            // Check if picture is already in target RMA to prevent duplicates
            boolean alreadyInTarget = false;
            if (targetRma.getPictures() != null) {
                alreadyInTarget = targetRma.getPictures().stream()
                    .anyMatch(p -> (p.getFilePath() != null && p.getFilePath().equals(filePath)));
            }
            
            if (alreadyInTarget) {
                logger.warn("Picture with same file path is already in target RMA {}", targetRmaId);
                return false;
            }
            
            // Log the state before modification
            logger.info("Before transfer - Source RMA {} has {} pictures", 
                sourceRma.getId(), sourceRma.getPictures().size());
            logger.info("Before transfer - Target RMA {} has {} pictures", 
                targetRma.getId(), targetRma.getPictures().size());
            
            // 1. Remove from source RMA's collection first
            sourceRma.getPictures().removeIf(p -> p.getId().equals(originalPicId));
            rmaRepository.saveAndFlush(sourceRma);
            logger.info("Removed picture from source RMA - now has {} pictures", 
                sourceRma.getPictures().size());
                
            // 2. Delete the original picture entity to avoid Hibernate issues
            rmaPictureRepository.deleteById(originalPicId);
            rmaPictureRepository.flush();
            logger.info("Deleted original picture from database");
            
            // 3. Create a completely new picture entity for the target RMA
            RmaPicture newPicture = new RmaPicture();
            newPicture.setFileName(fileName);
            newPicture.setFilePath(filePath);
            newPicture.setFileType(fileType);
            newPicture.setFileSize(fileSize);
            newPicture.setRma(targetRma);
            
            // 4. Save the new picture
            RmaPicture savedPicture = rmaPictureRepository.saveAndFlush(newPicture);
            logger.info("Created new picture entry with ID {} in target RMA {}", 
                    savedPicture.getId(), targetRmaId);
            
            // 5. Add to target RMA's collection
            if (targetRma.getPictures() == null) {
                targetRma.setPictures(new ArrayList<>());
            }
            targetRma.getPictures().add(savedPicture);
            rmaRepository.saveAndFlush(targetRma);
            logger.info("Added picture to target RMA - now has {} pictures", 
                targetRma.getPictures().size());
            
            // 6. Clear persistence context to avoid issues
            rmaPictureRepository.flush();
            rmaRepository.flush();
            
            logger.info("Successfully transferred picture to RMA {}", targetRmaId);
            return true;
        } catch (Exception e) {
            logger.error("Error transferring picture with ID {} to RMA {}: {}", 
                    pictureId, targetRmaId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Verify all file transfers were successful
     * 
     * @param fileIds List of file IDs
     * @param fileTypes List of file types
     * @param targetRmaIds List of target RMA IDs
     * @return Map with verification results
     */
    @Transactional(readOnly = true)
    public Map<String, Object> verifyTransfers(
            List<Long> fileIds, 
            List<String> fileTypes,
            List<Long> targetRmaIds) {
        
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> verifications = new ArrayList<>();
        int verifiedCount = 0;
        
        for (int i = 0; i < fileIds.size(); i++) {
            Long fileId = fileIds.get(i);
            String fileType = fileTypes.get(i);
            Long targetRmaId = targetRmaIds.get(i);
            
            Map<String, Object> verification = new HashMap<>();
            verification.put("fileId", fileId);
            verification.put("fileType", fileType);
            verification.put("targetRmaId", targetRmaId);
            
            boolean verified = false;
            
            // Get target RMA
            Optional<Rma> targetRmaOpt = rmaRepository.findById(targetRmaId);
            if (targetRmaOpt.isPresent()) {
                Rma targetRma = targetRmaOpt.get();
                
                if ("document".equalsIgnoreCase(fileType)) {
                    // Find the original document to get its file path
                    Optional<RmaDocument> origDocOpt = rmaDocumentRepository.findById(fileId);
                    if (origDocOpt.isPresent()) {
                        String origFilePath = origDocOpt.get().getFilePath();
                        
                        // Check if a document with the same file path exists in target RMA
                        if (targetRma.getDocuments() != null) {
                            verified = targetRma.getDocuments().stream()
                                .anyMatch(d -> d.getFilePath() != null && 
                                        d.getFilePath().equals(origFilePath));
                        }
                    }
                } else if ("picture".equalsIgnoreCase(fileType)) {
                    // Find the original picture to get its file path
                    Optional<RmaPicture> origPicOpt = rmaPictureRepository.findById(fileId);
                    if (origPicOpt.isPresent()) {
                        String origFilePath = origPicOpt.get().getFilePath();
                        
                        // Check if a picture with the same file path exists in target RMA
                        if (targetRma.getPictures() != null) {
                            verified = targetRma.getPictures().stream()
                                .anyMatch(p -> p.getFilePath() != null && 
                                        p.getFilePath().equals(origFilePath));
                        }
                    }
                }
            }
            
            verification.put("verified", verified);
            if (verified) {
                verifiedCount++;
            }
            
            verifications.add(verification);
        }
        
        result.put("totalFiles", fileIds.size());
        result.put("verifiedCount", verifiedCount);
        result.put("verifications", verifications);
        
        return result;
    }
} 