package com.pcd.manager.service;

import com.pcd.manager.model.Rma;
import com.pcd.manager.model.RmaDocument;
import com.pcd.manager.model.RmaPicture;
import com.pcd.manager.repository.RmaRepository;
import com.pcd.manager.repository.RmaPictureRepository;
import com.pcd.manager.repository.RmaDocumentRepository;
import com.pcd.manager.util.UploadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Arrays;

@Service
public class RmaService {

    private static final Logger logger = LoggerFactory.getLogger(RmaService.class);
    private final RmaRepository rmaRepository;
    private final RmaPictureRepository rmaPictureRepository;
    private final RmaDocumentRepository rmaDocumentRepository;
    private final UploadUtils uploadUtils;

    private static final List<String> IMAGE_TYPES = Arrays.asList(
        "image/jpeg", "image/png", "image/gif", "image/bmp", "image/webp"
    );

    @Autowired
    public RmaService(RmaRepository rmaRepository, 
                     RmaPictureRepository rmaPictureRepository,
                     RmaDocumentRepository rmaDocumentRepository,
                     UploadUtils uploadUtils) {
        this.rmaRepository = rmaRepository;
        this.rmaPictureRepository = rmaPictureRepository;
        this.rmaDocumentRepository = rmaDocumentRepository;
        this.uploadUtils = uploadUtils;
    }

