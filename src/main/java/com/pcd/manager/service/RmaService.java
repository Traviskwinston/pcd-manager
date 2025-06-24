package com.pcd.manager.service;

import com.pcd.manager.model.Rma;
import com.pcd.manager.model.RmaStatus;
import com.pcd.manager.model.RmaPriority;
import com.pcd.manager.model.Location;
import com.pcd.manager.model.Tool;
import com.pcd.manager.model.RmaDocument;
import com.pcd.manager.model.RmaPicture;
import com.pcd.manager.model.Passdown;
import com.pcd.manager.model.PartLineItem;
import com.pcd.manager.model.RmaComment;
import com.pcd.manager.model.User;
import com.pcd.manager.repository.RmaRepository;
import com.pcd.manager.repository.RmaPictureRepository;
import com.pcd.manager.repository.RmaDocumentRepository;
import com.pcd.manager.repository.RmaCommentRepository;
import com.pcd.manager.repository.UserRepository;
import com.pcd.manager.util.UploadUtils;
import com.pcd.manager.repository.MovingPartRepository;
import com.pcd.manager.model.MovingPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.web.multipart.MultipartFile;
import org.hibernate.Hibernate;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Service
public class RmaService {

    private static final Logger logger = LoggerFactory.getLogger(RmaService.class);
    private final RmaRepository rmaRepository;
    private final RmaPictureRepository rmaPictureRepository;
    private final RmaDocumentRepository rmaDocumentRepository;
    private final UploadUtils uploadUtils;
    private final ToolService toolService;
    private final PassdownService passdownService;
    private final RmaCommentRepository rmaCommentRepository;
    private final UserRepository userRepository;
    private final MovingPartRepository movingPartRepository;

    private static final List<String> IMAGE_TYPES = Arrays.asList(
        "image/jpeg", "image/png", "image/gif", "image/bmp", "image/webp"
    );

    @Autowired
    public RmaService(RmaRepository rmaRepository, 
                     RmaPictureRepository rmaPictureRepository,
                     RmaDocumentRepository rmaDocumentRepository,
                     UploadUtils uploadUtils,
                     ToolService toolService,
                     PassdownService passdownService,
                     RmaCommentRepository rmaCommentRepository,
                     UserRepository userRepository,
                     MovingPartRepository movingPartRepository) {
        this.rmaRepository = rmaRepository;
        this.rmaPictureRepository = rmaPictureRepository;
        this.rmaDocumentRepository = rmaDocumentRepository;
        this.uploadUtils = uploadUtils;
        this.toolService = toolService;
        this.passdownService = passdownService;
        this.rmaCommentRepository = rmaCommentRepository;
        this.userRepository = userRepository;
        this.movingPartRepository = movingPartRepository;
    }

    @Cacheable(value = "rma-list", key = "'all-rmas'")
    public List<Rma> getAllRmas() {
        logger.info("Getting all RMAs ordered by written date descending (cacheable)");
        List<Rma> rmas = rmaRepository.findAllOrderedByWrittenDateDesc();
        logger.info("Retrieved {} RMAs, first RMA written date: {}", 
            rmas.size(), 
            rmas.isEmpty() ? "N/A" : (rmas.get(0).getWrittenDate() != null ? rmas.get(0).getWrittenDate() : "NULL"));
        return rmas;
    }

    /**
     * Find all RMAs associated with a specific tool
     * 
     * @param toolId the ID of the tool
     * @return list of RMAs associated with the tool
     */
    @Transactional(readOnly = true)
    public List<Rma> findRmasByToolId(Long toolId) {
        logger.info("Finding RMAs for tool ID: {}", toolId);
        List<Rma> rmas = rmaRepository.findByToolId(toolId);
        
        // Only initialize collections if needed for detail views
        // For list views, use lightweight queries instead
        logger.info("Found {} RMAs for tool ID: {}", rmas.size(), toolId);
        return rmas;
    }
    
    /**
     * Find RMAs associated with a tool with collections initialized (for detail views)
     */
    @Transactional(readOnly = true)
    public List<Rma> findRmasByToolIdWithCollections(Long toolId) {
        logger.info("Finding RMAs with collections for tool ID: {}", toolId);
        List<Rma> rmas = rmaRepository.findByToolId(toolId);
        
        // Initialize lazy-loaded collections
        for (Rma rma : rmas) {
            if (rma.getDocuments() != null) rma.getDocuments().size();
            if (rma.getPictures() != null) rma.getPictures().size();
        }
        
        logger.info("Found {} RMAs with collections for tool ID: {}", rmas.size(), toolId);
        return rmas;
    }

