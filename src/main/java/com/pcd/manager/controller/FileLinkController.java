package com.pcd.manager.controller;

import com.pcd.manager.service.RmaService;
import com.pcd.manager.service.ToolService;
import com.pcd.manager.service.TrackTrendService;
import com.pcd.manager.service.MovingPartService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/files")
public class FileLinkController {

    private static final Logger logger = LoggerFactory.getLogger(FileLinkController.class);

    private final RmaService rmaService;
    private final ToolService toolService;
    private final TrackTrendService trackTrendService;
    private final MovingPartService movingPartService;

    @Autowired
    public FileLinkController(RmaService rmaService, ToolService toolService, TrackTrendService trackTrendService, MovingPartService movingPartService) {
        this.rmaService = rmaService;
        this.toolService = toolService;
        this.trackTrendService = trackTrendService;
        this.movingPartService = movingPartService;
    }

    @PostMapping("/link-entity")
    public String linkFileToEntity(
            @RequestParam(required = false) String filePath,
            @RequestParam String originalFileName,
            @RequestParam String fileType,
            @RequestParam String sourceEntityType,
            @RequestParam Long sourceEntityId,
            @RequestParam String targetEntityType,
            @RequestParam(required = false) Long targetRmaId,
            @RequestParam(required = false) Long targetToolId,
            @RequestParam(required = false) Long targetTrackTrendId,
            @RequestParam(required = false) Long fileId,
            RedirectAttributes redirectAttributes) {

        logger.info("Request to link item (File Path: {}, File ID: {}, Type: {}, Name: {}) from {} {} to {} (RMA ID: {}, Tool ID: {}, TT ID: {})",
                filePath, fileId, fileType, originalFileName, sourceEntityType, sourceEntityId, 
                targetEntityType, targetRmaId, targetToolId, targetTrackTrendId);

        Long actualTargetId = null;
        String targetTypeDisplay = "";

        if ("RMA".equalsIgnoreCase(targetEntityType) && targetRmaId != null) {
            actualTargetId = targetRmaId;
            targetTypeDisplay = "RMA";
        } else if ("TOOL".equalsIgnoreCase(targetEntityType) && targetToolId != null) {
            actualTargetId = targetToolId;
            targetTypeDisplay = "Tool";
        } else if ("TRACK_TREND".equalsIgnoreCase(targetEntityType) && targetTrackTrendId != null) {
            actualTargetId = targetTrackTrendId;
            targetTypeDisplay = "Track/Trend";
        }

        if (actualTargetId == null) {
            redirectAttributes.addFlashAttribute("error", "Target entity ID not provided for linking to " + targetEntityType);
            return determineRedirectPath(sourceEntityType, sourceEntityId, "/dashboard");
        }

        boolean success = false;
        String errorMessage = "Failed to link item. Service method not yet fully implemented or an unexpected error occurred.";

        try {
            if ("TOOL".equalsIgnoreCase(targetEntityType)) {
                if ("MOVING_PART_RECORD".equals(fileType)) {
                    success = toolService.linkMovingPartToTool(sourceEntityId, actualTargetId);
                } else {
                    success = toolService.linkFileToTool(actualTargetId, filePath, originalFileName, fileType, sourceEntityType, sourceEntityId);
                }
                if (!success) errorMessage = "Failed to link item to Tool.";
            } else if ("RMA".equalsIgnoreCase(targetEntityType)) {
                if ("MOVING_PART_RECORD".equals(fileType)) {
                    success = rmaService.linkMovingPartToRma(sourceEntityId, actualTargetId);
                } else {
                    success = rmaService.linkFileToRma(actualTargetId, filePath, originalFileName, fileType, sourceEntityType, sourceEntityId);
                }
                 if (!success) errorMessage = "Failed to link item to RMA.";
            } else if ("TRACK_TREND".equalsIgnoreCase(targetEntityType)) {
                if ("MOVING_PART_RECORD".equals(fileType)) {
                    success = movingPartService.linkMovingPartToTrackTrend(fileId, actualTargetId);
                } else {
                    success = trackTrendService.linkFileToTrackTrend(actualTargetId, filePath, originalFileName, fileType, sourceEntityType, sourceEntityId);
                }
                if (!success) errorMessage = "Failed to link item to Track/Trend.";
            } else {
                 errorMessage = "Unsupported target entity type: " + targetEntityType;
            }

            if (success) {
                redirectAttributes.addFlashAttribute("message", String.format("Item '%s' linked successfully to %s ID: %d.", originalFileName, targetTypeDisplay, actualTargetId));
            } else {
                redirectAttributes.addFlashAttribute("error", errorMessage);
            }

        } catch (Exception e) {
            logger.error("Error linking item: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error linking item: " + e.getMessage());
        }

        return determineRedirectPath(sourceEntityType, sourceEntityId, "/dashboard");
    }

    private String determineRedirectPath(String entityType, Long entityId, String fallbackPath) {
        if (entityType == null || entityId == null) {
            return fallbackPath;
        }
        switch (entityType.toUpperCase()) {
            case "RMA":
                return "redirect:/rma/" + entityId;
            case "TOOL":
                return "redirect:/tools/" + entityId;
            default:
                return fallbackPath;
        }
    }
} 