    public List<Rma> getAllRmas() {
        return rmaRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Rma> getRmaById(Long id) {
        Optional<Rma> rmaOpt = rmaRepository.findById(id);
        rmaOpt.ifPresent(rma -> {
            if (rma.getDocuments() != null) rma.getDocuments().size();
            if (rma.getPictures() != null) rma.getPictures().size();
        });
        return rmaOpt;
    }

    @Transactional
    public Rma saveRma(Rma rmaData, MultipartFile[] fileUploads) throws IOException {
        Rma rmaToSave;

        if (rmaData.getId() != null) {
            logger.info("Updating RMA ID: {}", rmaData.getId());
            rmaToSave = getRmaById(rmaData.getId())
                .orElseThrow(() -> new RuntimeException("RMA not found for update: " + rmaData.getId()));
            
            rmaToSave.setRmaNumber(rmaData.getRmaNumber());
            rmaToSave.setCustomerName(rmaData.getCustomerName());
            rmaToSave.setStatus(rmaData.getStatus());
            rmaToSave.setPriority(rmaData.getPriority());
            rmaToSave.setLocation(rmaData.getLocation());
            rmaToSave.setTechnician(rmaData.getTechnician());
            rmaToSave.setDescription(rmaData.getDescription());
            rmaToSave.setComments(rmaData.getComments());
            rmaToSave.setTool(rmaData.getTool());
            rmaToSave.setSerialNumber(rmaData.getSerialNumber());
            rmaToSave.setSalesOrder(rmaData.getSalesOrder());
            rmaToSave.setWrittenDate(rmaData.getWrittenDate());
            rmaToSave.setRmaNumberProvidedDate(rmaData.getRmaNumberProvidedDate());
            rmaToSave.setShippingMemoEmailedDate(rmaData.getShippingMemoEmailedDate());
            rmaToSave.setPartsReceivedDate(rmaData.getPartsReceivedDate());
            rmaToSave.setInstalledPartsDate(rmaData.getInstalledPartsDate());
            rmaToSave.setFailedPartsPackedDate(rmaData.getFailedPartsPackedDate());
            rmaToSave.setFailedPartsShippedDate(rmaData.getFailedPartsShippedDate());
            rmaToSave.setCustomerContact(rmaData.getCustomerContact());
            rmaToSave.setCustomerEmail(rmaData.getCustomerEmail());
            rmaToSave.setCustomerPhone(rmaData.getCustomerPhone());
            rmaToSave.setRootCause(rmaData.getRootCause());
            rmaToSave.setResolution(rmaData.getResolution());
            rmaToSave.setNotes(rmaData.getNotes());

            if (rmaToSave.getPartLineItems() == null) {
                rmaToSave.setPartLineItems(new ArrayList<>());
            } else {
                rmaToSave.getPartLineItems().clear();
            }
            if (rmaData.getPartLineItems() != null) {
                rmaToSave.getPartLineItems().addAll(rmaData.getPartLineItems());
            }
            
            if (rmaToSave.getDocuments() == null) rmaToSave.setDocuments(new ArrayList<>());
            if (rmaToSave.getPictures() == null) rmaToSave.setPictures(new ArrayList<>());

        } else {
            logger.info("Creating new RMA");
            rmaToSave = rmaData;
            if (rmaToSave.getPartLineItems() == null) rmaToSave.setPartLineItems(new ArrayList<>());
            if (rmaToSave.getDocuments() == null) rmaToSave.setDocuments(new ArrayList<>());
            if (rmaToSave.getPictures() == null) rmaToSave.setPictures(new ArrayList<>());
        }

        // Save the RMA first to ensure it has an ID before adding files
        // This helps prevent orphaned file records
        Rma savedRma = rmaRepository.saveAndFlush(rmaToSave);
        logger.info("Initial save of RMA complete, ID: {}", savedRma.getId());
        
        // Process file uploads after initial save
        if (fileUploads != null && fileUploads.length > 0) {
            logger.info("Processing {} file uploads for RMA ID: {}", fileUploads.length, savedRma.getId());
            
            int successCount = 0;
            int errorCount = 0;
            
            // Separate lists for documents and pictures to add
            List<RmaDocument> documentsToAdd = new ArrayList<>();
            List<RmaPicture> picturesToAdd = new ArrayList<>();
            
            // Ensure upload directories exist
            uploadUtils.ensureUploadDirectoryExists();
            
            for (MultipartFile file : fileUploads) {
                if (file != null && !file.isEmpty()) {
                    String contentType = file.getContentType();
                    String originalFilename = file.getOriginalFilename();
                    long fileSize = file.getSize();
                    
                    boolean isImage = contentType != null && IMAGE_TYPES.stream().anyMatch(contentType::equalsIgnoreCase);
                    String subdirectory = isImage ? "rma-pictures" : "rma-documents";
                    
                    logger.info("Processing file: {}, Size: {} bytes, Type: {}, IsImage: {}", 
                               originalFilename, fileSize, contentType, isImage);
                    
                    try {
                        // Save the file to disk
                        String savedPath = uploadUtils.saveFile(file, subdirectory);
                        
                        if (savedPath != null) {
                            // Create file entity
                            if (isImage) {
                                RmaPicture picture = new RmaPicture();
                                picture.setFileName(originalFilename);
                                picture.setFilePath(savedPath);
                                picture.setFileType(contentType);
                                picture.setFileSize(fileSize);
                                picture.setRma(savedRma);
                                
                                picturesToAdd.add(picture);
                                logger.info("Picture prepared for RMA: {} -> {}", originalFilename, savedPath);
                                successCount++;
                            } else {
                                RmaDocument document = new RmaDocument();
                                document.setFileName(originalFilename);
                                document.setFilePath(savedPath);
                                document.setFileType(contentType);
                                document.setFileSize(fileSize);
                                document.setRma(savedRma);
                                
                                documentsToAdd.add(document);
                                logger.info("Document prepared for RMA: {} -> {}", originalFilename, savedPath);
                                successCount++;
                            }
                            
                            // Verify file exists on disk
                            boolean fileExists = uploadUtils.fileExists(savedPath);
                            logger.info("File exists on disk after save: {} - {}", savedPath, fileExists);
                        } else {
                            logger.warn("Failed to save file: {} - null path returned", originalFilename);
                            errorCount++;
                        }
                    } catch (IOException e) {
                        logger.error("Error saving file {}: {}", originalFilename, e.getMessage(), e);
                        errorCount++;
                    }
                } else {
                    logger.warn("Skipping null or empty file in upload array");
                }
            }
            
            logger.info("File upload processing complete. Prepared: {}, Errors: {}", successCount, errorCount);
            
            // Save all documents and pictures in batch mode
            if (!documentsToAdd.isEmpty()) {
                rmaDocumentRepository.saveAllAndFlush(documentsToAdd);
                savedRma.getDocuments().addAll(documentsToAdd);
                logger.info("Added {} documents to RMA {}", documentsToAdd.size(), savedRma.getId());
            }
            
            if (!picturesToAdd.isEmpty()) {
                rmaPictureRepository.saveAllAndFlush(picturesToAdd);
                savedRma.getPictures().addAll(picturesToAdd);
                logger.info("Added {} pictures to RMA {}", picturesToAdd.size(), savedRma.getId());
            }
            
            // Save RMA again only if files were added successfully
            if (successCount > 0) {
                savedRma = rmaRepository.saveAndFlush(savedRma);
                logger.info("RMA saved again after adding {} files", successCount);
                
                // Verify file counts
                int pictureCount = savedRma.getPictures() != null ? savedRma.getPictures().size() : 0;
                int documentCount = savedRma.getDocuments() != null ? savedRma.getDocuments().size() : 0;
                logger.info("RMA now has {} pictures and {} documents", pictureCount, documentCount);
            }
        } else {
            logger.info("No files to process for RMA ID: {}", savedRma.getId());
        }

        logger.info("Successfully saved RMA ID: {} (complete)", savedRma.getId());
        return savedRma;
    }

    @Transactional
    public void deleteRma(Long id) {
        logger.info("Attempting to delete RMA ID: {}", id);
        Rma rma = getRmaById(id).orElseThrow(() -> new RuntimeException("RMA not found: " + id));

        if (rma.getDocuments() != null) {
            for (RmaDocument doc : rma.getDocuments()) {
                try {
                    if (doc.getFilePath() != null) {
                        boolean deleted = uploadUtils.deleteFile(doc.getFilePath());
                        logger.info("Deleted document file: {} - Result: {}", doc.getFilePath(), deleted);
                    }
                } catch (Exception e) {
                    logger.error("Error deleting document file {}: {}", doc.getFilePath(), e.getMessage());
                }
            }
        }
        if (rma.getPictures() != null) {
            for (RmaPicture pic : rma.getPictures()) {
                try {
                     if (pic.getFilePath() != null) {
                        boolean deleted = uploadUtils.deleteFile(pic.getFilePath());
                        logger.info("Deleted picture file: {} - Result: {}", pic.getFilePath(), deleted);
                     }
                } catch (Exception e) {
                    logger.error("Error deleting picture file {}: {}", pic.getFilePath(), e.getMessage());
                }
            }
        }
        
        rmaRepository.deleteById(id);
        logger.info("Successfully deleted RMA ID: {}", id);
    }

    /**
     * Get the most recent RMAs, limited by count
     * 
     * @param count the maximum number of RMAs to return
     * @return list of the most recent RMAs
     */
    public List<Rma> getRecentRmas(int count) {
        PageRequest pageRequest = PageRequest.of(0, count, Sort.by(Sort.Direction.DESC, "createdDate"));
        return rmaRepository.findAll(pageRequest).getContent();
    }
    
    /**
     * Delete a file (document or picture) from an RMA
     * 
     * @param fileId The ID of the file to delete
     * @param fileType The type of file (document or picture)
     * @return true if successfully deleted, false otherwise
     */
    @Transactional
    public boolean deleteFile(Long fileId, String fileType) {
        logger.info("Attempting to delete {} with ID: {}", fileType, fileId);
        
        try {
            if ("picture".equalsIgnoreCase(fileType)) {
                // Find the picture entity
                Optional<RmaPicture> pictureOpt = findPictureById(fileId);
                
                if (pictureOpt.isEmpty()) {
                    logger.warn("Picture with ID {} not found", fileId);
                    return false;
                }
                
                RmaPicture picture = pictureOpt.get();
                
                // Delete the file from disk
                String filePath = picture.getFilePath();
                if (filePath != null) {
                    boolean fileDeleted = uploadUtils.deleteFile(filePath);
                    logger.info("Deleted physical file: {} - Result: {}", filePath, fileDeleted);
                }
                
                // Get the RMA to update the pictures collection
                Rma rma = picture.getRma();
                if (rma != null) {
                    rma.getPictures().remove(picture);
                    // Save the RMA to persist the removal
                    rmaRepository.save(rma);
                }
                
                // Delete the picture entity
                rmaPictureRepository.delete(picture);
                
                return true;
                
            } else if ("document".equalsIgnoreCase(fileType)) {
                // Find the document entity
                Optional<RmaDocument> documentOpt = findDocumentById(fileId);
                
                if (documentOpt.isEmpty()) {
                    logger.warn("Document with ID {} not found", fileId);
                    return false;
                }
                
                RmaDocument document = documentOpt.get();
                
                // Delete the file from disk
                String filePath = document.getFilePath();
                if (filePath != null) {
                    boolean fileDeleted = uploadUtils.deleteFile(filePath);
                    logger.info("Deleted physical file: {} - Result: {}", filePath, fileDeleted);
                }
                
                // Get the RMA to update the documents collection
                Rma rma = document.getRma();
                if (rma != null) {
                    rma.getDocuments().remove(document);
                    // Save the RMA to persist the removal
                    rmaRepository.save(rma);
                }
                
                // Delete the document entity
                rmaDocumentRepository.delete(document);
                
                return true;
            }
            
            logger.warn("Invalid file type: {}", fileType);
            return false;
            
        } catch (Exception e) {
            logger.error("Error deleting file: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Transfer a file from one RMA to another
     * 
     * @param fileId The ID of the file to transfer
     * @param fileType The type of file (document or picture)
     * @param sourceRmaId The source RMA ID
     * @param targetRmaId The target RMA ID
     * @return true if successfully transferred, false otherwise
     */
    @Transactional
    public boolean transferFile(Long fileId, String fileType, Long sourceRmaId, Long targetRmaId) {
        logger.info("Attempting to transfer {} with ID: {} from RMA {} to RMA {}", 
                   fileType, fileId, sourceRmaId, targetRmaId);
        
        try {
            // Get the target RMA
            Rma targetRma = rmaRepository.findById(targetRmaId).orElse(null);
            if (targetRma == null) {
                logger.warn("Target RMA with ID {} not found", targetRmaId);
                return false;
            }
            
            if ("picture".equalsIgnoreCase(fileType)) {
                // Find the picture entity
                Optional<RmaPicture> pictureOpt = findPictureById(fileId);
                
                if (pictureOpt.isEmpty()) {
                    logger.warn("Picture with ID {} not found", fileId);
                    return false;
                }
                
                RmaPicture picture = pictureOpt.get();
                
                // Get the source RMA
                Rma sourceRma = picture.getRma();
                if (sourceRma == null || !sourceRma.getId().equals(sourceRmaId)) {
                    logger.warn("Picture {} does not belong to source RMA {}", fileId, sourceRmaId);
                    return false;
                }
                
                // Transfer using the dedicated method
                return transferPicture(fileId, targetRmaId);
                
            } else if ("document".equalsIgnoreCase(fileType)) {
                // Find the document entity
                Optional<RmaDocument> documentOpt = findDocumentById(fileId);
                
                if (documentOpt.isEmpty()) {
                    logger.warn("Document with ID {} not found", fileId);
                    return false;
                }
                
                RmaDocument document = documentOpt.get();
                
                // Get the source RMA
                Rma sourceRma = document.getRma();
                if (sourceRma == null || !sourceRma.getId().equals(sourceRmaId)) {
                    logger.warn("Document {} does not belong to source RMA {}", fileId, sourceRmaId);
                    return false;
                }
                
                // Transfer using the dedicated method
                return transferDocument(fileId, targetRmaId);
            }
            
            logger.warn("Invalid file type: {}", fileType);
            return false;
            
        } catch (Exception e) {
            logger.error("Error transferring file: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Find a picture by ID
     * 
     * @param id The picture ID
     * @return The RmaPicture or null if not found
     */
    public Optional<RmaPicture> findPictureById(Long id) {
        logger.info("Finding picture with ID: {}", id);
        return rmaPictureRepository.findById(id);
    }

    /**
     * Find a document by ID
     * 
     * @param id The document ID
     * @return The RmaDocument or null if not found
     */
    public Optional<RmaDocument> findDocumentById(Long id) {
        logger.info("Finding document with ID: {}", id);
        return rmaDocumentRepository.findById(id);
    }

    /**
     * Delete a picture by ID
     */
    @Transactional
    public boolean deletePicture(Long id) {
        logger.info("Deleting picture with ID: {}", id);
        
        Optional<RmaPicture> pictureOpt = findPictureById(id);
        if (pictureOpt.isEmpty()) {
            logger.warn("Picture with ID {} not found", id);
            return false;
        }
        
        RmaPicture picture = pictureOpt.get();
        Rma rma = picture.getRma();
        if (rma == null) {
            logger.warn("Picture with ID {} has no associated RMA", id);
            return false;
        }
        
        // First, get the file path while the entity still exists
        String filePath = picture.getFilePath();
        
        // Remove picture from RMA collection
        rma.getPictures().remove(picture);
        rmaRepository.save(rma);
        
        // Delete the picture entity from the database
        try {
            picture.setRma(null);  // Detach from RMA
            rmaPictureRepository.delete(picture);
            rmaPictureRepository.flush(); // Force immediate deletion
            logger.info("Successfully removed picture entity with ID {}", id);
        } catch (Exception e) {
            logger.error("Error deleting picture entity with ID {}: {}", id, e.getMessage(), e);
        }
        
        // Finally, try to delete the physical file
        if (filePath != null && !filePath.isEmpty()) {
            try {
                boolean deleted = uploadUtils.deleteFile(filePath);
                logger.info("Physical file deletion result for {}: {}", filePath, deleted);
            } catch (Exception e) {
                logger.error("Error deleting physical file {}: {}", filePath, e.getMessage(), e);
            }
        }
        
        logger.info("Successfully completed picture deletion process for ID {}", id);
        return true;
    }

    /**
     * Delete a document by ID
     */
    @Transactional
    public boolean deleteDocument(Long id) {
        logger.info("Deleting document with ID: {}", id);
        
        Optional<RmaDocument> documentOpt = findDocumentById(id);
        if (documentOpt.isEmpty()) {
            logger.warn("Document with ID {} not found", id);
            return false;
        }
        
        RmaDocument document = documentOpt.get();
        Rma rma = document.getRma();
        if (rma == null) {
            logger.warn("Document with ID {} has no associated RMA", id);
            return false;
        }
        
        // First, get the file path while the entity still exists
        String filePath = document.getFilePath();
        
        // Remove document from RMA collection
        rma.getDocuments().remove(document);
        rmaRepository.save(rma);
        
        // Delete the document entity from the database
        try {
            document.setRma(null);  // Detach from RMA
            rmaDocumentRepository.delete(document);
            rmaDocumentRepository.flush(); // Force immediate deletion
            logger.info("Successfully removed document entity with ID {}", id);
        } catch (Exception e) {
            logger.error("Error deleting document entity with ID {}: {}", id, e.getMessage(), e);
        }
        
        // Finally, try to delete the physical file
        if (filePath != null && !filePath.isEmpty()) {
            try {
                boolean deleted = uploadUtils.deleteFile(filePath);
                logger.info("Physical file deletion result for {}: {}", filePath, deleted);
            } catch (Exception e) {
                logger.error("Error deleting physical file {}: {}", filePath, e.getMessage(), e);
            }
        }
        
        logger.info("Successfully completed document deletion process for ID {}", id);
        return true;
    }

    /**
     * Transfer a picture to another RMA
     */
    @Transactional
    public boolean transferPicture(Long pictureId, Long targetRmaId) {
        logger.info("Transferring picture with ID {} to RMA {}", pictureId, targetRmaId);
        
        // Find picture
        Optional<RmaPicture> pictureOpt = findPictureById(pictureId);
        if (pictureOpt.isEmpty()) {
            logger.warn("Picture with ID {} not found", pictureId);
            return false;
        }
        
        RmaPicture picture = pictureOpt.get();
        
        // Find target RMA
        Optional<Rma> targetRmaOpt = getRmaById(targetRmaId);
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
        
        try {
            // Check if picture is already in target RMA to prevent duplicates
            boolean alreadyInTarget = false;
            if (targetRma.getPictures() != null) {
                alreadyInTarget = targetRma.getPictures().stream()
                    .anyMatch(p -> p.getId().equals(pictureId) || 
                              (p.getFilePath() != null && p.getFilePath().equals(picture.getFilePath())));
            }
            
            if (alreadyInTarget) {
                logger.warn("Picture with ID {} or same file path is already in target RMA {}", 
                           pictureId, targetRmaId);
                return false;
            }
            
            // Make a log of the current state
            logger.info("Before transfer: Picture belongs to RMA ID {} and has {} pictures", 
                    sourceRma.getId(), sourceRma.getPictures().size());
            logger.info("Target RMA ID {} has {} pictures", 
                    targetRma.getId(), targetRma.getPictures().size());
                    
            // Remove from source RMA
            sourceRma.getPictures().remove(picture);
            rmaRepository.saveAndFlush(sourceRma);
            logger.info("Removed picture from source RMA - now has {} pictures", 
                    sourceRma.getPictures().size());
            
            // Set new RMA and save the picture directly
            picture.setRma(targetRma);
            rmaPictureRepository.saveAndFlush(picture);
            logger.info("Updated picture RMA reference to target RMA ID {}", targetRma.getId());
            
            // Ensure the target RMA has the picture in its collection
            if (targetRma.getPictures() == null) {
                targetRma.setPictures(new ArrayList<>());
            }
            
            // Add to target RMA's collection if not already there
            boolean alreadyAdded = targetRma.getPictures().stream()
                .anyMatch(p -> p.getId().equals(pictureId));
                
            if (!alreadyAdded) {
                targetRma.getPictures().add(picture);
                rmaRepository.saveAndFlush(targetRma);
                logger.info("Added picture to target RMA collection - now has {} pictures", 
                        targetRma.getPictures().size());
            } else {
                logger.info("Picture already in target RMA collection");
            }
            
            // Final verification - reload both RMAs to confirm changes
            Optional<Rma> sourceRmaReloaded = getRmaById(sourceRma.getId());
            Optional<Rma> targetRmaReloaded = getRmaById(targetRma.getId());
            
            if (sourceRmaReloaded.isPresent() && targetRmaReloaded.isPresent()) {
                // Check picture counts
                int sourceCount = sourceRmaReloaded.get().getPictures().size();
                int targetCount = targetRmaReloaded.get().getPictures().size();
                
                // Check if picture is in target RMA
                boolean inTarget = targetRmaReloaded.get().getPictures().stream()
                    .anyMatch(p -> p.getId().equals(pictureId));
                    
                // Check if picture is still in source RMA (it shouldn't be)
                boolean stillInSource = sourceRmaReloaded.get().getPictures().stream()
                    .anyMatch(p -> p.getId().equals(pictureId));
                    
                logger.info("Transfer verification - Source RMA pictures: {}, Target RMA pictures: {}", 
                        sourceCount, targetCount);
                logger.info("Picture in target: {}, Still in source: {}", inTarget, stillInSource);
                
                if (!inTarget || stillInSource) {
                    logger.warn("Transfer may not have completed correctly - attempting fixes");
                    
                    // If the picture is still in the source, remove it again
                    if (stillInSource) {
                        Rma src = sourceRmaReloaded.get();
                        src.getPictures().removeIf(p -> p.getId().equals(pictureId));
                        rmaRepository.saveAndFlush(src);
                        logger.info("Removed picture from source RMA (second attempt)");
                    }
                    
                    // If the picture is not in the target, add it again
                    if (!inTarget) {
                        Rma tgt = targetRmaReloaded.get();
                        // Reload the picture to ensure we have the latest state
                        Optional<RmaPicture> picReloaded = findPictureById(pictureId);
                        if (picReloaded.isPresent()) {
                            RmaPicture pic = picReloaded.get();
                            pic.setRma(tgt);
                            rmaPictureRepository.saveAndFlush(pic);
                            
                            if (tgt.getPictures() == null) {
                                tgt.setPictures(new ArrayList<>());
                            }
                            tgt.getPictures().add(pic);
                            rmaRepository.saveAndFlush(tgt);
                            logger.info("Added picture to target RMA (second attempt)");
                        }
                    }
                }
            }
            
            logger.info("Successfully transferred picture with ID {} to RMA {}", pictureId, targetRmaId);
            return true;
        } catch (Exception e) {
            logger.error("Error transferring picture with ID {} to RMA {}: {}", pictureId, targetRmaId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Transfer a document to another RMA
     */
    @Transactional
    public boolean transferDocument(Long documentId, Long targetRmaId) {
        logger.info("Transferring document with ID {} to RMA {}", documentId, targetRmaId);
        
        // Find document
        Optional<RmaDocument> documentOpt = findDocumentById(documentId);
        if (documentOpt.isEmpty()) {
            logger.warn("Document with ID {} not found", documentId);
            return false;
        }
        
        RmaDocument document = documentOpt.get();
        
        // Find target RMA
        Optional<Rma> targetRmaOpt = getRmaById(targetRmaId);
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
        
        try {
            // Check if document is already in target RMA to prevent duplicates
            boolean alreadyInTarget = false;
            if (targetRma.getDocuments() != null) {
                alreadyInTarget = targetRma.getDocuments().stream()
                    .anyMatch(d -> d.getId().equals(documentId) || 
                              (d.getFilePath() != null && d.getFilePath().equals(document.getFilePath())));
            }
            
            if (alreadyInTarget) {
                logger.warn("Document with ID {} or same file path is already in target RMA {}", 
                           documentId, targetRmaId);
                return false;
            }
            
            // Make a log of the current state
            logger.info("Before transfer: Document belongs to RMA ID {} and has {} documents", 
                    sourceRma.getId(), sourceRma.getDocuments().size());
            logger.info("Target RMA ID {} has {} documents", 
                    targetRma.getId(), targetRma.getDocuments().size());
                    
            // Remove from source RMA
            sourceRma.getDocuments().remove(document);
            rmaRepository.saveAndFlush(sourceRma);
            logger.info("Removed document from source RMA - now has {} documents", 
                    sourceRma.getDocuments().size());
            
            // Set new RMA and save the document directly
            document.setRma(targetRma);
            rmaDocumentRepository.saveAndFlush(document);
            logger.info("Updated document RMA reference to target RMA ID {}", targetRma.getId());
            
            // Ensure the target RMA has the document in its collection
            if (targetRma.getDocuments() == null) {
                targetRma.setDocuments(new ArrayList<>());
            }
            
            // Add to target RMA's collection if not already there
            boolean alreadyAdded = targetRma.getDocuments().stream()
                .anyMatch(d -> d.getId().equals(documentId));
                
            if (!alreadyAdded) {
                targetRma.getDocuments().add(document);
                rmaRepository.saveAndFlush(targetRma);
                logger.info("Added document to target RMA collection - now has {} documents", 
                        targetRma.getDocuments().size());
            } else {
                logger.info("Document already in target RMA collection");
            }
            
            // Final verification - reload both RMAs to confirm changes
            Optional<Rma> sourceRmaReloaded = getRmaById(sourceRma.getId());
            Optional<Rma> targetRmaReloaded = getRmaById(targetRma.getId());
            
            if (sourceRmaReloaded.isPresent() && targetRmaReloaded.isPresent()) {
                // Check document counts
                int sourceCount = sourceRmaReloaded.get().getDocuments().size();
                int targetCount = targetRmaReloaded.get().getDocuments().size();
                
                // Check if document is in target RMA
                boolean inTarget = targetRmaReloaded.get().getDocuments().stream()
                    .anyMatch(d -> d.getId().equals(documentId));
                    
                // Check if document is still in source RMA (it shouldn't be)
                boolean stillInSource = sourceRmaReloaded.get().getDocuments().stream()
                    .anyMatch(d -> d.getId().equals(documentId));
                    
                logger.info("Transfer verification - Source RMA documents: {}, Target RMA documents: {}", 
                        sourceCount, targetCount);
                logger.info("Document in target: {}, Still in source: {}", inTarget, stillInSource);
                
                if (!inTarget || stillInSource) {
                    logger.warn("Transfer may not have completed correctly - attempting fixes");
                    
                    // If the document is still in the source, remove it again
                    if (stillInSource) {
                        Rma src = sourceRmaReloaded.get();
                        src.getDocuments().removeIf(d -> d.getId().equals(documentId));
                        rmaRepository.saveAndFlush(src);
                        logger.info("Removed document from source RMA (second attempt)");
                    }
                    
                    // If the document is not in the target, add it again
                    if (!inTarget) {
                        Rma tgt = targetRmaReloaded.get();
                        // Reload the document to ensure we have the latest state
                        Optional<RmaDocument> docReloaded = findDocumentById(documentId);
                        if (docReloaded.isPresent()) {
                            RmaDocument doc = docReloaded.get();
                            doc.setRma(tgt);
                            rmaDocumentRepository.saveAndFlush(doc);
                            
                            if (tgt.getDocuments() == null) {
                                tgt.setDocuments(new ArrayList<>());
                            }
                            tgt.getDocuments().add(doc);
                            rmaRepository.saveAndFlush(tgt);
                            logger.info("Added document to target RMA (second attempt)");
                        }
                    }
                }
            }
            
            logger.info("Successfully transferred document with ID {} to RMA {}", documentId, targetRmaId);
            return true;
        } catch (Exception e) {
            logger.error("Error transferring document with ID {} to RMA {}: {}", documentId, targetRmaId, e.getMessage(), e);
            return false;
        }
    }
} 