    /**
     * OPTIMIZATION: Bulk find RMAs for multiple tools to avoid N+1 queries
     * 
     * @param toolIds the list of tool IDs
     * @return list of RMAs associated with any of the tools
     */
    @Transactional(readOnly = true)
    public List<Rma> findRmasByToolIds(List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        logger.info("Bulk finding RMAs for {} tool IDs", toolIds.size());
        List<Rma> rmas = rmaRepository.findByToolIdIn(toolIds);
        
        // Initialize lazy-loaded collections
        for (Rma rma : rmas) {
            if (rma.getDocuments() != null) rma.getDocuments().size();
            if (rma.getPictures() != null) rma.getPictures().size();
        }
        
        logger.info("Found {} RMAs for {} tool IDs", rmas.size(), toolIds.size());
        return rmas;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "rma-details", key = "#id")
    public Optional<Rma> getRmaById(Long id) {
        logger.info("Getting RMA by ID: {} (cacheable)", id);
        Optional<Rma> rmaOpt = rmaRepository.findById(id);
        
        rmaOpt.ifPresent(rma -> {
            // Force initialization of collections
            if (rma.getDocuments() != null) {
                Hibernate.initialize(rma.getDocuments());
                logger.info("Initialized {} documents for RMA {}", rma.getDocuments().size(), id);
                // Initialize each document's data
                rma.getDocuments().forEach(doc -> {
                    Hibernate.initialize(doc);
                    logger.debug("Document: id={}, name='{}', path='{}'", 
                        doc.getId(), doc.getFileName(), doc.getFilePath());
                });
            }
            
            if (rma.getPictures() != null) {
                Hibernate.initialize(rma.getPictures());
                logger.info("Initialized {} pictures for RMA {}", rma.getPictures().size(), id);
                // Initialize each picture's data
                rma.getPictures().forEach(pic -> {
                    Hibernate.initialize(pic);
                    logger.debug("Picture: id={}, name='{}', path='{}'", 
                        pic.getId(), pic.getFileName(), pic.getFilePath());
                });
            }
            
            if (rma.getComments() != null) {
                Hibernate.initialize(rma.getComments());
                logger.debug("Initialized {} comments for RMA {}", rma.getComments().size(), id);
            }
            
            // Initialize part line items collection to avoid LazyInitializationException
            if (rma.getPartLineItems() != null) {
                Hibernate.initialize(rma.getPartLineItems());
                logger.debug("Initialized {} part line items for RMA {}", rma.getPartLineItems().size(), id);
            }
            
            // Initialize movement entries collection to avoid LazyInitializationException
            if (rma.getMovementEntries() != null) {
                Hibernate.initialize(rma.getMovementEntries());
                logger.debug("Initialized {} movement entries for RMA {}", rma.getMovementEntries().size(), id);
            }
        });
        
        return rmaOpt;
    }

