package com.pcd.manager.controller;

import com.pcd.manager.model.Rma;
import com.pcd.manager.model.RmaDocument;
import com.pcd.manager.model.RmaPicture;
import com.pcd.manager.service.RmaService;
import com.pcd.manager.service.FileTransferService;
import com.pcd.manager.util.UploadUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for handling file management operations
 */
@Controller
@RequestMapping("/api/files")
public class FileManagementController {

    private static final Logger logger = LoggerFactory.getLogger(FileManagementController.class);
    
    private final RmaService rmaService;
    private final FileTransferService fileTransferService;
    private final UploadUtils uploadUtils;
    
    @Autowired
    public FileManagementController(
            RmaService rmaService,
            FileTransferService fileTransferService,
            UploadUtils uploadUtils) {
        this.rmaService = rmaService;
        this.fileTransferService = fileTransferService;
        this.uploadUtils = uploadUtils;
    }
    
    /**
     * Upload files to an RMA
     * 
     * @param rmaId The RMA ID to attach files to
     * @param files The files to upload
     * @return Response with upload status
     */
    @PostMapping("/upload/{rmaId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadFiles(
            @PathVariable Long rmaId,
            @RequestParam("files") MultipartFile[] files) {
        
        logger.info("File upload request for RMA ID: {} with {} files", rmaId, files.length);
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> fileResults = new ArrayList<>();
        
        try {
            // Verify RMA exists
            Optional<Rma> rmaOpt = rmaService.getRmaById(rmaId);
            if (rmaOpt.isEmpty()) {
                logger.warn("RMA with ID {} not found", rmaId);
                response.put("success", false);
                response.put("message", "RMA not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            Rma rma = rmaOpt.get();
            int successCount = 0;
            
            // Process each file
            for (MultipartFile file : files) {
                Map<String, Object> fileResult = new HashMap<>();
                fileResult.put("originalName", file.getOriginalFilename());
                fileResult.put("size", file.getSize());
                fileResult.put("contentType", file.getContentType());
                
                if (file.isEmpty()) {
                    fileResult.put("success", false);
                    fileResult.put("message", "File is empty");
                    fileResults.add(fileResult);
                    continue;
                }
                
                try {
                    // Validate file
                    if (!uploadUtils.validateFile(file)) {
                        fileResult.put("success", false);
                        fileResult.put("message", "File validation failed");
                        fileResults.add(fileResult);
                        continue;
                    }
                    
                    // Determine subdirectory based on content type
                    String contentType = file.getContentType();
                    boolean isImage = contentType != null && contentType.startsWith("image/");
                    String subdirectory = isImage ? "rma-pictures" : "rma-documents";
                    
                    // Save file to disk
                    String filePath = uploadUtils.saveFile(file, subdirectory);
                    if (filePath == null) {
                        fileResult.put("success", false);
                        fileResult.put("message", "Failed to save file");
                        fileResults.add(fileResult);
                        continue;
                    }
                    
                    // Create and associate entity with RMA
                    if (isImage) {
                        RmaPicture picture = new RmaPicture();
                        picture.setFileName(file.getOriginalFilename());
                        picture.setFilePath(filePath);
                        picture.setFileType(contentType);
                        picture.setFileSize(file.getSize());
                        picture.setRma(rma);
                        
                        rma.getPictures().add(picture);
                    } else {
                        RmaDocument document = new RmaDocument();
                        document.setFileName(file.getOriginalFilename());
                        document.setFilePath(filePath);
                        document.setFileType(contentType);
                        document.setFileSize(file.getSize());
                        document.setRma(rma);
                        
                        rma.getDocuments().add(document);
                    }
                    
                    successCount++;
                    fileResult.put("success", true);
                    fileResult.put("filePath", filePath);
                    fileResult.put("fileType", isImage ? "picture" : "document");
                    fileResult.put("message", "File uploaded successfully");
                } catch (Exception e) {
                    logger.error("Error processing file {}: {}", file.getOriginalFilename(), e.getMessage(), e);
                    fileResult.put("success", false);
                    fileResult.put("message", "Error: " + e.getMessage());
                }
                
                fileResults.add(fileResult);
            }
            
            // Save RMA with new files
            if (successCount > 0) {
                rmaService.saveRma(rma, new MultipartFile[0]);
            }
            
            response.put("success", successCount > 0);
            response.put("totalFiles", files.length);
            response.put("successCount", successCount);
            response.put("failureCount", files.length - successCount);
            response.put("files", fileResults);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error handling file upload for RMA {}: {}", rmaId, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Delete a file from an RMA
     * 
     * @param fileId The ID of the file to delete
     * @param fileType The type of file (document/picture)
     * @param rmaId The RMA ID
     * @return Response with deletion status
     */
    @DeleteMapping("/{fileType}/{fileId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteFile(
            @PathVariable Long fileId,
            @PathVariable String fileType,
            @RequestParam Long rmaId) {
        
        logger.info("Delete request for {} with ID: {} from RMA: {}", fileType, fileId, rmaId);
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean success = rmaService.deleteFile(fileId, fileType);
            
            if (success) {
                response.put("success", true);
                response.put("message", "File deleted successfully");
            } else {
                response.put("success", false);
                response.put("message", "Failed to delete file");
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error deleting file: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Transfer a file between RMAs
     * 
     * @param fileId The ID of the file to transfer
     * @param fileType The type of file (document/picture)
     * @param sourceRmaId The source RMA ID
     * @param targetRmaId The target RMA ID
     * @return Response with transfer status
     */
    @PostMapping("/transfer")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> transferFile(
            @RequestParam Long fileId,
            @RequestParam String fileType,
            @RequestParam Long sourceRmaId,
            @RequestParam Long targetRmaId) {
        
        logger.info("Transfer request for {} with ID: {} from RMA: {} to RMA: {}", 
                fileType, fileId, sourceRmaId, targetRmaId);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean success = false;
            
            if ("document".equalsIgnoreCase(fileType)) {
                success = fileTransferService.transferDocument(fileId, targetRmaId);
            } else if ("picture".equalsIgnoreCase(fileType)) {
                success = fileTransferService.transferPicture(fileId, targetRmaId);
            } else {
                response.put("success", false);
                response.put("message", "Invalid file type: " + fileType);
                return ResponseEntity.badRequest().body(response);
            }
            
            if (success) {
                response.put("success", true);
                response.put("message", "File transferred successfully");
            } else {
                response.put("success", false);
                response.put("message", "Failed to transfer file");
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error transferring file: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Bulk transfer files between RMAs
     * 
     * @param fileIds List of file IDs to transfer
     * @param fileTypes List of file types (document/picture)
     * @param sourceRmaId Source RMA ID
     * @param targetRmaIds List of target RMA IDs
     * @return Response with transfer status
     */
    @PostMapping("/bulk-transfer")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> bulkTransferFiles(
            @RequestParam List<Long> fileIds,
            @RequestParam List<String> fileTypes,
            @RequestParam Long sourceRmaId,
            @RequestParam List<Long> targetRmaIds) {
        
        logger.info("Bulk transfer request for {} files from RMA: {}", 
                fileIds.size(), sourceRmaId);
        
        try {
            Map<String, Object> result = fileTransferService.transferMultipleFiles(
                    fileIds, fileTypes, sourceRmaId, targetRmaIds);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error during bulk file transfer: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get file information for an RMA
     * 
     * @param rmaId The RMA ID
     * @return Response with file information
     */
    @GetMapping("/info/{rmaId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getFileInfo(@PathVariable Long rmaId) {
        logger.info("File info request for RMA ID: {}", rmaId);
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<Rma> rmaOpt = rmaService.getRmaById(rmaId);
            if (rmaOpt.isEmpty()) {
                logger.warn("RMA with ID {} not found", rmaId);
                response.put("success", false);
                response.put("message", "RMA not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            Rma rma = rmaOpt.get();
            response.put("success", true);
            response.put("rmaId", rmaId);
            response.put("rmaNumber", rma.getRmaNumber());
            
            // Process pictures
            List<Map<String, Object>> pictures = new ArrayList<>();
            if (rma.getPictures() != null) {
                for (RmaPicture picture : rma.getPictures()) {
                    Map<String, Object> pictureInfo = new HashMap<>();
                    pictureInfo.put("id", picture.getId());
                    pictureInfo.put("fileName", picture.getFileName());
                    pictureInfo.put("fileType", picture.getFileType());
                    pictureInfo.put("fileSize", picture.getFileSize());
                    pictureInfo.put("filePath", picture.getFilePath());
                    pictureInfo.put("existsOnDisk", uploadUtils.fileExists(picture.getFilePath()));
                    pictures.add(pictureInfo);
                }
            }
            response.put("pictures", pictures);
            response.put("pictureCount", pictures.size());
            
            // Process documents
            List<Map<String, Object>> documents = new ArrayList<>();
            if (rma.getDocuments() != null) {
                for (RmaDocument document : rma.getDocuments()) {
                    Map<String, Object> documentInfo = new HashMap<>();
                    documentInfo.put("id", document.getId());
                    documentInfo.put("fileName", document.getFileName());
                    documentInfo.put("fileType", document.getFileType());
                    documentInfo.put("fileSize", document.getFileSize());
                    documentInfo.put("filePath", document.getFilePath());
                    documentInfo.put("existsOnDisk", uploadUtils.fileExists(document.getFilePath()));
                    documents.add(documentInfo);
                }
            }
            response.put("documents", documents);
            response.put("documentCount", documents.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting file info for RMA {}: {}", rmaId, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Download or view a file
     * 
     * @param fileId The ID of the file to download
     * @param fileType The type of file (document/picture)
     * @return The file as a response
     */
    @GetMapping("/download/{fileType}/{fileId}")
    public ResponseEntity<?> downloadFile(
            @PathVariable Long fileId,
            @PathVariable String fileType) {
        
        logger.info("Download request for {} with ID: {}", fileType, fileId);
        
        try {
            if ("document".equalsIgnoreCase(fileType)) {
                Optional<RmaDocument> documentOpt = rmaService.findDocumentById(fileId);
                if (documentOpt.isEmpty()) {
                    logger.warn("Document with ID {} not found", fileId);
                    return ResponseEntity.notFound().build();
                }
                
                RmaDocument document = documentOpt.get();
                return uploadUtils.serveFile(document.getFilePath(), document.getFileType());
            } else if ("picture".equalsIgnoreCase(fileType)) {
                Optional<RmaPicture> pictureOpt = rmaService.findPictureById(fileId);
                if (pictureOpt.isEmpty()) {
                    logger.warn("Picture with ID {} not found", fileId);
                    return ResponseEntity.notFound().build();
                }
                
                RmaPicture picture = pictureOpt.get();
                return uploadUtils.serveFile(picture.getFilePath(), picture.getFileType());
            }
            
            logger.warn("Invalid file type: {}", fileType);
            return ResponseEntity.badRequest().body("Invalid file type");
            
        } catch (Exception e) {
            logger.error("Error serving file: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error serving file: " + e.getMessage());
        }
    }
} 