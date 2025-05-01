package com.pcd.manager.controller;

import com.pcd.manager.model.Rma;
import com.pcd.manager.model.RmaStatus;
import com.pcd.manager.model.Tool;
import com.pcd.manager.model.RmaPicture;
import com.pcd.manager.model.RmaDocument;
import com.pcd.manager.service.RmaService;
import com.pcd.manager.service.LocationService;
import com.pcd.manager.service.ToolService;
import com.pcd.manager.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.pcd.manager.util.UploadUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.core.io.UrlResource;
import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.lang.StringBuilder;
import java.util.HashSet;
import java.util.Set;
import com.pcd.manager.service.FileTransferService;
import java.util.HashMap;

@Controller
@RequestMapping("/rma")
public class RmaController {

    private static final Logger logger = LoggerFactory.getLogger(RmaController.class);

    private final RmaService rmaService;
    private final LocationService locationService;
    private final ToolService toolService;
    private final UserService userService;
    private final UploadUtils uploadUtils;
    private final FileTransferService fileTransferService;

    @Autowired
    public RmaController(RmaService rmaService,
                         LocationService locationService,
                         ToolService toolService,
                         UserService userService,
                         UploadUtils uploadUtils,
                         FileTransferService fileTransferService) {
        this.rmaService = rmaService;
        this.locationService = locationService;
        this.toolService = toolService;
        this.userService = userService;
        this.uploadUtils = uploadUtils;
        this.fileTransferService = fileTransferService;
    }

    @GetMapping
    public String listRmas(Model model) {
        List<Rma> rmas = rmaService.getAllRmas();
        model.addAttribute("rmas", rmas);
        return "rma/list";
    }

    @GetMapping("/matrix")
    public String showRmaMatrix(Model model) {
        List<Rma> allRmas = rmaService.getAllRmas();
        
        // Convert RMAs to map grouped by status
        Map<RmaStatus, List<Rma>> rmasByStatus = allRmas.stream()
                .collect(Collectors.groupingBy(rma -> rma.getStatus()));
        
        // Ensure all statuses are represented in the map
        Map<RmaStatus, List<Rma>> matrix = new EnumMap<>(RmaStatus.class);
        for (RmaStatus status : RmaStatus.values()) {
            matrix.put(status, rmasByStatus.getOrDefault(status, List.of()));
        }
        
        model.addAttribute("matrix", matrix);
        model.addAttribute("statuses", RmaStatus.values());
        
        return "rma/matrix";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("rma", new Rma());
        model.addAttribute("locations", locationService.getAllLocations());
        model.addAttribute("tools", toolService.getAllTools());
        model.addAttribute("technicians", userService.getAllUsers());
        return "rma/form";
    }

    @GetMapping("/{id}")
    public String showRma(@PathVariable Long id, Model model) {
        rmaService.getRmaById(id).ifPresent(rma -> model.addAttribute("rma", rma));
        return "rma/view";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        rmaService.getRmaById(id).ifPresent(rma -> model.addAttribute("rma", rma));
        model.addAttribute("locations", locationService.getAllLocations());
        model.addAttribute("tools", toolService.getAllTools());
        model.addAttribute("technicians", userService.getAllUsers());
        
        // Add all RMAs for transfer functionality
        model.addAttribute("allRmas", rmaService.getAllRmas());
        
        return "rma/form";
    }

