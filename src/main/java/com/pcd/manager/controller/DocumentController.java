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
            // Ensure the directory exists
            Path documentsPath = Paths.get(uploadDir, DOCUMENT_SUBDIR);
            if (!Files.exists(documentsPath)) {
                Files.createDirectories(documentsPath);
                logger.info("Created reference documents directory: {}", documentsPath);
            }

            // Get all files in the directory
            List<String> documents = new ArrayList<>();
            if (Files.exists(documentsPath)) {
                documents = Files.list(documentsPath)
                        .filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .collect(Collectors.toList());
            }

            model.addAttribute("documents", documents);
            return "documents/list";
        } catch (IOException e) {
            logger.error("Error listing documents", e);
            model.addAttribute("error", "Failed to list documents: " + e.getMessage());
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
    @GetMapping("/download/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> downloadDocument(@PathVariable String filename) {
        try {
            Path filePath = Paths.get(uploadDir, DOCUMENT_SUBDIR, filename);
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                logger.error("Document not found: {}", filename);
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
    @GetMapping("/delete/{filename:.+}")
    public String deleteDocument(@PathVariable String filename, RedirectAttributes redirectAttributes) {
        try {
            Path filePath = Paths.get(uploadDir, DOCUMENT_SUBDIR, filename);
            boolean deleted = Files.deleteIfExists(filePath);

            if (deleted) {
                redirectAttributes.addFlashAttribute("message", "Document deleted successfully: " + filename);
            } else {
                redirectAttributes.addFlashAttribute("error", "Document not found: " + filename);
            }
        } catch (IOException e) {
            logger.error("Error deleting document", e);
            redirectAttributes.addFlashAttribute("error", "Failed to delete document: " + e.getMessage());
        }

        return "redirect:/documents";
    }
} 