    @Transactional
    @CacheEvict(value = {"rma-list", "rma-details", "dashboard-data"}, allEntries = true)
    public Rma saveRma(Rma rmaToSave, MultipartFile[] fileUploads) {
        try {
            logger.info("=== SAVING RMA WITH FILES ===");
            logger.info("RMA ID: {}, Number of files: {}", 
                rmaToSave.getId(), 
                fileUploads != null ? fileUploads.length : 0);
            
            // If this is an update, load the existing RMA data
            Rma rmaData = null;
            if (rmaToSave.getId() != null) {
                rmaData = rmaRepository.findById(rmaToSave.getId()).orElse(null);
                logger.info("Found existing RMA: {}", rmaData != null);
            }

            // Initialize collections if needed
            if (rmaData == null) {
                logger.info("Creating new RMA with empty collections");
                if (rmaToSave.getPartLineItems() == null) rmaToSave.setPartLineItems(new ArrayList<>());
                if (rmaToSave.getDocuments() == null) rmaToSave.setDocuments(new ArrayList<>());
                if (rmaToSave.getPictures() == null) rmaToSave.setPictures(new ArrayList<>());
            } else {
                logger.info("Updating existing RMA and ensuring collections exist");
                rmaToSave = rmaData;
                if (rmaToSave.getPartLineItems() == null) rmaToSave.setPartLineItems(new ArrayList<>());
                if (rmaToSave.getDocuments() == null) rmaToSave.setDocuments(new ArrayList<>());
                if (rmaToSave.getPictures() == null) rmaToSave.setPictures(new ArrayList<>());
            }

            // Save the RMA first to get an ID
            logger.info("Saving RMA to get ID");
            Rma savedRma = rmaRepository.saveAndFlush(rmaToSave);
            logger.info("RMA saved with ID: {}", savedRma.getId());
            
            // Process file uploads if any
            if (fileUploads != null && fileUploads.length > 0) {
                List<RmaDocument> documentsToAdd = new ArrayList<>();
                List<RmaPicture> picturesToAdd = new ArrayList<>();
                int successCount = 0;
                int errorCount = 0;
                
                for (MultipartFile file : fileUploads) {
                    if (file != null && !file.isEmpty()) {
                        try {
                            String originalFilename = file.getOriginalFilename();
                            String contentType = file.getContentType();
                            
                            if (originalFilename == null) {
                                logger.warn("Skipping file with null filename");
                                continue;
                            }
                            
                            logger.info("Processing file: {}, type: {}, size: {}", 
                                originalFilename, contentType, file.getSize());
                            
                            // Check if it's an Excel file
                            boolean isExcelFile = (contentType != null && 
                                (contentType.equals("application/vnd.ms-excel") || 
                                 contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))) ||
                                (originalFilename.toLowerCase().endsWith(".xlsx") || 
                                 originalFilename.toLowerCase().endsWith(".xls"));
                                 
                            if (isExcelFile) {
                                logger.info("Processing Excel file: {}", originalFilename);
                            }
                            
                            // Check if it's an image file
                            if (uploadUtils.isImageFile(originalFilename)) {
                                logger.info("Saving as picture: {}", originalFilename);
                                RmaPicture picture = uploadUtils.saveRmaPicture(savedRma, file);
                                if (picture != null) {
                                    picturesToAdd.add(picture);
                                    successCount++;
                                    logger.info("Successfully saved picture: {}", originalFilename);
                                }
                            } else {
                                logger.info("Saving as document: {}", originalFilename);
                                RmaDocument document = uploadUtils.saveRmaDocument(savedRma, file);
                                if (document != null) {
                                    documentsToAdd.add(document);
                                    successCount++;
                                    logger.info("Successfully saved document: {}", originalFilename);
                                }
                            }
                        } catch (Exception e) {
                            logger.error("Error processing file {}: {}", file.getOriginalFilename(), e.getMessage(), e);
                            errorCount++;
                        }
                    }
                }
                
                // Add all successful uploads to the RMA
                if (!documentsToAdd.isEmpty()) {
                    logger.info("Adding {} documents to RMA", documentsToAdd.size());
                    if (savedRma.getDocuments() == null) {
                        savedRma.setDocuments(new ArrayList<>());
                    }
                    savedRma.getDocuments().addAll(documentsToAdd);
                    // Save documents to the repository
                    for (RmaDocument doc : documentsToAdd) {
                        logger.info("Saving document to repository: {}", doc.getFileName());
                        rmaDocumentRepository.save(doc);
                    }
                }
                
                if (!picturesToAdd.isEmpty()) {
                    logger.info("Adding {} pictures to RMA", picturesToAdd.size());
                    if (savedRma.getPictures() == null) {
                        savedRma.setPictures(new ArrayList<>());
                    }
                    savedRma.getPictures().addAll(picturesToAdd);
                    // Save pictures to the repository
                    for (RmaPicture pic : picturesToAdd) {
                        logger.info("Saving picture to repository: {}", pic.getFileName());
                        rmaPictureRepository.save(pic);
                    }
                }
                
                // Save again if we added any files
                if (successCount > 0) {
                    logger.info("Saving RMA again with {} new files", successCount);
                    savedRma = rmaRepository.save(savedRma);
                    
                    // If this RMA is associated with a tool, automatically link the new files to the tool
                    if (savedRma.getTool() != null) {
                        logger.info("RMA {} is associated with Tool {}, linking new files automatically", 
                            savedRma.getId(), savedRma.getTool().getId());
                        linkAllFilesToTool(savedRma);
                    }
                }
                
                logger.info("File processing complete. Success: {}, Errors: {}", successCount, errorCount);
            }
            
            return savedRma;
            
        } catch (Exception e) {
            logger.error("Error saving RMA: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    @CacheEvict(value = {"rma-list", "rma-details", "dashboard-data"}, allEntries = true)
    public void deleteRma(Long id) {
        logger.info("Attempting to delete RMA ID: {}", id);
        Rma rma = getRmaById(id).orElseThrow(() -> new RuntimeException("RMA not found: " + id));

        // Detach pictures and documents from the tool if there is one
        if (rma.getTool() != null) {
            Tool tool = rma.getTool();
            
            // Handle pictures
            if (rma.getPictures() != null && !rma.getPictures().isEmpty() && 
                tool.getPicturePaths() != null && !tool.getPicturePaths().isEmpty()) {
                
                Set<String> rmaPicturePaths = rma.getPictures().stream()
                    .map(RmaPicture::getFilePath)
                    .filter(path -> path != null && !path.isEmpty())
                    .collect(Collectors.toSet());
                    
                // Find the paths to remove
                Set<String> picPathsToRemove = new HashSet<>();
                for (String path : tool.getPicturePaths()) {
                    if (rmaPicturePaths.contains(path)) {
                        picPathsToRemove.add(path);
                        // Also remove from names map
                        if (tool.getPictureNames() != null) {
                            tool.getPictureNames().remove(path);
                        }
                    }
                }
                
                if (!picPathsToRemove.isEmpty()) {
                    tool.getPicturePaths().removeAll(picPathsToRemove);
                    logger.info("Detached {} pictures from Tool {} before deleting RMA {}", 
                        picPathsToRemove.size(), tool.getId(), id);
                }
            }
            
            // Handle documents
            if (rma.getDocuments() != null && !rma.getDocuments().isEmpty() && 
                tool.getDocumentPaths() != null && !tool.getDocumentPaths().isEmpty()) {
                
                Set<String> rmaDocumentPaths = rma.getDocuments().stream()
                    .map(RmaDocument::getFilePath)
                    .filter(path -> path != null && !path.isEmpty())
                    .collect(Collectors.toSet());
                    
                // Find the paths to remove
                Set<String> docPathsToRemove = new HashSet<>();
                for (String path : tool.getDocumentPaths()) {
                    if (rmaDocumentPaths.contains(path)) {
                        docPathsToRemove.add(path);
                        // Also remove from names map
                        if (tool.getDocumentNames() != null) {
                            tool.getDocumentNames().remove(path);
                        }
                    }
                }
                
                if (!docPathsToRemove.isEmpty()) {
                    tool.getDocumentPaths().removeAll(docPathsToRemove);
                    logger.info("Detached {} documents from Tool {} before deleting RMA {}", 
                        docPathsToRemove.size(), tool.getId(), id);
                }
            }
            
            // Save tool if any files were detached
            try {
                toolService.saveTool(tool);
                logger.info("Saved Tool {} after detaching files from RMA {}", tool.getId(), id);
            } catch (Exception e) {
                logger.error("Error saving tool after detaching files: {}", e.getMessage(), e);
            }
        }

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

    /**
     * Link RMA pictures to the associated tool
     * 
     * @param rma The RMA containing pictures to link
     */
    @Transactional
    public void linkPicturesToTool(Rma rma) {
        if (rma == null || rma.getTool() == null || rma.getPictures() == null || rma.getPictures().isEmpty()) {
            logger.debug("No pictures to link or no tool associated with RMA ID: {}", 
                rma != null ? rma.getId() : "null");
            return;
        }
        
        Long toolId = rma.getTool().getId();
        logger.info("Linking {} pictures from RMA {} to Tool {}", 
            rma.getPictures().size(), rma.getId(), toolId);
        
        Tool tool = rma.getTool();
        
        // Process each picture in the RMA
        for (RmaPicture rmaPicture : rma.getPictures()) {
            // Skip null or empty paths
            if (rmaPicture.getFilePath() == null || rmaPicture.getFilePath().isEmpty()) {
                continue;
            }
            
            // Create tag with RMA and parts info
            String tag = createPictureTag(rma);
            
            // Only add if not already linked
            if (tool.getPicturePaths() == null) {
                tool.setPicturePaths(new HashSet<>());
            }
            if (tool.getPictureNames() == null) {
                tool.setPictureNames(new HashMap<>());
            }
            
            // Check if already exists in tool
            if (!tool.getPicturePaths().contains(rmaPicture.getFilePath())) {
                tool.getPicturePaths().add(rmaPicture.getFilePath());
                
                // Store the original name with the RMA tag prefix
                String originalName = rmaPicture.getFileName() != null ? rmaPicture.getFileName() : 
                    rmaPicture.getFilePath().substring(rmaPicture.getFilePath().lastIndexOf('/') + 1);
                
                tool.getPictureNames().put(rmaPicture.getFilePath(), tag + ": " + originalName);
                
                logger.info("Linked picture {} from RMA {} to Tool {}", 
                    rmaPicture.getFilePath(), rma.getId(), toolId);
            }
        }
        
        // Save the tool with the updated pictures
        try {
            toolService.saveTool(tool);
            logger.info("Successfully saved tool {} with linked pictures from RMA {}", 
                toolId, rma.getId());
        } catch (Exception e) {
            logger.error("Error saving tool with linked pictures: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Link RMA documents to the associated tool
     * 
     * @param rma The RMA containing documents to link
     */
    @Transactional
    public void linkDocumentsToTool(Rma rma) {
        if (rma == null || rma.getTool() == null || rma.getDocuments() == null || rma.getDocuments().isEmpty()) {
            logger.debug("No documents to link or no tool associated with RMA ID: {}", 
                rma != null ? rma.getId() : "null");
            return;
        }
        
        Long toolId = rma.getTool().getId();
        logger.info("Linking {} documents from RMA {} to Tool {}", 
            rma.getDocuments().size(), rma.getId(), toolId);
        
        Tool tool = rma.getTool();
        
        // Process each document in the RMA
        for (RmaDocument rmaDocument : rma.getDocuments()) {
            // Skip null or empty paths
            if (rmaDocument.getFilePath() == null || rmaDocument.getFilePath().isEmpty()) {
                continue;
            }
            
            // Create tag with RMA and parts info
            String tag = createPictureTag(rma);
            
            // Only add if not already linked
            if (tool.getDocumentPaths() == null) {
                tool.setDocumentPaths(new HashSet<>());
            }
            if (tool.getDocumentNames() == null) {
                tool.setDocumentNames(new HashMap<>());
            }
            
            // Check if already exists in tool
            if (!tool.getDocumentPaths().contains(rmaDocument.getFilePath())) {
                tool.getDocumentPaths().add(rmaDocument.getFilePath());
                
                // Store the original name with the RMA tag prefix
                String originalName = rmaDocument.getFileName() != null ? rmaDocument.getFileName() : 
                    rmaDocument.getFilePath().substring(rmaDocument.getFilePath().lastIndexOf('/') + 1);
                
                tool.getDocumentNames().put(rmaDocument.getFilePath(), tag + ": " + originalName);
                
                logger.info("Linked document {} from RMA {} to Tool {}", 
                    rmaDocument.getFilePath(), rma.getId(), toolId);
            }
        }
        
        // Save the tool with the updated documents
        try {
            toolService.saveTool(tool);
            logger.info("Successfully saved tool {} with linked documents from RMA {}", 
                toolId, rma.getId());
        } catch (Exception e) {
            logger.error("Error saving tool with linked documents: {}", e.getMessage(), e);
        }
    }

    /**
     * Link all RMA files (pictures and documents) to the associated tool
     * 
     * @param rma The RMA containing files to link
     */
    @Transactional
    public void linkAllFilesToTool(Rma rma) {
        if (rma == null || rma.getTool() == null) {
            return;
        }
        
        linkPicturesToTool(rma);
        linkDocumentsToTool(rma);
    }

    /**
     * Create a tag for files with RMA and parts information
     */
    private String createPictureTag(Rma rma) {
        StringBuilder tag = new StringBuilder();
        
        // Just show the RMA number
        if (rma.getRmaNumber() != null && !rma.getRmaNumber().isEmpty()) {
            tag.append("RMA ").append(rma.getRmaNumber());
        } else {
            tag.append("RMA-").append(rma.getId());
        }
        
        // Add part info if available - using part names instead of numbers
        if (rma.getPartLineItems() != null && !rma.getPartLineItems().isEmpty()) {
            tag.append(" | Parts: ");
            int count = 0;
            for (PartLineItem part : rma.getPartLineItems()) {
                if (count++ < 3) { // Limit to first 3 parts to keep tag manageable
                    // Use part name if available, otherwise use part number
                    String partName = part.getPartName();
                    if (partName == null || partName.isEmpty()) {
                        partName = part.getPartNumber();
                    }
                    
                    if (count > 1) {
                        tag.append(", ");
                    }
                    tag.append(partName);
                } else {
                    tag.append("...");
                    break;
                }
            }
        }
        
        return tag.toString();
    }
    
    /**
     * Update tool associations when an RMA's tool is changed
     * 
     * @param rma The RMA with updated tool
     * @param oldToolId The previous tool ID
     */
    @Transactional
    public void updateRmaToolAssociation(Rma rma, Long oldToolId) {
        if (rma == null || rma.getId() == null) {
            logger.warn("Cannot update tool association for null RMA");
            return;
        }
        
        // If tool didn't change or there are no files, nothing to do
        if ((oldToolId == null && rma.getTool() == null) || 
            (oldToolId != null && rma.getTool() != null && oldToolId.equals(rma.getTool().getId())) ||
            ((rma.getPictures() == null || rma.getPictures().isEmpty()) && 
             (rma.getDocuments() == null || rma.getDocuments().isEmpty()))) {
            return;
        }
        
        logger.info("Updating tool association for RMA {} from Tool {} to Tool {}", 
            rma.getId(), oldToolId, rma.getTool() != null ? rma.getTool().getId() : "null");
        
        // If there was a previous tool, remove files from it
        if (oldToolId != null) {
            Tool oldTool = toolService.getToolById(oldToolId).orElse(null);
            if (oldTool != null) {
                // Handle pictures
                if (rma.getPictures() != null && !rma.getPictures().isEmpty() &&
                    oldTool.getPicturePaths() != null && !oldTool.getPicturePaths().isEmpty()) {
                    
                    logger.info("Removing pictures from previous Tool {}", oldToolId);
                    
                    // Get paths of pictures associated with this RMA
                    Set<String> rmaPicturePaths = rma.getPictures().stream()
                        .map(RmaPicture::getFilePath)
                        .filter(path -> path != null && !path.isEmpty())
                        .collect(Collectors.toSet());
                    
                    // Remove these paths from the old tool
                    Set<String> picPathsToRemove = new HashSet<>();
                    for (String path : oldTool.getPicturePaths()) {
                        if (rmaPicturePaths.contains(path)) {
                            picPathsToRemove.add(path);
                            oldTool.getPictureNames().remove(path);
                        }
                    }
                    
                    oldTool.getPicturePaths().removeAll(picPathsToRemove);
                    logger.info("Removed {} pictures from Tool {}", picPathsToRemove.size(), oldToolId);
                }
                
                // Handle documents
                if (rma.getDocuments() != null && !rma.getDocuments().isEmpty() &&
                    oldTool.getDocumentPaths() != null && !oldTool.getDocumentPaths().isEmpty()) {
                    
                    logger.info("Removing documents from previous Tool {}", oldToolId);
                    
                    // Get paths of documents associated with this RMA
                    Set<String> rmaDocumentPaths = rma.getDocuments().stream()
                        .map(RmaDocument::getFilePath)
                        .filter(path -> path != null && !path.isEmpty())
                        .collect(Collectors.toSet());
                    
                    // Remove these paths from the old tool
                    Set<String> docPathsToRemove = new HashSet<>();
                    for (String path : oldTool.getDocumentPaths()) {
                        if (rmaDocumentPaths.contains(path)) {
                            docPathsToRemove.add(path);
                            oldTool.getDocumentNames().remove(path);
                        }
                    }
                    
                    oldTool.getDocumentPaths().removeAll(docPathsToRemove);
                    logger.info("Removed {} documents from Tool {}", docPathsToRemove.size(), oldToolId);
                }
                
                // Save the old tool with files removed
                try {
                    toolService.saveTool(oldTool);
                    logger.info("Successfully saved old Tool {} after removing files", oldToolId);
                } catch (Exception e) {
                    logger.error("Error removing files from old tool: {}", e.getMessage(), e);
                }
            }
        }
        
        // Add files to the new tool if there is one
        if (rma.getTool() != null) {
            linkAllFilesToTool(rma);
        }
    }

    /**
     * Link a document from an RMA to a Tool
     * 
     * @param documentId the ID of the document
     * @param toolId the ID of the tool
     * @return true if successful, false otherwise
     */
    @Transactional
    public boolean linkDocumentToTool(Long documentId, Long toolId) {
        logger.info("Linking document {} to tool {}", documentId, toolId);
        try {
            // Get the document
            Optional<RmaDocument> documentOpt = findDocumentById(documentId);
            if (documentOpt.isEmpty()) {
                logger.warn("Document with ID {} not found", documentId);
                return false;
            }
            
            RmaDocument document = documentOpt.get();
            String documentPath = document.getFilePath();
            String fileName = document.getFileName();
            
            // Get the tool
            Optional<Tool> toolOpt = toolService.getToolById(toolId);
            if (toolOpt.isEmpty()) {
                logger.warn("Tool with ID {} not found", toolId);
                return false;
            }
            
            Tool tool = toolOpt.get();
            
            // Check if the document is already linked to the tool
            if (tool.getDocumentPaths() != null && tool.getDocumentPaths().contains(documentPath)) {
                logger.warn("Document is already linked to tool {}", toolId);
                return true; // Already linked, so technically successful
            }
            
            // Add the document path to the tool
            if (tool.getDocumentPaths() == null) {
                tool.setDocumentPaths(new HashSet<>());
            }
            tool.getDocumentPaths().add(documentPath);
            
            // Update document name mapping if needed
            if (tool.getDocumentNames() == null) {
                tool.setDocumentNames(new HashMap<>());
            }
            
            // Format: "RMA-123: filename.pdf"
            String rmaPrefix = "RMA-" + document.getRma().getId() + ": ";
            tool.getDocumentNames().put(documentPath, rmaPrefix + fileName);
            
            // Save the tool
            toolService.saveTool(tool);
            
            logger.info("Successfully linked document to tool {}", toolId);
            return true;
        } catch (Exception e) {
            logger.error("Error linking document to tool: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Link a picture from an RMA to a Tool
     * 
     * @param pictureId the ID of the picture
     * @param toolId the ID of the tool
     * @return true if successful, false otherwise
     */
    @Transactional
    public boolean linkPictureToTool(Long pictureId, Long toolId) {
        logger.info("Linking picture {} to tool {}", pictureId, toolId);
        try {
            // Get the picture
            Optional<RmaPicture> pictureOpt = findPictureById(pictureId);
            if (pictureOpt.isEmpty()) {
                logger.warn("Picture with ID {} not found", pictureId);
                return false;
            }
            
            RmaPicture picture = pictureOpt.get();
            String picturePath = picture.getFilePath();
            String fileName = picture.getFileName();
            
            // Get the tool
            Optional<Tool> toolOpt = toolService.getToolById(toolId);
            if (toolOpt.isEmpty()) {
                logger.warn("Tool with ID {} not found", toolId);
                return false;
            }
            
            Tool tool = toolOpt.get();
            
            // Check if the picture is already linked to the tool
            if (tool.getPicturePaths() != null && tool.getPicturePaths().contains(picturePath)) {
                logger.warn("Picture is already linked to tool {}", toolId);
                return true; // Already linked, so technically successful
            }
            
            // Add the picture path to the tool
            if (tool.getPicturePaths() == null) {
                tool.setPicturePaths(new HashSet<>());
            }
            tool.getPicturePaths().add(picturePath);
            
            // Update picture name mapping if needed
            if (tool.getPictureNames() == null) {
                tool.setPictureNames(new HashMap<>());
            }
            
            // Format: "RMA-123: filename.jpg"
            String rmaPrefix = "RMA-" + picture.getRma().getId() + ": ";
            tool.getPictureNames().put(picturePath, rmaPrefix + fileName);
            
            // Save the tool
            toolService.saveTool(tool);
            
            logger.info("Successfully linked picture to tool {}", toolId);
            return true;
        } catch (Exception e) {
            logger.error("Error linking picture to tool: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Link a document from an RMA to a Passdown
     * 
     * @param documentId the ID of the document
     * @param passdownId the ID of the passdown
     * @return true if successful, false otherwise
     */
    @Transactional
    public boolean linkDocumentToPassdown(Long documentId, Long passdownId) {
        logger.info("Linking document {} to passdown {}", documentId, passdownId);
        try {
            // Get the document
            Optional<RmaDocument> documentOpt = findDocumentById(documentId);
            if (documentOpt.isEmpty()) {
                logger.warn("Document with ID {} not found", documentId);
                return false;
            }
            
            RmaDocument document = documentOpt.get();
            String documentPath = document.getFilePath();
            
            // Get the passdown
            Optional<Passdown> passdownOpt = passdownService.getPassdownById(passdownId);
            if (passdownOpt.isEmpty()) {
                logger.warn("Passdown with ID {} not found", passdownId);
                return false;
            }
            
            Passdown passdown = passdownOpt.get();
            
            // Check if the document is already linked to the passdown
            if (passdown.getDocumentPaths() != null && passdown.getDocumentPaths().contains(documentPath)) {
                logger.warn("Document is already linked to passdown {}", passdownId);
                return true; // Already linked, so technically successful
            }
            
            // Add the document path to the passdown
            if (passdown.getDocumentPaths() == null) {
                passdown.setDocumentPaths(new HashSet<>());
            }
            passdown.getDocumentPaths().add(documentPath);
            
            // Save the passdown
            passdownService.savePassdown(passdown);
            
            logger.info("Successfully linked document to passdown {}", passdownId);
            return true;
        } catch (Exception e) {
            logger.error("Error linking document to passdown: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Link a picture from an RMA to a Passdown
     * 
     * @param pictureId the ID of the picture
     * @param passdownId the ID of the passdown
     * @return true if successful, false otherwise
     */
    @Transactional
    public boolean linkPictureToPassdown(Long pictureId, Long passdownId) {
        logger.info("Linking picture {} to passdown {}", pictureId, passdownId);
        try {
            // Get the picture
            Optional<RmaPicture> pictureOpt = findPictureById(pictureId);
            if (pictureOpt.isEmpty()) {
                logger.warn("Picture with ID {} not found", pictureId);
                return false;
            }
            
            RmaPicture picture = pictureOpt.get();
            String picturePath = picture.getFilePath();
            
            // Get the passdown
            Optional<Passdown> passdownOpt = passdownService.getPassdownById(passdownId);
            if (passdownOpt.isEmpty()) {
                logger.warn("Passdown with ID {} not found", passdownId);
                return false;
            }
            
            Passdown passdown = passdownOpt.get();
            
            // Check if the picture is already linked to the passdown
            if (passdown.getPicturePaths() != null && passdown.getPicturePaths().contains(picturePath)) {
                logger.warn("Picture is already linked to passdown {}", passdownId);
                return true; // Already linked, so technically successful
            }
            
            // Add the picture path to the passdown
            if (passdown.getPicturePaths() == null) {
                passdown.setPicturePaths(new HashSet<>());
            }
            passdown.getPicturePaths().add(picturePath);
            
            // Save the passdown
            passdownService.savePassdown(passdown);
            
            logger.info("Successfully linked picture to passdown {}", passdownId);
            return true;
        } catch (Exception e) {
            logger.error("Error linking picture to passdown: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Add a comment to an RMA
     */
    @Transactional
    public RmaComment addComment(Long rmaId, String content, String userEmail) {
        Rma rma = rmaRepository.findById(rmaId)
            .orElseThrow(() -> new IllegalArgumentException("RMA not found: " + rmaId));
        
        User user = userRepository.findByEmailIgnoreCase(userEmail)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));
        
        RmaComment comment = new RmaComment();
        comment.setContent(content);
        comment.setRma(rma);
        comment.setUser(user);
        comment.setCreatedDate(LocalDateTime.now());
        
        RmaComment savedComment = rmaCommentRepository.save(comment);
        
        // Add the comment to the RMA's comment list
        rma.getComments().add(savedComment);
        rmaRepository.save(rma);
        
        return savedComment;
    }
    
    /**
     * Get all comments for an RMA
     */
    public List<RmaComment> getCommentsForRma(Long rmaId) {
        return rmaCommentRepository.findByRmaIdOrderByCreatedDateDesc(rmaId);
    }

    @Transactional
    public boolean linkFileToRma(Long rmaId, String filePath, String originalFileName, 
                               String fileType, String sourceEntityType, Long sourceEntityId) {
        logger.info("Attempting to link file {} (type: {}) to RMA ID: {}. Original source: {} {}", 
                    filePath, fileType, rmaId, sourceEntityType, sourceEntityId);

        Optional<Rma> rmaOpt = rmaRepository.findById(rmaId);
        if (rmaOpt.isEmpty()) {
            logger.warn("Target RMA with ID {} not found.", rmaId);
            return false;
        }
        Rma rma = rmaOpt.get();

        if (filePath == null || filePath.isBlank()) {
            logger.warn("File path is null or blank, cannot link to RMA {}", rmaId);
            return false;
        }

        // Initialize collections if null
        if (rma.getDocuments() == null) rma.setDocuments(new ArrayList<>());
        if (rma.getPictures() == null) rma.setPictures(new ArrayList<>());

        long fileSize = 0L;
        try {
            // Attempt to get file size if uploadUtils can provide the full path
            java.nio.file.Path fullPath = java.nio.file.Paths.get(uploadUtils.getUploadDir() + java.io.File.separator + filePath);
            if (java.nio.file.Files.exists(fullPath)) {
                fileSize = java.nio.file.Files.size(fullPath);
            }
        } catch (Exception e) {
            logger.warn("Could not determine file size for {}: {}", filePath, e.getMessage());
        }
        
        String actualFileType = determineMimeTypeFromPath(filePath); // Helper to get MIME type

        if ("document".equalsIgnoreCase(fileType)) {
            boolean alreadyLinked = rma.getDocuments().stream()
                                     .anyMatch(doc -> filePath.equals(doc.getFilePath()));
            if (alreadyLinked) {
                logger.info("Document {} is already linked to RMA {}.", filePath, rmaId);
                return true; // Or false if re-linking is an error
            }
            RmaDocument document = new RmaDocument();
            document.setRma(rma);
            document.setFilePath(filePath);
            document.setFileName(originalFileName);
            document.setFileType(actualFileType);
            document.setFileSize(fileSize);
            rmaDocumentRepository.save(document);
            rma.getDocuments().add(document); // Add to the RMA's collection
            logger.info("Linked document {} to RMA {}", filePath, rmaId);
        } else if ("picture".equalsIgnoreCase(fileType)) {
            boolean alreadyLinked = rma.getPictures().stream()
                                     .anyMatch(pic -> filePath.equals(pic.getFilePath()));
            if (alreadyLinked) {
                logger.info("Picture {} is already linked to RMA {}.", filePath, rmaId);
                return true; // Or false
            }
            RmaPicture picture = new RmaPicture();
            picture.setRma(rma);
            picture.setFilePath(filePath);
            picture.setFileName(originalFileName);
            picture.setFileType(actualFileType);
            picture.setFileSize(fileSize);
            rmaPictureRepository.save(picture);
            rma.getPictures().add(picture); // Add to the RMA's collection
            logger.info("Linked picture {} to RMA {}", filePath, rmaId);
        } else {
            logger.warn("Unsupported file type '{}' for linking to RMA.", fileType);
            return false;
        }

        try {
            rmaRepository.save(rma); // Save RMA to persist the new document/picture in its list
            return true;
        } catch (Exception e) {
            logger.error("Error saving RMA {} after linking file {}: {}", rmaId, filePath, e.getMessage(), e);
            return false;
        }
    }
    
    // Helper method to determine MIME type (simplified)
    private String determineMimeTypeFromPath(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return "application/octet-stream";
        }
        String lowerPath = filePath.toLowerCase();
        if (lowerPath.endsWith(".pdf")) return "application/pdf";
        if (lowerPath.endsWith(".doc")) return "application/msword";
        if (lowerPath.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lowerPath.endsWith(".xls")) return "application/vnd.ms-excel";
        if (lowerPath.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lowerPath.endsWith(".txt")) return "text/plain";
        if (lowerPath.endsWith(".csv")) return "text/csv";
        if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) return "image/jpeg";
        if (lowerPath.endsWith(".png")) return "image/png";
        if (lowerPath.endsWith(".gif")) return "image/gif";
        // Add more common types as needed
        return "application/octet-stream"; // Default
    }

    @Transactional
    public boolean linkMovingPartToRma(Long movingPartId, Long targetRmaId) {
        logger.info("Attempting to link MovingPart ID: {} to RMA ID: {}", movingPartId, targetRmaId);
        Optional<MovingPart> movingPartOpt = movingPartRepository.findById(movingPartId);
        Optional<Rma> targetRmaOpt = rmaRepository.findById(targetRmaId);

        if (movingPartOpt.isPresent() && targetRmaOpt.isPresent()) {
            MovingPart movingPart = movingPartOpt.get();
            Rma targetRma = targetRmaOpt.get();

            // Prevent linking to its own primary RMA if that logic is desired
            if (movingPart.getRma() != null && movingPart.getRma().getId().equals(targetRmaId)) {
                logger.info("MovingPart {} is already primarily associated with RMA {}. Not creating additional link.", movingPartId, targetRmaId);
                // Optionally, still return true if this is considered a successful state for UI feedback
                // Or, if an additional link is always for a *different* RMA, this could be an error or no-op.
                // For now, let's say it's not an error but doesn't create a new link if it's the same as primary.
                return true; 
            }

            movingPart.setAdditionallyLinkedRma(targetRma);
            movingPartRepository.save(movingPart);
            logger.info("Successfully linked MovingPart {} to RMA {} via additionallyLinkedRma field", movingPartId, targetRmaId);
            return true;
        }
        if (movingPartOpt.isEmpty()) logger.warn("MovingPart not found with ID: {}", movingPartId);
        if (targetRmaOpt.isEmpty()) logger.warn("Target RMA not found with ID: {}", targetRmaId);
        return false;
    }

    @Transactional
    public void updateRmaStatus(Long rmaId, RmaStatus newStatus) {
        Rma rma = rmaRepository.findById(rmaId)
            .orElseThrow(() -> new IllegalArgumentException("RMA not found with ID: " + rmaId));
        rma.setStatus(newStatus);
        rmaRepository.save(rma);
        logger.info("Updated status for RMA ID {} to {}", rmaId, newStatus);
    }
    
    @Transactional
    public void updateRmaPriority(Long rmaId, RmaPriority newPriority) {
        Rma rma = rmaRepository.findById(rmaId)
            .orElseThrow(() -> new IllegalArgumentException("RMA not found with ID: " + rmaId));
        rma.setPriority(newPriority);
        rmaRepository.save(rma);
        logger.info("Updated priority for RMA ID {} to {}", rmaId, newPriority);
    }

    /**
     * OPTIMIZATION: Bulk find lightweight RMA data for multiple tools to avoid loading full objects
     * Returns only essential fields: id, rmaNumber, status, toolId
     */
    @Transactional(readOnly = true)
    public List<Object[]> findRmaListDataByToolIds(List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        logger.info("Bulk finding lightweight RMA data for {} tool IDs", toolIds.size());
        List<Object[]> rmaData = rmaRepository.findRmaListDataByToolIds(toolIds);
        logger.info("Found {} lightweight RMA records for {} tool IDs", rmaData.size(), toolIds.size());
        return rmaData;
    }
} 