    @PostMapping
    public String saveRma(@ModelAttribute Rma rma, 
                          @RequestParam(value = "fileUploads", required = false) MultipartFile[] fileUploads,
                          @RequestParam(value = "documentUploads", required = false) MultipartFile[] documentUploads,
                          @RequestParam(value = "imageUploads", required = false) MultipartFile[] imageUploads,
                          @RequestParam(value = "hasFileTransfers", required = false) Boolean hasFileTransfers,
                          @RequestParam(value = "transferFileIds", required = false) List<Long> transferFileIds,
                          @RequestParam(value = "transferFileTypes", required = false) List<String> transferFileTypes,
                          @RequestParam(value = "transferTargetRmaIds", required = false) List<Long> transferTargetRmaIds,
                          RedirectAttributes redirectAttributes) {
        try {
            // Log upload parameters for debugging
            logger.info("RMA ID: {}, fileUploads: {}, documentUploads: {}, imageUploads: {}", 
                rma.getId(), 
                fileUploads != null ? fileUploads.length : 0,
                documentUploads != null ? documentUploads.length : 0,
                imageUploads != null ? imageUploads.length : 0);
            
            // Log transfer parameters for debugging
            logger.info("hasFileTransfers: {}", hasFileTransfers);
            logger.info("transferFileIds: {}", transferFileIds);
            logger.info("transferFileTypes: {}", transferFileTypes);
            logger.info("transferTargetRmaIds: {}", transferTargetRmaIds);
            
            // Combine all file uploads into a single array
            List<MultipartFile> allFiles = new ArrayList<>();
            
            // Add main fileUploads (from the combined hidden input)
            if (fileUploads != null) {
                for (MultipartFile file : fileUploads) {
                    if (file != null && !file.isEmpty()) {
                        logger.info("Adding fileUpload: {}, size: {}", file.getOriginalFilename(), file.getSize());
                        allFiles.add(file);
                    }
                }
            }
            
            // Handle any direct document uploads
            if (documentUploads != null) {
                for (MultipartFile file : documentUploads) {
                    if (file != null && !file.isEmpty()) {
                        logger.info("Adding documentUpload: {}, size: {}", file.getOriginalFilename(), file.getSize());
                        allFiles.add(file);
                    }
                }
            }
            
            // Handle any direct image uploads
            if (imageUploads != null) {
                for (MultipartFile file : imageUploads) {
                    if (file != null && !file.isEmpty()) {
                        logger.info("Adding imageUpload: {}, size: {}", file.getOriginalFilename(), file.getSize());
                        allFiles.add(file);
                    }
                }
            }
            
            // Log the total combined file count
            logger.info("Combined total of {} files for upload", allFiles.size());
            
            // Convert back to array for the service method
            MultipartFile[] combinedUploads = allFiles.isEmpty() ? null : allFiles.toArray(new MultipartFile[0]);
            
            // Save the RMA first
            Rma savedRma = rmaService.saveRma(rma, combinedUploads);
            
            // Process file transfers if present
            boolean hasTransferErrors = false;
            StringBuilder transferResults = new StringBuilder();
            
            if (Boolean.TRUE.equals(hasFileTransfers) && transferFileIds != null && !transferFileIds.isEmpty() 
                    && transferFileTypes != null && !transferFileTypes.isEmpty() 
                    && transferTargetRmaIds != null && !transferTargetRmaIds.isEmpty()) {
                
                // Use the new FileTransferService for batch file transfers
                Map<String, Object> transferResult = fileTransferService.transferMultipleFiles(
                    transferFileIds, transferFileTypes, savedRma.getId(), transferTargetRmaIds);
                
                int successCount = (int) transferResult.get("successCount");
                int failureCount = (int) transferResult.get("failureCount");
                int totalFiles = (int) transferResult.get("totalFiles");
                
                if (successCount == totalFiles) {
                    transferResults.append("Successfully transferred all ").append(totalFiles).append(" files.");
                } else if (successCount > 0) {
                    transferResults.append("Successfully transferred ").append(successCount)
                        .append(" out of ").append(totalFiles).append(" files.");
                    if (failureCount > 0) {
                        hasTransferErrors = true;
                        transferResults.append(" ").append(failureCount).append(" transfers failed.");
                    }
                } else {
                    hasTransferErrors = true;
                    transferResults.append("Failed to transfer any files. Please check the logs for details.");
                }
                
                // Verify transfers for additional confirmation
                Map<String, Object> verificationResult = fileTransferService.verifyTransfers(
                    transferFileIds, transferFileTypes, transferTargetRmaIds);
                
                int verifiedCount = (int) verificationResult.get("verifiedCount");
                if (verifiedCount < successCount) {
                    hasTransferErrors = true;
                    transferResults.append(" Warning: Only ").append(verifiedCount)
                        .append(" out of ").append(successCount).append(" successful transfers could be verified.");
                }
            }
            
            // Set the appropriate message
            StringBuilder message = new StringBuilder("RMA saved successfully");
            if (allFiles.size() > 0) {
                message.append(" with ").append(allFiles.size()).append(" files");
            }
            
            if (hasTransferErrors) {
                redirectAttributes.addFlashAttribute("warning", 
                    message.toString() + ", but there were issues with file transfers: " + transferResults.toString());
            } else if (transferResults.length() > 0) {
                redirectAttributes.addFlashAttribute("message", 
                    message.toString() + ". " + transferResults.toString());
            } else {
                redirectAttributes.addFlashAttribute("message", message.toString() + ".");
            }
            
            logger.info("RMA {} saved successfully with {} files.", rma.getRmaNumber(), allFiles.size());
        } catch (Exception e) {
            logger.error("Error saving RMA: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error saving RMA: " + e.getMessage());
            if (rma.getId() != null) {
                return "redirect:/rma/edit/" + rma.getId();
            } else {
                return "redirect:/rma/new";
            }
        }
        return "redirect:/rma";
    }

