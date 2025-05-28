package com.pcd.manager.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.ArrayList;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import java.net.MalformedURLException;
import jakarta.annotation.PostConstruct;
import java.io.FileNotFoundException;
import java.util.Map;
import java.util.HashMap;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Arrays;
import com.pcd.manager.model.Rma;
import com.pcd.manager.model.RmaPicture;
import com.pcd.manager.model.RmaDocument;
import org.springframework.http.ResponseEntity;

@Component
public class UploadUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(UploadUtils.class);
    
    // List of allowed file extensions
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(
        "pdf", "doc", "docx", "xls", "xlsx", "txt", "csv", 
        "jpg", "jpeg", "png", "gif", "bmp", "webp"
    );
    
    // List of allowed MIME types
    private static final List<String> ALLOWED_MIME_TYPES = Arrays.asList(
        "application/pdf", 
        "application/msword", 
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel", 
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 
        "text/plain", "text/csv",
        "image/jpeg", "image/png", "image/gif", "image/bmp", "image/webp"
    );
    
    // List of image file extensions
    private static final List<String> IMAGE_EXTENSIONS = Arrays.asList(
        "jpg", "jpeg", "png", "gif", "bmp", "webp"
    );

    // List of image MIME types
    private static final List<String> IMAGE_MIME_TYPES = Arrays.asList(
        "image/jpeg", "image/png", "image/gif", "image/bmp", "image/webp"
    );
    
    // List of Excel file extensions and MIME types
    private static final List<String> EXCEL_EXTENSIONS = Arrays.asList("xls", "xlsx");
    private static final List<String> EXCEL_MIME_TYPES = Arrays.asList(
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );
    
    @Value("${app.upload.dir:${user.home}/uploads}")
    private String uploadDir;
    
    private Path baseUploadPath;
    
    @Value("${upload.max-file-size:10485760}")
    private long maxFileSize; // Default 10MB

    @PostConstruct
    public void init() {
        this.baseUploadPath = Paths.get(uploadDir);
        initializeDirectories();
    }
    
    /**
     * Initializes upload directories
     */
    public void initializeDirectories() {
        try {
            if (!Files.exists(baseUploadPath)) {
                Files.createDirectories(baseUploadPath);
                logger.info("Created base upload directory: {}", baseUploadPath);
            }
            
            // Create subdirectories for different file types
            String[] subdirs = {"pictures", "documents", "rma-pictures", "rma-documents", "reference-documents"};
            for (String subdir : subdirs) {
                Path subdirPath = baseUploadPath.resolve(subdir);
                if (!Files.exists(subdirPath)) {
                    Files.createDirectories(subdirPath);
                    logger.info("Created subdirectory: {}", subdirPath);
                }
            }
        } catch (IOException e) {
            logger.error("Could not initialize upload directories", e);
            throw new RuntimeException("Could not initialize upload directories", e);
        }
    }
    
    /**
     * Ensures the upload directory exists
     */
    public void ensureUploadDirectoryExists() {
        File directory = new File(uploadDir);
        if (!directory.exists()) {
            boolean created = directory.mkdirs();
            if (created) {
                logger.info("Created upload directory: {}", directory.getAbsolutePath());
            } else {
                logger.warn("Failed to create upload directory: {}", directory.getAbsolutePath());
            }
        }
    }
    
    /**
     * Validates the file type and size
     * 
     * @param file The file to validate
     * @return True if the file is valid, false otherwise
     */
    public boolean validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            logger.warn("File is null or empty");
            return false;
        }
        
        // Check file size
        if (file.getSize() > maxFileSize) {
            logger.warn("File size {} exceeds maximum allowed size {}", file.getSize(), maxFileSize);
            return false;
        }
        
        // Check content type and extension
        String contentType = file.getContentType();
        String originalFilename = file.getOriginalFilename();
        
        if (originalFilename == null) {
            logger.warn("Original filename is null");
            return false;
        }
        
        String extension = "";
        int lastDotIndex = originalFilename.lastIndexOf('.');
        if (lastDotIndex > 0) {
            extension = originalFilename.substring(lastDotIndex + 1).toLowerCase();
        }
        
        // Log file details for debugging
        logger.info("Validating file: name={}, type={}, extension={}", originalFilename, contentType, extension);
        
        // Check if it's a valid file type
        boolean isValidContentType = contentType != null && ALLOWED_MIME_TYPES.contains(contentType.toLowerCase());
        boolean isValidExtension = ALLOWED_EXTENSIONS.contains(extension);
        
        // Special handling for Excel files
        boolean isExcelFile = (contentType != null && EXCEL_MIME_TYPES.contains(contentType.toLowerCase())) ||
                            EXCEL_EXTENSIONS.contains(extension);
                            
        if (isExcelFile) {
            logger.info("Validating Excel file: {}", originalFilename);
        }
        
        if (!isValidContentType && !isValidExtension) {
            logger.warn("Invalid content type: {} and extension: {}", contentType, extension);
            return false;
        }
        
        return true;
    }
    
    /**
     * Saves a file to the upload directory
     * 
     * @param file The file to save
     * @param subdirectory Optional subdirectory
     * @return The path where the file was saved, or null if saving failed
     * @throws IOException If an I/O error occurs
     */
    public String saveFile(MultipartFile file, String subdirectory) throws IOException {
        if (file == null || file.isEmpty()) {
            logger.warn("Cannot save null or empty file");
            return null;
        }
        
        // Validate the file before saving
        if (!validateFile(file)) {
            logger.warn("File validation failed for {}", file.getOriginalFilename());
            return null;
        }
        
        // Create subdirectory if provided
        String targetDir = uploadDir;
        if (subdirectory != null && !subdirectory.isEmpty()) {
            targetDir = targetDir + File.separator + subdirectory;
            File subDir = new File(targetDir);
            if (!subDir.exists()) {
                boolean created = subDir.mkdirs();
                if (!created) {
                    logger.warn("Failed to create subdirectory: {}", subDir.getAbsolutePath());
                }
            }
        }
        
        // Create year/month based directory structure
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM");
        String datePath = dateFormat.format(new Date());
        targetDir = targetDir + File.separator + datePath;
        
        File dateDir = new File(targetDir);
        if (!dateDir.exists()) {
            boolean created = dateDir.mkdirs();
            if (!created) {
                logger.warn("Failed to create date directory: {}", dateDir.getAbsolutePath());
            }
        }
        
        // Generate a unique filename
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String uniqueFilename = UUID.randomUUID().toString() + extension;
        
        // Save the file
        Path targetPath = Paths.get(targetDir, uniqueFilename);
        logger.info("Saving file to: {}", targetPath);
        
        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            // Return the path relative to the upload directory
            String relativePath = datePath + File.separator + uniqueFilename;
            if (subdirectory != null && !subdirectory.isEmpty()) {
                relativePath = subdirectory + File.separator + relativePath;
            }
            logger.info("File saved successfully. Relative path: {}", relativePath);
            return relativePath;
        } catch (IOException e) {
            logger.error("Failed to save file: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Checks if a file exists at the given path
     * 
     * @param filePath The path to check
     * @return True if the file exists, false otherwise
     */
    public boolean fileExists(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }
        
        // Try with the path as provided
        File file = new File(filePath);
        if (file.exists() && file.isFile()) {
            return true;
        }
        
        // If it's a relative path, try with the upload directory
        if (!file.isAbsolute()) {
            file = new File(uploadDir, filePath);
            return file.exists() && file.isFile();
        }
        
        return false;
    }
    
    /**
     * Deletes a file at the given path
     * 
     * @param filePath The path of the file to delete
     * @return True if the file was successfully deleted, false otherwise
     */
    public boolean deleteFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            logger.warn("Cannot delete file: path is null or empty");
            return false;
        }
        
        try {
            // Try with the path as provided
            File file = new File(filePath);
            
            // If it's not absolute or doesn't exist, try with the upload directory
            if (!file.isAbsolute() || !file.exists()) {
                String fullPath = uploadDir + File.separator + filePath.replace('/', File.separatorChar);
                file = new File(fullPath);
                logger.info("Using full path for deletion: {}", fullPath);
            }
            
            if (file.exists() && file.isFile()) {
                boolean deleted = file.delete();
                if (deleted) {
                    logger.info("File deleted successfully: {}", file.getAbsolutePath());
                } else {
                    logger.warn("Failed to delete file: {}", file.getAbsolutePath());
                }
                return deleted;
            } else {
                logger.warn("Cannot delete file: file does not exist or is not a file: {}", file.getAbsolutePath());
                return false;
            }
        } catch (Exception e) {
            logger.error("Error deleting file {}: {}", filePath, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Gets the maximum allowed file size
     * 
     * @return The maximum allowed file size in bytes
     */
    public long getMaxFileSize() {
        return maxFileSize;
    }
    
    /**
     * Gets the root upload directory
     * 
     * @return The upload directory
     */
    public String getUploadDir() {
        return this.uploadDir;
    }
    
    /**
     * Create the upload directory if it doesn't exist and return status information
     * 
     * @return Map containing information about the directory status
     */
    public Map<String, String> ensureUploadDirectoryExistsStatus() {
        Map<String, String> status = new HashMap<>();
        try {
            // Add the upload directory path to the status
            status.put("uploadDir", this.uploadDir);
            status.put("baseUploadPath", this.baseUploadPath.toString());
            
            // Check if the directory exists
            boolean baseExists = Files.exists(this.baseUploadPath);
            status.put("baseExists", String.valueOf(baseExists));
            
            // Create it if it doesn't exist
            if (!baseExists) {
                Files.createDirectories(this.baseUploadPath);
                status.put("baseCreated", "true");
                baseExists = true;
            }
            
            // Check and create subdirectories
            String[] subdirs = {"pictures", "documents", "rma-pictures", "rma-documents"};
            for (String subdir : subdirs) {
                Path subdirPath = this.baseUploadPath.resolve(subdir);
                boolean subdirExists = Files.exists(subdirPath);
                status.put(subdir + "Exists", String.valueOf(subdirExists));
                
                if (!subdirExists) {
                    Files.createDirectories(subdirPath);
                    status.put(subdir + "Created", "true");
                }
            }
            
            // Check if directories are writable
            status.put("baseWritable", String.valueOf(Files.isWritable(this.baseUploadPath)));
            for (String subdir : subdirs) {
                Path subdirPath = this.baseUploadPath.resolve(subdir);
                status.put(subdir + "Writable", String.valueOf(Files.isWritable(subdirPath)));
            }
            
            status.put("success", "true");
        } catch (Exception e) {
            logger.error("Error ensuring upload directories exist", e);
            status.put("success", "false");
            status.put("error", e.getMessage());
        }
        
        return status;
    }

    /**
     * Check if a file is an image based on its name/extension
     * 
     * @param fileName The name of the file to check
     * @return true if the file is an image, false otherwise
     */
    public boolean isImageFile(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }
        
        String extension = "";
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            extension = fileName.substring(lastDotIndex + 1).toLowerCase();
        }
        
        return IMAGE_EXTENSIONS.contains(extension);
    }

    /**
     * Save a picture file and create an RmaPicture entity
     * 
     * @param rma The RMA to associate the picture with
     * @param file The picture file to save
     * @return The created RmaPicture entity, or null if saving failed
     * @throws IOException If an I/O error occurs
     */
    public RmaPicture saveRmaPicture(Rma rma, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty() || rma == null) {
            logger.warn("Cannot save picture: file or RMA is null/empty");
            return null;
        }
        
        // Save the file to disk
        String filePath = saveFile(file, "rma-pictures");
        if (filePath == null) {
            logger.warn("Failed to save picture file to disk");
            return null;
        }
        
        // Create and return the RmaPicture entity
        RmaPicture picture = new RmaPicture();
        picture.setRma(rma);
        picture.setFileName(file.getOriginalFilename());
        picture.setFilePath(filePath);
        picture.setFileType(file.getContentType());
        picture.setFileSize(file.getSize());
        
        return picture;
    }

    /**
     * Save a document file and create an RmaDocument entity
     * 
     * @param rma The RMA to associate the document with
     * @param file The document file to save
     * @return The created RmaDocument entity, or null if saving failed
     * @throws IOException If an I/O error occurs
     */
    public RmaDocument saveRmaDocument(Rma rma, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty() || rma == null) {
            logger.warn("Cannot save document: file or RMA is null/empty");
            return null;
        }
        
        // Save the file to disk
        String filePath = saveFile(file, "rma-documents");
        if (filePath == null) {
            logger.warn("Failed to save document file to disk");
            return null;
        }
        
        // Create and return the RmaDocument entity
        RmaDocument document = new RmaDocument();
        document.setRma(rma);
        document.setFileName(file.getOriginalFilename());
        document.setFilePath(filePath);
        document.setFileType(file.getContentType());
        document.setFileSize(file.getSize());
        
        return document;
    }

    /**
     * Serves a file from the upload directory
     * 
     * @param filePath The path to the file relative to the upload directory
     * @param contentType The content type of the file
     * @return ResponseEntity containing the file
     * @throws IOException If an I/O error occurs
     */
    public ResponseEntity<?> serveFile(String filePath, String contentType) throws IOException {
        try {
            Path file = baseUploadPath.resolve(filePath);
            Resource resource = new UrlResource(file.toUri());
            
            if (resource.exists() || resource.isReadable()) {
                return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, 
                           "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
            } else {
                throw new FileNotFoundException("Could not read file: " + filePath);
            }
        } catch (MalformedURLException e) {
            throw new FileNotFoundException("Could not read file: " + filePath);
        }
    }
} 