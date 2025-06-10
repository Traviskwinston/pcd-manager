package com.pcd.manager.controller;

import com.pcd.manager.util.UploadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;

@Controller
@RequestMapping("/documents")
public class DocumentController {

    private static final Logger logger = LoggerFactory.getLogger(DocumentController.class);

    @Value("${app.upload.dir:${user.dir}/uploads}")
    private String uploadDir;

    private final String DOCUMENT_SUBDIR = "reference-documents";

    @Autowired
    private UploadUtils uploadUtils;

    /**
     * Display all reference documents
     */
    @GetMapping
    public String listDocuments(Model model) {
        try {
            Path documentsRootPath = Paths.get(uploadDir, DOCUMENT_SUBDIR);
            if (!Files.exists(documentsRootPath)) {
                Files.createDirectories(documentsRootPath);
                logger.info("Created reference documents directory: {}", documentsRootPath);
            }

            List<Map<String, String>> documents = new ArrayList<>();
            if (Files.exists(documentsRootPath) && Files.isDirectory(documentsRootPath)) {
                try {
                    documents = Files.walk(documentsRootPath)
                            .filter(Files::isRegularFile)
                            .filter(path -> {
                                try {
                                    // Additional check to ensure it's a readable file
                                    return Files.isReadable(path) && Files.size(path) >= 0;
                                } catch (IOException e) {
                                    logger.warn("Cannot read file: {}", path, e);
                                    return false;
                                }
                            })
                            .map(path -> {
                                try {
                                    Map<String, String> docInfo = new HashMap<>();
                                    String relativePath = documentsRootPath.relativize(path).toString().replace("\\", "/");
                                    docInfo.put("name", path.getFileName().toString());
                                    docInfo.put("path", relativePath); 
                                    
                                    try {
                                        long sizeInBytes = Files.size(path);
                                        docInfo.put("size", String.valueOf(sizeInBytes / 1024)); // Size in KB
                                        docInfo.put("lastModified", Files.getLastModifiedTime(path).toString());
                                    } catch (IOException e) {
                                        logger.warn("Could not read metadata for file: {}", path, e);
                                        docInfo.put("size", "N/A");
                                        docInfo.put("lastModified", "N/A");
                                    }
                                    return docInfo;
                                } catch (Exception e) {
                                    logger.error("Error processing file: {}", path, e);
                                    // Return a valid map even in case of error
                                    Map<String, String> errorDocInfo = new HashMap<>();
                                    errorDocInfo.put("name", "Error reading file");
                                    errorDocInfo.put("path", "N/A");
                                    errorDocInfo.put("size", "N/A");
                                    errorDocInfo.put("lastModified", "N/A");
                                    return errorDocInfo;
                                }
                            })
                            .filter(docMap -> docMap != null && docMap.containsKey("name"))
                            .sorted(Comparator.comparing(docMap -> docMap.get("name").toLowerCase()))
                            .collect(Collectors.toList());
                } catch (IOException e) {
                    logger.error("Error walking directory tree", e);
                    documents = new ArrayList<>();
                }
            }

            logger.debug("Found {} documents", documents.size());
            model.addAttribute("documents", documents);
            return "documents/list";
        } catch (Exception e) {
            logger.error("Error listing documents", e);
            model.addAttribute("error", "Failed to list documents: " + e.getMessage());
            model.addAttribute("documents", new ArrayList<>()); // Ensure documents is never null
            return "documents/list";
        }
    }

    /**
     * Upload a new document
     */
    @PostMapping("/upload")
    public String uploadDocument(@RequestParam("file") MultipartFile file,
                                 RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please select a file to upload");
            return "redirect:/documents";
        }

        try {
            // Save the file
            String savedPath = uploadUtils.saveFile(file, DOCUMENT_SUBDIR);
            if (savedPath != null) {
                redirectAttributes.addFlashAttribute("message", 
                        "Document uploaded successfully: " + file.getOriginalFilename());
            } else {
                redirectAttributes.addFlashAttribute("error", 
                        "Failed to upload document: " + file.getOriginalFilename());
            }
        } catch (IOException e) {
            logger.error("Error uploading document", e);
            redirectAttributes.addFlashAttribute("error", 
                    "Failed to upload document: " + e.getMessage());
        }

        return "redirect:/documents";
    }

    /**
     * Download a document
     */
    @GetMapping("/download/{*filepath}")
    @ResponseBody
    public ResponseEntity<Resource> downloadDocument(@PathVariable String filepath) {
        try {
            Path fileFullPath = Paths.get(uploadDir, DOCUMENT_SUBDIR, filepath);
            Resource resource = new UrlResource(fileFullPath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                logger.error("Document not found: {}", filepath);
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            logger.error("Error downloading document", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete a document
     */
    @GetMapping("/delete/{*filepath}")
    public String deleteDocument(@PathVariable String filepath, RedirectAttributes redirectAttributes) {
        try {
            Path fileFullPath = Paths.get(uploadDir, DOCUMENT_SUBDIR, filepath);
            String filenameForMessage = Paths.get(filepath).getFileName().toString();

            boolean deleted = Files.deleteIfExists(fileFullPath);

            if (deleted) {
                redirectAttributes.addFlashAttribute("message", "Document deleted successfully: " + filenameForMessage);
            } else {
                redirectAttributes.addFlashAttribute("error", "Document not found or could not be deleted: " + filenameForMessage);
            }
        } catch (IOException e) {
            logger.error("Error deleting document '{}': {}", filepath, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to delete document: " + e.getMessage());
        }

        return "redirect:/documents";
    }
} 