    @PostMapping("/{id}/delete")
    public String deleteRma(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            rmaService.deleteRma(id);
             redirectAttributes.addFlashAttribute("message", "RMA deleted successfully.");
        } catch (Exception e) {
             logger.error("Error deleting RMA ID {}: {}", id, e.getMessage(), e);
             redirectAttributes.addFlashAttribute("error", "Error deleting RMA: " + e.getMessage());
        }
        return "redirect:/rma";
    }

    // Re-add API endpoint for fetching tool details
    @GetMapping("/api/tool/{id}")
    @ResponseBody
    public ResponseEntity<Tool> getToolDetails(@PathVariable Long id) {
        Optional<Tool> tool = toolService.getToolById(id);
        return tool.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Verify upload directory exists when accessing files
    @GetMapping("/files/**")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(HttpServletRequest request) {
        try {
            // Get the file path from the request
            String requestURL = request.getRequestURL().toString();
            // Extract everything after "/files/"
            String filePath = requestURL.substring(requestURL.indexOf("/files/") + "/files/".length());
            
            logger.info("Attempting to serve file: {}", filePath);
            
            // If the file path contains an absolute path (like C:/), extract just the relative portion
            if (filePath.contains(":/")) {
                // Extract just the path after "uploads/"
                int uploadsIndex = filePath.lastIndexOf("uploads/");
                if (uploadsIndex != -1) {
                    filePath = filePath.substring(uploadsIndex + "uploads/".length());
                    logger.info("Extracted relative path: {}", filePath);
                } else {
                    logger.warn("Could not find 'uploads/' in file path: {}", filePath);
                    return ResponseEntity.badRequest().build();
                }
            }
            
            // Create the complete file path using the configured upload directory
            String fullPath = uploadUtils.getUploadDir() + File.separator + filePath;
            logger.info("Full file path: {}", fullPath);
            
            // Check if the file exists
            if (!uploadUtils.fileExists(fullPath)) {
                logger.warn("File not found: {}", fullPath);
                return ResponseEntity.notFound().build();
            }
            
            // Create URL resource from the file
            File file = new File(fullPath);
            Resource resource = new UrlResource(file.toURI());
            
            // Determine content type
            String contentType = null;
            try {
                contentType = Files.probeContentType(Paths.get(file.getAbsolutePath()));
            } catch (IOException e) {
                logger.warn("Could not determine content type for file: {}", filePath);
            }
            
            if (contentType == null) {
                contentType = "application/octet-stream";
            }
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (Exception e) {
            logger.error("Error serving file: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Get upload directory info
    @GetMapping("/api/upload-info")
    @ResponseBody
    public Map<String, String> getUploadInfo() {
        try {
            Map<String, String> info = new HashMap<>();
            String uploadDirectory = uploadUtils.getUploadDir();
            info.put("uploadDir", uploadDirectory);
            info.put("uploadDirectory", uploadDirectory);
            info.put("status", "OK");
            
            File dir = new File(uploadDirectory);
            if (dir.exists() && dir.isDirectory()) {
                info.put("exists", "true");
                info.put("canWrite", String.valueOf(dir.canWrite()));
                info.put("canRead", String.valueOf(dir.canRead()));
                info.put("freeSpace", String.valueOf(dir.getFreeSpace()));
                info.put("absolutePath", dir.getAbsolutePath());
            } else {
                info.put("exists", "false");
                info.put("error", "Upload directory does not exist");
                info.put("createAttempt", "true");
                
                // Try to create the directory
                boolean created = dir.mkdirs();
                info.put("created", String.valueOf(created));
                if (created) {
                    info.put("createdPath", dir.getAbsolutePath());
                }
            }
            
            return info;
        } catch (Exception e) {
            logger.error("Error getting upload info: {}", e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("status", "ERROR");
            error.put("error", e.getMessage());
            return error;
        }
    }

    /**
     * Diagnostic API endpoint for RMA with complete file data
     */
    @GetMapping("/api/diagnose/{id}")
    @ResponseBody
    public Map<String, Object> diagnoseRma(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Optional<Rma> rmaOpt = rmaService.getRmaById(id);
            
            if (rmaOpt.isPresent()) {
                Rma rma = rmaOpt.get();
                result.put("rmaId", rma.getId());
                result.put("rmaNumber", rma.getRmaNumber());
                
                // Force initialization of collections 
                if (rma.getPictures() != null) {
                    List<Map<String, Object>> pictureData = new ArrayList<>();
                    
                    for (RmaPicture pic : rma.getPictures()) {
                        Map<String, Object> picInfo = new HashMap<>();
                        picInfo.put("id", pic.getId());
                        picInfo.put("fileName", pic.getFileName());
                        picInfo.put("filePath", pic.getFilePath());
                        picInfo.put("fileType", pic.getFileType());
                        picInfo.put("fileSize", pic.getFileSize());
                        
                        // Check if file exists on disk
                        boolean fileExists = uploadUtils.fileExists(pic.getFilePath());
                        picInfo.put("existsOnDisk", fileExists);
                        
                        pictureData.add(picInfo);
                    }
                    
                    result.put("pictureCount", rma.getPictures().size());
                    result.put("pictures", pictureData);
                } else {
                    result.put("pictureCount", 0);
                    result.put("pictures", Collections.emptyList());
                }
                
                // Same for documents
                if (rma.getDocuments() != null) {
                    List<Map<String, Object>> docData = new ArrayList<>();
                    
                    for (RmaDocument doc : rma.getDocuments()) {
                        Map<String, Object> docInfo = new HashMap<>();
                        docInfo.put("id", doc.getId());
                        docInfo.put("fileName", doc.getFileName());
                        docInfo.put("filePath", doc.getFilePath());
                        docInfo.put("fileType", doc.getFileType());
                        docInfo.put("fileSize", doc.getFileSize());
                        
                        // Check if file exists on disk
                        boolean fileExists = uploadUtils.fileExists(doc.getFilePath());
                        docInfo.put("existsOnDisk", fileExists);
                        
                        docData.add(docInfo);
                    }
                    
                    result.put("documentCount", rma.getDocuments().size());
                    result.put("documents", docData);
                } else {
                    result.put("documentCount", 0);
                    result.put("documents", Collections.emptyList());
                }
                
                result.put("uploadDirConfig", uploadUtils.getUploadDir());
                result.put("success", true);
                
            } else {
                result.put("error", "RMA not found with ID: " + id);
                result.put("success", false);
            }
        } catch (Exception e) {
            logger.error("Error diagnosing RMA: ", e);
            result.put("error", "Exception: " + e.getMessage());
            result.put("success", false);
        }
        
        return result;
    }
    
    // Check if a file exists
    @GetMapping("/api/file-exists")
    @ResponseBody
    public Map<String, Object> checkFileExists(@RequestParam String path) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean exists = uploadUtils.fileExists(path);
            result.put("exists", exists);
            result.put("path", path);
            
            if (exists) {
                File file = new File(path);
                result.put("size", file.length());
                result.put("lastModified", file.lastModified());
                result.put("canRead", file.canRead());
            }
            
            return result;
        } catch (Exception e) {
            logger.error("Error checking if file exists: {}", e.getMessage(), e);
            result.put("exists", false);
            result.put("error", e.getMessage());
            return result;
        }
    }
    
    /**
     * Handle file deletion requests
     */
    @PostMapping("/file/delete")
    public ResponseEntity<?> deleteFile(@RequestParam Long fileId,
                       @RequestParam String fileType,
                       @RequestParam Long rmaId) {
        logger.info("Deleting {} with ID {} from RMA {}", fileType, fileId, rmaId);
        
        try {
            boolean success = false;
            
            if ("picture".equalsIgnoreCase(fileType)) {
                success = rmaService.deletePicture(fileId);
            } else if ("document".equalsIgnoreCase(fileType)) {
                success = rmaService.deleteDocument(fileId);
            } else {
                logger.warn("Invalid file type: {}", fileType);
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Invalid file type");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (!success) {
                logger.warn("Failed to delete {} with ID {}", fileType, fileId);
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Failed to delete file");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
            
            logger.info("Successfully deleted {} with ID {}", fileType, fileId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "File deleted successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error deleting file: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * API endpoint to transfer a file between RMAs
     */
    @PostMapping("/file/transfer")
    public ResponseEntity<?> transferFile(@RequestParam Long fileId,
                       @RequestParam String fileType,
                       @RequestParam Long sourceRmaId,
                       @RequestParam Long targetRmaId) {
        logger.info("API request to transfer file - ID: {}, Type: {}, Source RMA: {}, Target RMA: {}", 
                  fileId, fileType, sourceRmaId, targetRmaId);
        
        try {
            boolean success;
            
            if ("document".equalsIgnoreCase(fileType)) {
                success = fileTransferService.transferDocument(fileId, targetRmaId);
            } else if ("picture".equalsIgnoreCase(fileType)) {
                success = fileTransferService.transferPicture(fileId, targetRmaId);
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Invalid file type: " + fileType
                ));
            }
            
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "File transferred successfully"
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Failed to transfer file. Check logs for details."
                ));
            }
        } catch (Exception e) {
            logger.error("Error transferring file: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Error: " + e.getMessage()
            ));
        }
    }
} 