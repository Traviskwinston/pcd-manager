package com.pcd.manager.controller;

import com.pcd.manager.model.*;
import com.pcd.manager.service.*;
import com.pcd.manager.util.UploadUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.pcd.manager.model.Passdown;
import com.pcd.manager.service.PassdownService;
import java.time.LocalDate;
import com.pcd.manager.service.ExcelService;
import java.security.Principal;
import org.springframework.util.StringUtils;
import com.pcd.manager.model.User;
import com.pcd.manager.model.RmaComment;
import org.springframework.security.core.Authentication;
import com.pcd.manager.model.MovingPart;
import org.springframework.security.core.context.SecurityContextHolder;
import com.pcd.manager.service.TrackTrendService;
import org.springframework.transaction.annotation.Transactional;

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
    private final PassdownService passdownService;
    private final ExcelService excelService;
    private final MovingPartService movingPartService;
    private final TrackTrendService trackTrendService;

    @Autowired
    public RmaController(RmaService rmaService,
                         LocationService locationService,
                         ToolService toolService,
                         UserService userService,
                         UploadUtils uploadUtils,
                         FileTransferService fileTransferService,
                         PassdownService passdownService,
                         ExcelService excelService,
                         MovingPartService movingPartService,
                         TrackTrendService trackTrendService) {
        this.rmaService = rmaService;
        this.locationService = locationService;
        this.toolService = toolService;
        this.userService = userService;
        this.uploadUtils = uploadUtils;
        this.fileTransferService = fileTransferService;
        this.passdownService = passdownService;
        this.excelService = excelService;
        this.movingPartService = movingPartService;
        this.trackTrendService = trackTrendService;
    }

    @GetMapping
    public String listRmas(Model model) {
        List<Rma> rmas = rmaService.getAllRmas();
        
        // Explicitly load comments for each RMA
        for (Rma rma : rmas) {
            try {
                List<RmaComment> comments = rmaService.getCommentsForRma(rma.getId());
                rma.setComments(comments);
            } catch (Exception e) {
                // Log the error but continue processing
                logger.error("Error loading comments for RMA ID " + rma.getId() + ": " + e.getMessage(), e);
                rma.setComments(new ArrayList<>());
            }
        }
        
        // Load moving parts for each RMA and create a map
        Map<Long, List<MovingPart>> movingPartsMap = new HashMap<>();
        for (Rma rma : rmas) {
            try {
                List<MovingPart> movingParts = movingPartService.getMovingPartsByRmaId(rma.getId());
                movingPartsMap.put(rma.getId(), movingParts);
            } catch (Exception e) {
                logger.error("Error loading moving parts for RMA ID " + rma.getId() + ": " + e.getMessage(), e);
                movingPartsMap.put(rma.getId(), new ArrayList<>());
            }
        }
        
        model.addAttribute("rmas", rmas);
        model.addAttribute("movingPartsMap", movingPartsMap);
        return "rma/list";
    }

    @GetMapping("/test-upload")
    public String testUpload() {
        return "test-upload";
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
    public String showCreateForm(Model model, @RequestParam(required = false) Long toolId) {
        Rma rma = new Rma();
        
        // Get the currently logged-in user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUserName = null;
        
        if (authentication != null && authentication.isAuthenticated() && !authentication.getName().equals("anonymousUser")) {
            String username = authentication.getName(); // Username (email) of the logged-in user
            logger.info("Current authenticated user: {}", username);
            
            Optional<User> currentUser = userService.getUserByUsername(username);
            if (currentUser.isPresent()) {
                User user = currentUser.get();
                
                // Store user name for the view
                if (user.getName() != null && !user.getName().trim().isEmpty()) {
                    currentUserName = user.getName().trim();
                } else if (user.getFirstName() != null && !user.getFirstName().trim().isEmpty()) {
                    StringBuilder nameBuilder = new StringBuilder(user.getFirstName().trim());
                    if (user.getLastName() != null && !user.getLastName().trim().isEmpty()) {
                        nameBuilder.append(" ").append(user.getLastName().trim());
                    }
                    currentUserName = nameBuilder.toString();
                } else {
                    currentUserName = username;
                }
                
                logger.info("Found currentUserName: {}", currentUserName);
                
                // Set location with priority order:
                // 1. User's active site (this is what we're adding/prioritizing)
                // 2. User's default location 
                // 3. User's active tool's location
                if (user.getActiveSite() != null) {
                    rma.setLocation(user.getActiveSite());
                    logger.info("Set default location to user's active site: {}", user.getActiveSite().getDisplayName());
                } else if (user.getDefaultLocation() != null) {
                    rma.setLocation(user.getDefaultLocation());
                    logger.info("Set default location to user's default location: {}", user.getDefaultLocation().getDisplayName());
                } else if (user.getActiveTool() != null && user.getActiveTool().getLocation() != null) {
                    rma.setLocation(user.getActiveTool().getLocation());
                    logger.info("Set default location to user's active tool location: {}", user.getActiveTool().getLocation().getDisplayName());
                } else {
                    // If no user-specific location, try to get the system default location
                    Optional<Location> defaultLocation = locationService.getDefaultLocation();
                    if (defaultLocation.isPresent()) {
                        rma.setLocation(defaultLocation.get());
                        logger.info("Set default location to system default: {}", defaultLocation.get().getDisplayName());
                    } else {
                        logger.warn("No default location found for RMA form");
                    }
                }
            } else {
                logger.warn("Could not find user with username: {}", username);
            }
        } else {
            logger.warn("No authenticated user found or user is anonymous");
            
            // Try to set a default location from the system settings
            Optional<Location> defaultLocation = locationService.getDefaultLocation();
            if (defaultLocation.isPresent()) {
                rma.setLocation(defaultLocation.get());
                logger.info("Set default location to system default for anonymous user: {}", defaultLocation.get().getDisplayName());
            }
        }
        
        // If toolId is provided, fetch and set the tool
        if (toolId != null) {
            logger.info("Tool ID {} provided for new RMA", toolId);
            toolService.getToolById(toolId).ifPresent(tool -> {
                rma.setTool(tool);
                // If we have a tool, use its location only if we don't already have a location from the user
                if (tool.getLocation() != null && rma.getLocation() == null) {
                    rma.setLocation(tool.getLocation());
                    logger.info("Set location from provided tool: {}", tool.getLocation().getDisplayName());
                }
                logger.info("Pre-selected tool {} for new RMA", tool.getName());
            });
        }
        
        model.addAttribute("rma", rma);
        model.addAttribute("locations", locationService.getAllLocations());
        model.addAttribute("tools", toolService.getAllTools());
        model.addAttribute("technicians", userService.getAllUsers());
        
        // Add current user name to the model for use in the form
        model.addAttribute("currentUserName", currentUserName);
        
        return "rma/form";
    }

    @GetMapping("/{id}")
    public String showRma(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        logger.info("Showing RMA page for ID: {}", id);
        Optional<Rma> rmaOpt = rmaService.getRmaById(id);
        
        if (!rmaOpt.isPresent()) {
            logger.warn("RMA not found with ID: {}", id);
            redirectAttributes.addFlashAttribute("error", "RMA not found with ID: " + id);
            return "redirect:/rma";
        }
        
        Rma rma = rmaOpt.get();
        
        // Log document and picture information
        if (rma.getDocuments() != null) {
            logger.info("RMA {} has {} documents:", id, rma.getDocuments().size());
            rma.getDocuments().forEach(doc -> {
                logger.info("  Document: id={}, name='{}', path='{}', exists={}",
                    doc.getId(), doc.getFileName(), doc.getFilePath(),
                    uploadUtils.fileExists(doc.getFilePath()));
            });
        } else {
            logger.info("RMA {} has no documents collection", id);
        }
        
        if (rma.getPictures() != null) {
            logger.info("RMA {} has {} pictures:", id, rma.getPictures().size());
            rma.getPictures().forEach(pic -> {
                logger.info("  Picture: id={}, name='{}', path='{}', exists={}",
                    pic.getId(), pic.getFileName(), pic.getFilePath(),
                    uploadUtils.fileExists(pic.getFilePath()));
            });
        } else {
            logger.info("RMA {} has no pictures collection", id);
        }
        
        // Add all necessary model attributes
        model.addAttribute("rma", rma);
        model.addAttribute("allRmas", rmaService.getAllRmas());
        model.addAttribute("allTools", toolService.getAllTools());
        model.addAttribute("locations", locationService.getAllLocations());
        model.addAttribute("technicians", userService.getAllUsers());
        model.addAttribute("comments", rma.getComments());
        
        // Add moving parts and their destination chains
        List<MovingPart> movingParts = movingPartService.getMovingPartsByRmaId(id);
        model.addAttribute("movingParts", movingParts);
        
        // Create a map of moving part IDs to their destination chains
        Map<Long, List<Tool>> movingPartDestinationChains = new HashMap<>();
        for (MovingPart part : movingParts) {
            List<Tool> chain = movingPartService.getDestinationChainForMovingPart(part.getId());
            movingPartDestinationChains.put(part.getId(), chain);
        }
        model.addAttribute("movingPartDestinationChains", movingPartDestinationChains);
        
        return "rma/view";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, 
                               @RequestParam(required = false, defaultValue = "false") boolean importExcel,
                               Model model) {
        rmaService.getRmaById(id).ifPresent(rma -> model.addAttribute("rma", rma));
        model.addAttribute("locations", locationService.getAllLocations());
        model.addAttribute("tools", toolService.getAllTools());
        model.addAttribute("technicians", userService.getAllUsers());
        model.addAttribute("importExcel", importExcel);
        
        // Add all RMAs for transfer functionality
        model.addAttribute("allRmas", rmaService.getAllRmas());
        
        // Add moving parts associated with this RMA
        List<MovingPart> movingParts = movingPartService.getMovingPartsByRmaId(id);
        model.addAttribute("movingParts", movingParts);
        
        return "rma/form";
    }

    @PostMapping("/parse-excel")
    @ResponseBody
    public Map<String, Object> parseExcelFile(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("=== EXCEL FILE PARSING STARTED ===");
            logger.info("Received file: {}, size: {} bytes, content type: {}", 
                file.getOriginalFilename(), file.getSize(), file.getContentType());
            
            if (file.isEmpty()) {
                logger.warn("Uploaded file is empty");
                response.put("error", "Please select a file to upload");
                return response;
            }

            // Create a temporary RMA to store the file
            Rma tempRma = new Rma();
            tempRma.setDocuments(new ArrayList<>());
            
            // Save the Excel file as a document first
            logger.info("Saving Excel file as temporary document...");
            RmaDocument excelDoc = uploadUtils.saveRmaDocument(tempRma, file);
            
            if (excelDoc == null) {
                logger.error("Failed to save Excel file as document");
                response.put("error", "Failed to save Excel file");
                return response;
            }
            
            logger.info("Excel file saved successfully:");
            logger.info("  - File Name: {}", excelDoc.getFileName());
            logger.info("  - File Path: {}", excelDoc.getFilePath());
            logger.info("  - File Type: {}", excelDoc.getFileType());
            logger.info("  - File Size: {} bytes", excelDoc.getFileSize());

            // Now parse the saved file using its path
            logger.info("Parsing Excel file from path: {}", excelDoc.getFilePath());
            Map<String, Object> extractedData = excelService.extractRmaDataFromExcelFile(excelDoc.getFilePath());
            
            if (extractedData.containsKey("error")) {
                logger.error("Failed to parse Excel file: {}", extractedData.get("error"));
                // Clean up the temporary file since parsing failed
                uploadUtils.deleteFile(excelDoc.getFilePath());
                response.put("error", extractedData.get("error"));
                return response;
            }

            // Add the document info to the response
            extractedData.put("document", Map.of(
                "fileName", excelDoc.getFileName(),
                "filePath", excelDoc.getFilePath(),
                "fileType", excelDoc.getFileType(),
                "fileSize", excelDoc.getFileSize()
            ));

            logger.info("Excel file parsed successfully");
            response.putAll(extractedData);
            
            return response;
        } catch (Exception e) {
            logger.error("Error processing Excel file: {}", e.getMessage(), e);
            response.put("error", "Error processing Excel file: " + e.getMessage());
            return response;
        }
    }

    @PostMapping("/save")
    public String saveRma(@ModelAttribute Rma rma,
                         @RequestParam(value = "documentUploads", required = false) MultipartFile[] documentUploads,
                         @RequestParam(value = "imageUploads", required = false) MultipartFile[] imageUploads,
                         HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        try {
            // Debug logging
            logger.info("=== RMA SAVE REQUEST DEBUG ===");
            logger.info("Request Method: {}", request.getMethod());
            logger.info("Content Type: {}", request.getContentType());
            logger.info("documentUploads param exists: {}", documentUploads != null);
            logger.info("imageUploads param exists: {}", imageUploads != null);
            if (documentUploads != null) {
                logger.info("documentUploads length: {}", documentUploads.length);
                for (int i = 0; i < documentUploads.length; i++) {
                    MultipartFile f = documentUploads[i];
                    logger.info("documentUploads[{}]: empty={}, name={}, size={}", 
                        i, f.isEmpty(), f.getOriginalFilename(), f.getSize());
                }
            }
            if (imageUploads != null) {
                logger.info("imageUploads length: {}", imageUploads.length);
                for (int i = 0; i < imageUploads.length; i++) {
                    MultipartFile f = imageUploads[i];
                    logger.info("imageUploads[{}]: empty={}, name={}, size={}", 
                        i, f.isEmpty(), f.getOriginalFilename(), f.getSize());
                }
            }
            
            // Log all request parameters
            logger.info("All request parameter names:");
            request.getParameterNames().asIterator().forEachRemaining(name -> {
                logger.info("  {} = {}", name, request.getParameter(name));
            });
            logger.info("=== END DEBUG ===");
            
            logger.info("=== SAVING RMA ===");
            // Combine all file uploads into one list
            List<MultipartFile> allFiles = new ArrayList<>();
            
            // Handle document uploads
            if (documentUploads != null) {
                for (MultipartFile file : documentUploads) {
                    if (file != null && !file.isEmpty()) {
                        String contentType = file.getContentType();
                        logger.info("Processing document upload: {}, size: {}, type: {}", 
                            file.getOriginalFilename(), file.getSize(), contentType);
                        
                        // Check if this is an Excel file
                        boolean isExcelFile = (contentType != null && 
                            (contentType.equals("application/vnd.ms-excel") || 
                             contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))) ||
                            (file.getOriginalFilename() != null && 
                             (file.getOriginalFilename().toLowerCase().endsWith(".xlsx") || 
                              file.getOriginalFilename().toLowerCase().endsWith(".xls")));
                              
                        if (isExcelFile) {
                            logger.info("=== EXCEL FILE SAVE PROCESS STARTED ===");
                            logger.info("Found Excel file in document uploads: {}", file.getOriginalFilename());
                            
                            // Save Excel file as a document
                            logger.info("Attempting to save Excel file as document...");
                            RmaDocument excelDoc = uploadUtils.saveRmaDocument(rma, file);
                            
                            if (excelDoc != null) {
                                if (rma.getDocuments() == null) {
                                    rma.setDocuments(new ArrayList<>());
                                    logger.info("Initialized documents collection for RMA");
                                }
                                rma.getDocuments().add(excelDoc);
                                logger.info("Successfully saved Excel file as document:");
                                logger.info("  - File Name: {}", excelDoc.getFileName());
                                logger.info("  - File Path: {}", excelDoc.getFilePath());
                                logger.info("  - File Type: {}", excelDoc.getFileType());
                                logger.info("  - File Size: {} bytes", excelDoc.getFileSize());
                            } else {
                                logger.warn("Failed to save Excel file as document");
                            }
                            logger.info("=== EXCEL FILE SAVE PROCESS COMPLETED ===");
                        }
                        
                        allFiles.add(file);
                    }
                }
            }
            
            // Handle any direct image uploads
            if (imageUploads != null) {
                for (MultipartFile file : imageUploads) {
                    if (file != null && !file.isEmpty()) {
                        logger.info("Processing image upload: {}, size: {}, type: {}", 
                            file.getOriginalFilename(), file.getSize(), file.getContentType());
                        allFiles.add(file);
                    }
                }
            }
            
            // Log the total combined file count
            logger.info("Combined total of {} files for upload", allFiles.size());
            
            // Convert back to array for the service method
            MultipartFile[] combinedUploads = allFiles.isEmpty() ? null : allFiles.toArray(new MultipartFile[0]);
            
            // Save the RMA with all files
            Rma savedRma = rmaService.saveRma(rma, combinedUploads);
            logger.info("RMA saved successfully with ID: {}", savedRma.getId());
            
            // Log final document count
            int finalDocCount = savedRma.getDocuments() != null ? savedRma.getDocuments().size() : 0;
            logger.info("Final document count in saved RMA: {}", finalDocCount);
            if (savedRma.getDocuments() != null) {
                logger.info("Final document list:");
                for (RmaDocument doc : savedRma.getDocuments()) {
                    logger.info("  - {}", doc.getFileName());
                }
            }
            
            redirectAttributes.addFlashAttribute("message", "RMA saved successfully");
            return "redirect:/rma/" + savedRma.getId();
            
        } catch (Exception e) {
            logger.error("Error saving RMA: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error saving RMA: " + e.getMessage());
            return "redirect:/rma/list";
        }
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
    public Map<String, Object> ajaxGetToolDetails(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        Optional<Tool> toolOpt = toolService.getToolById(id);
        
        if (toolOpt.isPresent()) {
            Tool tool = toolOpt.get();
            result.put("id", tool.getId());
            result.put("name", tool.getName());
            result.put("secondaryName", tool.getSecondaryName());
            result.put("toolType", tool.getToolType());
            result.put("serialNumber1", tool.getSerialNumber1());
            result.put("serialNumber2", tool.getSerialNumber2());
            result.put("model1", tool.getModel1());
            result.put("model2", tool.getModel2());
            
            // Add new tool fields
            result.put("commissionDate", tool.getCommissionDate());
            result.put("chemicalGasService", tool.getChemicalGasService());
            result.put("startUpSl03Date", tool.getStartUpSl03Date());
            
            if (tool.getLocation() != null) {
                Map<String, Object> location = new HashMap<>();
                location.put("id", tool.getLocation().getId());
                location.put("displayName", tool.getLocation().getDisplayName());
                result.put("location", location);
            }
        }
        
        return result;
    }

    // Add API endpoint for getting all tools
    @GetMapping("/api/tools")
    @ResponseBody
    public List<Map<String, Object>> getAllTools() {
        List<Tool> tools = toolService.getAllTools();
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (Tool tool : tools) {
            Map<String, Object> toolMap = new HashMap<>();
            toolMap.put("id", tool.getId());
            toolMap.put("name", tool.getName());
            toolMap.put("chemicalGasService", tool.getChemicalGasService());
            
            if (tool.getLocation() != null) {
                toolMap.put("location", tool.getLocation().getDisplayName());
            }
            
            result.add(toolMap);
        }
        
        return result;
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

    /**
     * API endpoint to link a file between RMA and other entities (Tool, Passdown)
     */
    @PostMapping("/file/link")
    public String linkFile(@RequestParam Long fileId,
                           @RequestParam String fileType,
                           @RequestParam Long sourceRmaId,
                           @RequestParam String linkTarget,
                           @RequestParam(required = false) Long linkToolId,
                           @RequestParam(required = false) Long linkPassdownId) {
        
        logger.info("Request to link file - ID: {}, Type: {}, Source RMA: {}, Target Type: {}, Target ID: {}/{}",
                 fileId, fileType, sourceRmaId, linkTarget, linkToolId, linkPassdownId);
        
        try {
            boolean success = false;
            
            if ("tool".equals(linkTarget) && linkToolId != null) {
                // Handle linking to tool
                logger.info("Linking {} to Tool ID: {}", fileType, linkToolId);
                if ("document".equalsIgnoreCase(fileType)) {
                    // Logic for linking document to tool
                    success = rmaService.linkDocumentToTool(fileId, linkToolId);
                } else if ("picture".equalsIgnoreCase(fileType)) {
                    // Logic for linking picture to tool
                    success = rmaService.linkPictureToTool(fileId, linkToolId);
                }
            } else if ("passdown".equals(linkTarget) && linkPassdownId != null) {
                // Handle linking to passdown
                logger.info("Linking {} to Passdown ID: {}", fileType, linkPassdownId);
                if ("document".equalsIgnoreCase(fileType)) {
                    // Logic for linking document to passdown
                    success = rmaService.linkDocumentToPassdown(fileId, linkPassdownId);
                } else if ("picture".equalsIgnoreCase(fileType)) {
                    // Logic for linking picture to passdown
                    success = rmaService.linkPictureToPassdown(fileId, linkPassdownId);
                }
            }
            
            if (success) {
                logger.info("Successfully linked file");
            } else {
                logger.warn("Failed to link file");
            }
            
        } catch (Exception e) {
            logger.error("Error linking file: {}", e.getMessage(), e);
        }
        
        // Return to the RMA view
        return "redirect:/rma/" + sourceRmaId;
    }

    /**
     * Generate Excel RMA form
     */
    @GetMapping("/{id}/excel")
    public ResponseEntity<byte[]> generateExcelRma(@PathVariable Long id, Principal principal) {
        try {
            logger.info("===== GENERATING EXCEL RMA ID: {} =====", id);
            
            // Get the RMA
            Rma rma = rmaService.getRmaById(id)
                    .orElseThrow(() -> new RuntimeException("RMA not found with ID: " + id));
            
            // Debug - print all RMA fields to help diagnose the issue
            logger.info("RMA DETAILS - ID: {}, Number: '{}', SAP Notification: '{}', Service Order: '{}'", 
                       id, rma.getRmaNumber(), rma.getSapNotificationNumber(), rma.getServiceOrder());
            
            logger.info("TOOL INFO - Tool: {}", rma.getTool() != null ? rma.getTool().getName() : "null");
            
            // Debug - examine part line items in detail
            if (rma.getPartLineItems() != null) {
                logger.info("PART LINE ITEMS COUNT: {}", rma.getPartLineItems().size());
                for (int i = 0; i < rma.getPartLineItems().size(); i++) {
                    PartLineItem part = rma.getPartLineItems().get(i);
                    logger.info("PART #{} - Name: '{}', Number: '{}', Desc: '{}'", 
                              i+1, 
                              part.getPartName(), 
                              part.getPartNumber(), 
                              part.getProductDescription());
                }
            } else {
                logger.info("PART LINE ITEMS: null");
            }
            
            // Get current user
            User currentUser = null;
            if (principal != null) {
                currentUser = userService.getUserByEmail(principal.getName())
                        .orElse(null);
            }
            
            // Generate the Excel file
            byte[] excelContent = excelService.populateRmaTemplate(rma, currentUser);
            
            // Build descriptive filename
            StringBuilder filenameBuilder = new StringBuilder();
            logger.info("Building filename for RMA export. RMA Number: '{}', SAP Notification: '{}', ID: {}", 
                        rma.getRmaNumber(), rma.getSapNotificationNumber(), id);

            // Start with "RMA" as a prefix to always ensure context
            filenameBuilder.append("RMA");
            
            // Add RMA number, SAP notification, or ID
            if (rma.getRmaNumber() != null && !rma.getRmaNumber().trim().isEmpty()) {
                filenameBuilder.append("_").append(rma.getRmaNumber().trim());
                logger.info("Added RMA number to filename: '{}'", rma.getRmaNumber().trim());
            } else if (rma.getSapNotificationNumber() != null && !rma.getSapNotificationNumber().trim().isEmpty()) {
                filenameBuilder.append("_").append(rma.getSapNotificationNumber().trim());
                logger.info("Added SAP notification number to filename: '{}'", rma.getSapNotificationNumber().trim());
            } else if (rma.getServiceOrder() != null && !rma.getServiceOrder().trim().isEmpty()) {
                filenameBuilder.append("_").append(rma.getServiceOrder().trim());
                logger.info("Added Service Order to filename: '{}'", rma.getServiceOrder().trim());
            } else {
                filenameBuilder.append("_").append(id);
                logger.info("Added ID to filename: {}", id);
            }

            // Add tool name if available
            if (rma.getTool() != null && rma.getTool().getName() != null && !rma.getTool().getName().trim().isEmpty()) {
                filenameBuilder.append("_").append(rma.getTool().getName().trim());
                logger.info("Added tool name to filename: '{}'", rma.getTool().getName().trim());
            }

            // Add first part name/number if available
            boolean partInfoAdded = false;
            if (rma.getPartLineItems() != null && !rma.getPartLineItems().isEmpty()) {
                logger.info("Found {} part line items to consider for filename", rma.getPartLineItems().size());
                for (PartLineItem part : rma.getPartLineItems()) {
                    if (part.getPartNumber() != null && !part.getPartNumber().trim().isEmpty()) {
                        filenameBuilder.append("_").append(part.getPartNumber().trim());
                        logger.info("Added part number to filename: '{}'", part.getPartNumber().trim());
                        partInfoAdded = true;
                        break;
                    } else if (part.getPartName() != null && !part.getPartName().trim().isEmpty()) {
                        filenameBuilder.append("_").append(part.getPartName().trim());
                        logger.info("Added part name to filename: '{}'", part.getPartName().trim());
                        partInfoAdded = true;
                        break;
                    } else if (part.getProductDescription() != null && !part.getProductDescription().trim().isEmpty()) {
                        filenameBuilder.append("_").append(part.getProductDescription().trim());
                        logger.info("Added product description to filename: '{}'", part.getProductDescription().trim());
                        partInfoAdded = true;
                        break;
                    }
                }
                if (!partInfoAdded) {
                    logger.warn("No valid part numbers or names found in the part line items");
                }
            } else {
                logger.info("No part line items available to add to filename");
            }

            // Always add a timestamp for uniqueness
            filenameBuilder.append("_").append(System.currentTimeMillis());
            logger.info("Added timestamp to ensure unique filename");

            // Clean filename and ensure it ends with .xlsx
            String filename = filenameBuilder.toString()
                    .replaceAll("[\\\\/:*?\"<>|]", "_") // Replace invalid filename chars
                    .replaceAll("\\s+", "_") // Replace spaces with underscores
                    .trim() + ".xlsx";

            logger.info("Final Excel export filename: '{}'", filename);
            
            // Set up response headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", filename);
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
            
            return new ResponseEntity<>(excelContent, headers, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error generating Excel RMA form", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Handles Excel file upload to pre-populate RMA form
     */
    @PostMapping("/uploadExcel")
    @ResponseBody
    public Map<String, Object> uploadExcelFile(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (file.isEmpty()) {
                response.put("success", false);
                response.put("error", "Uploaded file is empty");
                return response;
            }
            
            // Check file extension 
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !(originalFilename.endsWith(".xlsx") || originalFilename.endsWith(".xls"))) {
                response.put("success", false);
                response.put("error", "Uploaded file is not an Excel file");
                return response;
            }
            
            // Extract data from Excel
            byte[] fileBytes = file.getBytes();
            Map<String, Object> extractedData = excelService.extractRmaDataFromExcel(fileBytes);
            
            // Check if extraction was successful
            if (extractedData.containsKey("error")) {
                response.put("success", false);
                response.put("error", extractedData.get("error"));
                return response;
            }
            
            response.put("success", true);
            response.put("data", extractedData);
            
        } catch (Exception e) {
            logger.error("Error uploading Excel file", e);
            response.put("success", false);
            response.put("error", "Failed to process Excel file: " + e.getMessage());
        }
        
        return response;
    }

    /**
     * Handle export of draft (unsaved) RMA to Excel
     */
    @PostMapping("/export-draft")
    public ResponseEntity<byte[]> exportDraftRma(@ModelAttribute Rma rma, Principal principal) {
        try {
            // Add more detailed logging for debugging
            logger.info("===== EXPORTING DRAFT/UNSAVED RMA WITH FORM VALUES =====");
            logger.info("Exporting data for RMA ID: {}, Number: '{}', SAP: '{}'", 
                       rma.getId(), rma.getRmaNumber(), rma.getSapNotificationNumber());
            
            // Report on parts data
            if (rma.getPartLineItems() != null) {
                logger.info("Found {} part line items in form data", rma.getPartLineItems().size());
                int index = 0;
                for (PartLineItem part : rma.getPartLineItems()) {
                    logger.info("Part #{}: Name='{}', Number: '{}', Desc='{}'", 
                               ++index, part.getPartName(), part.getPartNumber(), part.getProductDescription());
                }
            } else {
                logger.info("No part line items found in form data");
            }
            
            // Get current user
            User currentUser = null;
            if (principal != null) {
                currentUser = userService.getUserByEmail(principal.getName())
                        .orElse(null);
            }
            
            // Generate the Excel file from the draft RMA
            byte[] excelContent = excelService.populateRmaTemplate(rma, currentUser);
            
            // Build descriptive filename
            StringBuilder filenameBuilder = new StringBuilder();
            logger.info("Building filename for draft RMA export. RMA Number: '{}', SAP Notification: '{}'", 
                       rma.getRmaNumber(), rma.getSapNotificationNumber());

            // Start with "RMA" as a prefix to always ensure context
            filenameBuilder.append("RMA");
            
            // Add RMA number, SAP notification, Service Order or Draft
            if (rma.getRmaNumber() != null && !rma.getRmaNumber().trim().isEmpty()) {
                filenameBuilder.append("_").append(rma.getRmaNumber().trim());
                logger.info("Added RMA number to filename: '{}'", rma.getRmaNumber().trim());
            } else if (rma.getSapNotificationNumber() != null && !rma.getSapNotificationNumber().trim().isEmpty()) {
                filenameBuilder.append("_").append(rma.getSapNotificationNumber().trim());
                logger.info("Added SAP notification number to filename: '{}'", rma.getSapNotificationNumber().trim());
            } else if (rma.getServiceOrder() != null && !rma.getServiceOrder().trim().isEmpty()) {
                filenameBuilder.append("_").append(rma.getServiceOrder().trim());
                logger.info("Added Service Order to filename: '{}'", rma.getServiceOrder().trim());
            } else {
                filenameBuilder.append("_Draft");
                logger.info("Added 'Draft' to filename");
            }

            // Add tool name if available
            if (rma.getTool() != null && rma.getTool().getName() != null && !rma.getTool().getName().trim().isEmpty()) {
                filenameBuilder.append("_").append(rma.getTool().getName().trim());
                logger.info("Added tool name to filename: '{}'", rma.getTool().getName().trim());
            } else {
                logger.info("No tool name available to add to filename");
            }

            // Add first part name/number if available
            boolean partInfoAdded = false;
            if (rma.getPartLineItems() != null && !rma.getPartLineItems().isEmpty()) {
                logger.info("Found {} part line items to consider for filename", rma.getPartLineItems().size());
                for (PartLineItem part : rma.getPartLineItems()) {
                    if (part.getPartNumber() != null && !part.getPartNumber().trim().isEmpty()) {
                        filenameBuilder.append("_").append(part.getPartNumber().trim());
                        logger.info("Added part number to filename: '{}'", part.getPartNumber().trim());
                        partInfoAdded = true;
                        break;
                    } else if (part.getPartName() != null && !part.getPartName().trim().isEmpty()) {
                        filenameBuilder.append("_").append(part.getPartName().trim());
                        logger.info("Added part name to filename: '{}'", part.getPartName().trim());
                        partInfoAdded = true;
                        break;
                    } else if (part.getProductDescription() != null && !part.getProductDescription().trim().isEmpty()) {
                        filenameBuilder.append("_").append(part.getProductDescription().trim());
                        logger.info("Added product description to filename: '{}'", part.getProductDescription().trim());
                        partInfoAdded = true;
                        break;
                    }
                }
                if (!partInfoAdded) {
                    logger.warn("No valid part numbers or names found in the part line items");
                }
            } else {
                logger.info("No part line items available to add to filename");
            }

            // Always add a timestamp for uniqueness
            filenameBuilder.append("_").append(System.currentTimeMillis());
            logger.info("Added timestamp to ensure unique filename");

            // Clean filename and ensure it ends with .xlsx
            String filename = filenameBuilder.toString()
                    .replaceAll("[\\\\/:*?\"<>|]", "_") // Replace invalid filename chars
                    .replaceAll("\\s+", "_") // Replace spaces with underscores
                    .trim() + ".xlsx";

            logger.info("Final Excel export filename for form data: '{}'", filename);
            
            // Set up response headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", filename);
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
            
            return new ResponseEntity<>(excelContent, headers, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error generating Excel from form data", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Handles adding a new comment to an RMA
     */
    @GetMapping("/{id}/post-comment")
    public String getPostComment(@PathVariable Long id) {
        return "rma/post-comment";
    }

    @PostMapping("/{id}/post-comment")
    public String postComment(@PathVariable Long id, 
                            @RequestParam("content") String content,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {
        try {
            String userEmail = authentication.getName();
            rmaService.addComment(id, content, userEmail);
            redirectAttributes.addFlashAttribute("message", "Comment added successfully");
        } catch (Exception e) {
            logger.error("Error adding comment to RMA {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error adding comment: " + e.getMessage());
        }
        return "redirect:/rma/" + id;
    }

    @PostMapping("/{id}/update-notes")
    @ResponseBody
    public Map<String, Object> updateNotes(@PathVariable Long id, HttpServletRequest request) {
        logger.info("Updating notes for RMA ID: {}", id);
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<Rma> rmaOpt = rmaService.getRmaById(id);
            if (rmaOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "RMA not found");
                return response;
            }
            
            Rma rma = rmaOpt.get();
            String notes = request.getParameter("notes");
            rma.setNotes(notes);
            
            rmaService.saveRma(rma, null);
            response.put("success", true);
            response.put("message", "Notes updated successfully");
            
        } catch (Exception e) {
            logger.error("Error updating notes for RMA {}: {}", id, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error updating notes: " + e.getMessage());
        }
        
        return response;
    }
    
    @PostMapping("/{id}/update-status")
    @ResponseBody
    public Map<String, Object> updateStatus(@PathVariable Long id, HttpServletRequest request) {
        logger.info("Updating status for RMA ID: {}", id);
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<Rma> rmaOpt = rmaService.getRmaById(id);
            if (rmaOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "RMA not found");
                return response;
            }
            
            Rma rma = rmaOpt.get();
            String statusParam = request.getParameter("status");
            
            if (statusParam != null && !statusParam.trim().isEmpty()) {
                try {
                    RmaStatus newStatus = RmaStatus.valueOf(statusParam);
                    rma.setStatus(newStatus);
                    
                    rmaService.saveRma(rma, null);
                    response.put("success", true);
                    response.put("message", "Status updated successfully");
                    response.put("newStatus", newStatus.getDisplayName());
                    response.put("newStatusClass", getStatusBadgeClass(newStatus));
                    
                } catch (IllegalArgumentException e) {
                    response.put("success", false);
                    response.put("message", "Invalid status value: " + statusParam);
                }
            } else {
                response.put("success", false);
                response.put("message", "Status parameter is required");
            }
            
        } catch (Exception e) {
            logger.error("Error updating status for RMA {}: {}", id, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error updating status: " + e.getMessage());
        }
        
        return response;
    }
    
    private String getStatusBadgeClass(RmaStatus status) {
        return switch (status) {
            case RMA_WRITTEN_EMAILED -> "badge rounded-pill fs-6 bg-primary";
            case NUMBER_PROVIDED -> "badge rounded-pill fs-6 bg-info";
            case MEMO_EMAILED -> "badge rounded-pill fs-6 bg-secondary";
            case RECEIVED_PARTS -> "badge rounded-pill fs-6 bg-warning";
            case WAITING_CUSTOMER, WAITING_FSE -> "badge rounded-pill fs-6 bg-danger";
            case COMPLETED -> "badge rounded-pill fs-6 bg-success";
            default -> "badge rounded-pill fs-6 bg-light";
        };
    }

    /**
     * Add moving part from RMA context
     */
    @PostMapping("/{id}/moving-parts/add")
    public String addMovingPartFromRma(@PathVariable("id") Long rmaId,
                                     @RequestParam("partName") String partName,
                                     @RequestParam("fromToolId") Long fromToolId,
                                     @RequestParam("destinationToolIds") List<Long> destinationToolIds,
                                     @RequestParam(value = "notes", required = false) String notes,
                                     RedirectAttributes redirectAttributes) {
        
        try {
            // Get the RMA to link the moving part to
            Optional<Rma> rmaOpt = rmaService.getRmaById(rmaId);
            if (rmaOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "RMA not found");
                return "redirect:/rma";
            }
            
            Rma rma = rmaOpt.get();
            
            // For backward compatibility, if only one destination, use the toToolId parameter
            Long toToolId = (destinationToolIds != null && !destinationToolIds.isEmpty()) ? destinationToolIds.get(0) : null;
            
            MovingPart movingPart = movingPartService.createMovingPart(partName, fromToolId, toToolId, notes, null, rma);
            
            // If there are multiple destinations, set the destination chain
            if (destinationToolIds != null && destinationToolIds.size() > 1) {
                movingPart.setDestinationToolIds(destinationToolIds);
                movingPartService.save(movingPart);
            }
            
            redirectAttributes.addFlashAttribute("message", "Moving part recorded successfully");
        } catch (Exception e) {
            logger.error("Error adding moving part from RMA {}: {}", rmaId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error recording moving part: " + e.getMessage());
        }
        
        return "redirect:/rma/" + rmaId;
    }

    /**
     * Edit moving part from RMA context
     */
    @PostMapping("/{rmaId}/moving-parts/{movingPartId}/edit")
    public String editMovingPartFromRma(@PathVariable("rmaId") Long rmaId,
                                       @PathVariable("movingPartId") Long movingPartId,
                                       @RequestParam("partName") String partName,
                                       @RequestParam("fromToolId") Long fromToolId,
                                       @RequestParam("destinationToolIds") List<Long> destinationToolIds,
                                       @RequestParam(value = "notes", required = false) String notes,
                                       RedirectAttributes redirectAttributes) {
        
        try {
            // Get the existing moving part
            Optional<MovingPart> movingPartOpt = movingPartService.getMovingPartById(movingPartId);
            if (movingPartOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Moving part not found");
                return "redirect:/rma/" + rmaId;
            }
            
            MovingPart movingPart = movingPartOpt.get();
            
            // Update the moving part fields
            movingPart.setPartName(partName);
            movingPart.setNotes(notes);
            
            // Update tools
            if (fromToolId != null) {
                Optional<Tool> fromTool = toolService.getToolById(fromToolId);
                fromTool.ifPresent(movingPart::setFromTool);
            }
            
            // For backward compatibility, set the first destination as toTool
            Long toToolId = (destinationToolIds != null && !destinationToolIds.isEmpty()) ? destinationToolIds.get(0) : null;
            if (toToolId != null) {
                Optional<Tool> toTool = toolService.getToolById(toToolId);
                toTool.ifPresent(movingPart::setToTool);
            }
            
            // Set the destination chain if there are multiple destinations
            if (destinationToolIds != null && destinationToolIds.size() > 1) {
                movingPart.setDestinationToolIds(destinationToolIds);
            } else {
                // Clear destination chain if only one destination
                movingPart.setDestinationChain(null);
            }
            
            // Save the updated moving part
            movingPartService.save(movingPart);
            
            redirectAttributes.addFlashAttribute("success", "Moving part updated successfully");
            
        } catch (Exception e) {
            logger.error("Error updating moving part from RMA context", e);
            redirectAttributes.addFlashAttribute("error", "Failed to update moving part: " + e.getMessage());
        }
        
        return "redirect:/rma/" + rmaId;
    }
    
    /**
     * Delete moving part from RMA context
     */
    @PostMapping("/{rmaId}/moving-parts/{movingPartId}/delete")
    public String deleteMovingPartFromRma(@PathVariable("rmaId") Long rmaId,
                                         @PathVariable("movingPartId") Long movingPartId,
                                         RedirectAttributes redirectAttributes) {
        
        try {
            // Get the existing moving part
            Optional<MovingPart> movingPartOpt = movingPartService.getMovingPartById(movingPartId);
            if (movingPartOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Moving part not found");
                return "redirect:/rma/" + rmaId;
            }
            
            // Delete the moving part
            movingPartService.deleteMovingPart(movingPartId);
            
            redirectAttributes.addFlashAttribute("success", "Moving part deleted successfully");
            
        } catch (Exception e) {
            logger.error("Error deleting moving part from RMA context", e);
            redirectAttributes.addFlashAttribute("error", "Failed to delete moving part: " + e.getMessage());
        }
        
        return "redirect:/rma/" + rmaId;
    }

    @PostMapping("/{id}/update-header")
    @ResponseBody
    public Map<String, Object> updateHeader(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<Rma> rmaOpt = rmaService.getRmaById(id);
            if (!rmaOpt.isPresent()) {
                response.put("success", false);
                response.put("message", "RMA not found");
                return response;
            }
            
            Rma rma = rmaOpt.get();
            
            // Get updated values from request
            String rmaNumber = request.getParameter("rmaNumber");
            String sapNotificationNumber = request.getParameter("sapNotificationNumber");
            String toolIdStr = request.getParameter("toolId");
            
            // Update RMA number
            if (rmaNumber != null) {
                rma.setRmaNumber(rmaNumber.trim().isEmpty() ? null : rmaNumber.trim());
            }
            
            // Update SAP notification number
            if (sapNotificationNumber != null) {
                rma.setSapNotificationNumber(sapNotificationNumber.trim().isEmpty() ? null : sapNotificationNumber.trim());
            }
            
            // Update tool
            if (toolIdStr != null && !toolIdStr.trim().isEmpty()) {
                try {
                    Long toolId = Long.parseLong(toolIdStr);
                    Optional<Tool> toolOpt = toolService.getToolById(toolId);
                    if (toolOpt.isPresent()) {
                        rma.setTool(toolOpt.get());
                    } else {
                        response.put("success", false);
                        response.put("message", "Selected tool not found");
                        return response;
                    }
                } catch (NumberFormatException e) {
                    response.put("success", false);
                    response.put("message", "Invalid tool ID format");
                    return response;
                }
            } else {
                // Allow setting tool to null (no tool selected)
                rma.setTool(null);
            }
            
            // Save the updated RMA
            rmaService.saveRma(rma, null);
            
            response.put("success", true);
            response.put("message", "Header information updated successfully");
            
            logger.info("Updated header for RMA ID {}: RMA Number: {}, SAP Number: {}, Tool: {}", 
                       id, rma.getRmaNumber(), rma.getSapNotificationNumber(), 
                       rma.getTool() != null ? rma.getTool().getName() : "None");
            
        } catch (Exception e) {
            logger.error("Error updating header for RMA ID " + id, e);
            response.put("success", false);
            response.put("message", "Error updating header: " + e.getMessage());
        }
        
        return response;
    }

    @PostMapping("/{id}/update-parts")
    @ResponseBody
    @Transactional
    public Map<String, Object> updateParts(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("=== UPDATE PARTS REQUEST for RMA ID: {} ===", id);
            
            Optional<Rma> rmaOpt = rmaService.getRmaById(id);
            if (!rmaOpt.isPresent()) {
                response.put("success", false);
                response.put("message", "RMA not found");
                return response;
            }
            
            Rma rma = rmaOpt.get();
            
            // Clear existing part line items
            rma.getPartLineItems().clear();
            logger.info("Cleared existing parts");
            
            // Create hardcoded part with specified values
            PartLineItem hardcodedPart = new PartLineItem();
            hardcodedPart.setPartName("Part Name 1");
            hardcodedPart.setPartNumber("Part Number 1");
            hardcodedPart.setProductDescription("Part Description");
            hardcodedPart.setQuantity(2);
            hardcodedPart.setReplacementRequired(true);
            
            // Add the hardcoded part to RMA
            rma.getPartLineItems().add(hardcodedPart);
            logger.info("Added hardcoded part: name='{}', number='{}', desc='{}', qty={}, replacement={}", 
                hardcodedPart.getPartName(), hardcodedPart.getPartNumber(), 
                hardcodedPart.getProductDescription(), hardcodedPart.getQuantity(), 
                hardcodedPart.getReplacementRequired());
            
            // Save the RMA
            Rma savedRma = rmaService.saveRma(rma, null);
            
            // Verify the save by reloading from database
            Optional<Rma> verifyRmaOpt = rmaService.getRmaById(id);
            if (verifyRmaOpt.isPresent()) {
                Rma verifyRma = verifyRmaOpt.get();
                logger.info("VERIFICATION: Reloaded RMA has {} parts after save", verifyRma.getPartLineItems().size());
                for (int i = 0; i < verifyRma.getPartLineItems().size(); i++) {
                    PartLineItem verifyItem = verifyRma.getPartLineItems().get(i);
                    logger.info("  Verified Part {}: name='{}', number='{}', qty={}", 
                        i, verifyItem.getPartName(), verifyItem.getPartNumber(), verifyItem.getQuantity());
                }
            }
            
            response.put("success", true);
            response.put("message", "Parts information updated successfully");
            response.put("addedCount", 1);
            
        } catch (Exception e) {
            logger.error("Error updating parts for RMA ID " + id, e);
            response.put("success", false);
            response.put("message", "Error updating parts: " + e.getMessage());
        }
        
        return response;
    }
    
    @PostMapping("/{id}/update-dates")
    @ResponseBody
    public Map<String, Object> updateDates(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<Rma> rmaOpt = rmaService.getRmaById(id);
            if (!rmaOpt.isPresent()) {
                response.put("success", false);
                response.put("message", "RMA not found");
                return response;
            }
            
            Rma rma = rmaOpt.get();
            
            // Parse and update date fields
            String[] dateFields = {
                "writtenDate", "rmaNumberProvidedDate", "shippingMemoEmailedDate",
                "partsReceivedDate", "installedPartsDate", "failedPartsPackedDate", "failedPartsShippedDate"
            };
            
            for (String fieldName : dateFields) {
                String dateValue = request.getParameter(fieldName);
                if (dateValue != null && !dateValue.trim().isEmpty()) {
                    try {
                        LocalDate date = LocalDate.parse(dateValue);
                        switch (fieldName) {
                            case "writtenDate":
                                rma.setWrittenDate(date);
                                break;
                            case "rmaNumberProvidedDate":
                                rma.setRmaNumberProvidedDate(date);
                                break;
                            case "shippingMemoEmailedDate":
                                rma.setShippingMemoEmailedDate(date);
                                break;
                            case "partsReceivedDate":
                                rma.setPartsReceivedDate(date);
                                break;
                            case "installedPartsDate":
                                rma.setInstalledPartsDate(date);
                                break;
                            case "failedPartsPackedDate":
                                rma.setFailedPartsPackedDate(date);
                                break;
                            case "failedPartsShippedDate":
                                rma.setFailedPartsShippedDate(date);
                                break;
                        }
                    } catch (Exception e) {
                        logger.warn("Error parsing date field {}: {}", fieldName, dateValue, e);
                    }
                } else {
                    // Set to null if empty
                    switch (fieldName) {
                        case "writtenDate":
                            rma.setWrittenDate(null);
                            break;
                        case "rmaNumberProvidedDate":
                            rma.setRmaNumberProvidedDate(null);
                            break;
                        case "shippingMemoEmailedDate":
                            rma.setShippingMemoEmailedDate(null);
                            break;
                        case "partsReceivedDate":
                            rma.setPartsReceivedDate(null);
                            break;
                        case "installedPartsDate":
                            rma.setInstalledPartsDate(null);
                            break;
                        case "failedPartsPackedDate":
                            rma.setFailedPartsPackedDate(null);
                            break;
                        case "failedPartsShippedDate":
                            rma.setFailedPartsShippedDate(null);
                            break;
                    }
                }
            }
            
            // Save the updated RMA
            rmaService.saveRma(rma, null);
            
            response.put("success", true);
            response.put("message", "Dates updated successfully");
            
            logger.info("Updated dates for RMA ID {}", id);
            
        } catch (Exception e) {
            logger.error("Error updating dates for RMA ID " + id, e);
            response.put("success", false);
            response.put("message", "Error updating dates: " + e.getMessage());
        }
        
        return response;
    }
    
    @PostMapping("/{id}/update-problem")
    @ResponseBody
    public Map<String, Object> updateProblem(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<Rma> rmaOpt = rmaService.getRmaById(id);
            if (!rmaOpt.isPresent()) {
                response.put("success", false);
                response.put("message", "RMA not found");
                return response;
            }
            
            Rma rma = rmaOpt.get();
            
            // Update problem information fields
            rma.setProblemDiscoverer(request.getParameter("problemDiscoverer"));
            rma.setWhatHappened(request.getParameter("whatHappened"));
            rma.setWhyAndHowItHappened(request.getParameter("whyAndHowItHappened"));
            rma.setHowContained(request.getParameter("howContained"));
            rma.setWhoContained(request.getParameter("whoContained"));
            
            // Parse problem discovery date
            String discoveryDateStr = request.getParameter("problemDiscoveryDate");
            if (discoveryDateStr != null && !discoveryDateStr.trim().isEmpty()) {
                try {
                    rma.setProblemDiscoveryDate(LocalDate.parse(discoveryDateStr));
                } catch (Exception e) {
                    logger.warn("Error parsing problem discovery date: {}", discoveryDateStr, e);
                }
            } else {
                rma.setProblemDiscoveryDate(null);
            }
            
            // Update process impact fields
            rma.setInterruptionToFlow("true".equals(request.getParameter("interruptionToFlow")));
            rma.setInterruptionToProduction("true".equals(request.getParameter("interruptionToProduction")));
            rma.setExposedToProcessGasOrChemicals("true".equals(request.getParameter("exposedToProcessGasOrChemicals")));
            rma.setPurged("true".equals(request.getParameter("purged")));
            
            // Parse downtime hours
            String downtimeStr = request.getParameter("downtimeHours");
            if (downtimeStr != null && !downtimeStr.trim().isEmpty()) {
                try {
                    rma.setDowntimeHours(Double.parseDouble(downtimeStr));
                } catch (NumberFormatException e) {
                    logger.warn("Error parsing downtime hours: {}", downtimeStr, e);
                }
            } else {
                rma.setDowntimeHours(null);
            }
            
            rma.setInstructionsForExposedComponent(request.getParameter("instructionsForExposedComponent"));
            
            // Save the updated RMA
            rmaService.saveRma(rma, null);
            
            response.put("success", true);
            response.put("message", "Problem information updated successfully");
            
            logger.info("Updated problem information for RMA ID {}", id);
            
        } catch (Exception e) {
            logger.error("Error updating problem information for RMA ID " + id, e);
            response.put("success", false);
            response.put("message", "Error updating problem information: " + e.getMessage());
        }
        
        return response;
    }
    
    @PostMapping("/{id}/update-customer")
    @ResponseBody
    public Map<String, Object> updateCustomer(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<Rma> rmaOpt = rmaService.getRmaById(id);
            if (!rmaOpt.isPresent()) {
                response.put("success", false);
                response.put("message", "RMA not found");
                return response;
            }
            
            Rma rma = rmaOpt.get();
            
            // Update customer information fields
            rma.setCustomerName(request.getParameter("customerName"));
            rma.setCompanyShipToName(request.getParameter("companyShipToName"));
            rma.setCompanyShipToAddress(request.getParameter("companyShipToAddress"));
            rma.setCity(request.getParameter("city"));
            rma.setState(request.getParameter("state"));
            rma.setZipCode(request.getParameter("zipCode"));
            rma.setAttn(request.getParameter("attn"));
            rma.setCustomerContact(request.getParameter("customerContact"));
            rma.setCustomerPhone(request.getParameter("customerPhone"));
            rma.setCustomerEmail(request.getParameter("customerEmail"));
            rma.setSalesOrder(request.getParameter("salesOrder"));
            
            // Save the updated RMA
            rmaService.saveRma(rma, null);
            
            response.put("success", true);
            response.put("message", "Customer information updated successfully");
            
            logger.info("Updated customer information for RMA ID {}", id);
            
        } catch (Exception e) {
            logger.error("Error updating customer information for RMA ID " + id, e);
            response.put("success", false);
            response.put("message", "Error updating customer information: " + e.getMessage());
        }
        
        return response;
    }

    @PostMapping("/{id}/test-parts")
    @ResponseBody
    public Map<String, Object> testParts(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        logger.info("=== TEST PARTS REQUEST for RMA ID: {} ===", id);
        
        // Log all parameters received
        Map<String, String[]> paramMap = request.getParameterMap();
        logger.info("All request parameters:");
        for (Map.Entry<String, String[]> entry : paramMap.entrySet()) {
            logger.info("  {} = {}", entry.getKey(), java.util.Arrays.toString(entry.getValue()));
        }
        
        response.put("success", true);
        response.put("message", "Test endpoint reached successfully");
        response.put("paramCount", paramMap.size());
        
        return response;
    }

    @PostMapping("/{id}/debug-params")
    @ResponseBody
    public Map<String, Object> debugParams(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        logger.info("=== DEBUG PARAMS REQUEST for RMA ID: {} ===", id);
        
        // Log all parameters received
        Map<String, String[]> paramMap = request.getParameterMap();
        logger.info("Total parameters received: {}", paramMap.size());
        
        Map<String, Object> allParams = new HashMap<>();
        int partParamCount = 0;
        
        for (Map.Entry<String, String[]> entry : paramMap.entrySet()) {
            String paramName = entry.getKey();
            String[] values = entry.getValue();
            logger.info("  {} = {}", paramName, java.util.Arrays.toString(values));
            allParams.put(paramName, values.length == 1 ? values[0] : values);
            
            if (paramName.contains("partLineItems")) {
                partParamCount++;
            }
        }
        
        logger.info("Part-related parameters: {}", partParamCount);
        
        response.put("success", true);
        response.put("message", "Debug params logged successfully");
        response.put("totalParams", paramMap.size());
        response.put("partParams", partParamCount);
        response.put("allParams", allParams);
        
        return response;
    }

    /**
     * Diagnostic endpoint to check file upload configuration and paths
     */
    @GetMapping("/api/upload-diagnostic/{id}")
    @ResponseBody
    public Map<String, Object> uploadDiagnostic(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get upload directory info
            result.put("uploadDir", uploadUtils.getUploadDir());
            result.put("uploadDirAbsolute", new File(uploadUtils.getUploadDir()).getAbsolutePath());
            result.put("uploadDirExists", new File(uploadUtils.getUploadDir()).exists());
            
            // Check RMA and its files
            Optional<Rma> rmaOpt = rmaService.getRmaById(id);
            if (rmaOpt.isPresent()) {
                Rma rma = rmaOpt.get();
                result.put("rmaId", rma.getId());
                result.put("rmaNumber", rma.getRmaNumber());
                
                // Check documents
                List<Map<String, Object>> docInfo = new ArrayList<>();
                if (rma.getDocuments() != null) {
                    for (RmaDocument doc : rma.getDocuments()) {
                        Map<String, Object> info = new HashMap<>();
                        info.put("id", doc.getId());
                        info.put("fileName", doc.getFileName());
                        info.put("storedPath", doc.getFilePath());
                        
                        // Check different path resolutions
                        File f1 = new File(doc.getFilePath());
                        info.put("absolutePathExists", f1.exists());
                        info.put("absolutePath", f1.getAbsolutePath());
                        
                        File f2 = new File(uploadUtils.getUploadDir(), doc.getFilePath());
                        info.put("relativePathExists", f2.exists());
                        info.put("relativePath", f2.getAbsolutePath());
                        
                        docInfo.add(info);
                    }
                }
                result.put("documents", docInfo);
                result.put("documentCount", docInfo.size());
                
                // Check pictures
                List<Map<String, Object>> picInfo = new ArrayList<>();
                if (rma.getPictures() != null) {
                    for (RmaPicture pic : rma.getPictures()) {
                        Map<String, Object> info = new HashMap<>();
                        info.put("id", pic.getId());
                        info.put("fileName", pic.getFileName());
                        info.put("storedPath", pic.getFilePath());
                        
                        File f1 = new File(pic.getFilePath());
                        info.put("absolutePathExists", f1.exists());
                        info.put("absolutePath", f1.getAbsolutePath());
                        
                        File f2 = new File(uploadUtils.getUploadDir(), pic.getFilePath());
                        info.put("relativePathExists", f2.exists());
                        info.put("relativePath", f2.getAbsolutePath());
                        
                        picInfo.add(info);
                    }
                }
                result.put("pictures", picInfo);
                result.put("pictureCount", picInfo.size());
            }
            
            // Check subdirectories
            String[] subdirs = {"rma-pictures", "rma-documents"};
            Map<String, Object> subdirInfo = new HashMap<>();
            for (String subdir : subdirs) {
                File dir = new File(uploadUtils.getUploadDir(), subdir);
                subdirInfo.put(subdir + "_exists", dir.exists());
                subdirInfo.put(subdir + "_path", dir.getAbsolutePath());
            }
            result.put("subdirectories", subdirInfo);
            
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            logger.error("Upload diagnostic error: ", e);
        }
        
        return result;
    }
} 