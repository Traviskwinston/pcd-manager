package com.pcd.manager.service;

import com.pcd.manager.model.MovingPart;
import com.pcd.manager.model.Note;
import com.pcd.manager.model.Tool;
import com.pcd.manager.model.Rma;
import com.pcd.manager.model.TrackTrend;
import com.pcd.manager.repository.MovingPartRepository;
import com.pcd.manager.repository.NoteRepository;
import com.pcd.manager.repository.ToolRepository;
import com.pcd.manager.repository.TrackTrendRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

@Service
public class MovingPartService {

    private static final Logger logger = LoggerFactory.getLogger(MovingPartService.class);

    @Autowired
    private MovingPartRepository movingPartRepository;
    
    @Autowired
    private ToolRepository toolRepository;
    
    @Autowired
    private NoteRepository noteRepository;
    
    @Autowired
    private TrackTrendRepository trackTrendRepository;
    
    public List<MovingPart> getAllMovingParts() {
        return movingPartRepository.findAll();
    }
    
    public List<MovingPart> getMovingPartsByTool(Tool tool) {
        return movingPartRepository.findAllByTool(tool);
    }
    
    public List<MovingPart> getMovingPartsByToolId(Long toolId) {
        logger.debug("Searching for moving parts for tool ID: {}", toolId);
        List<MovingPart> results = movingPartRepository.findAllByToolId(toolId);
        logger.debug("Found {} moving parts for tool ID: {}", results.size(), toolId);
        return results;
    }
    
    /**
     * OPTIMIZATION: Bulk gets moving parts for multiple tools to avoid N+1 queries
     * @param toolIds The list of tool IDs
     * @return List of moving parts for the specified tools
     */
    public List<MovingPart> getMovingPartsByToolIds(List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        logger.debug("Bulk searching for moving parts for {} tool IDs", toolIds.size());
        List<MovingPart> results = movingPartRepository.findAllByToolIds(toolIds);
        logger.debug("Found {} moving parts for {} tool IDs", results.size(), toolIds.size());
        return results;
    }
    
    @Transactional
    public MovingPart createMovingPart(String partName, Long fromToolId, List<Long> destinationToolIds, String notes, Long noteId, Rma rma) {
        return createMovingPart(partName, fromToolId, null, destinationToolIds, null, notes, noteId, rma);
    }
    
    public MovingPart createMovingPart(String partName, Long fromToolId, String fromCustomLocation, 
                                      List<Long> destinationToolIds, List<String> toCustomLocations, 
                                      String notes, Long noteId, Rma rma) {
        MovingPart movingPart = new MovingPart();
        movingPart.setPartName(partName);
        movingPart.setMoveDate(LocalDateTime.now());
        movingPart.setNotes(notes);
        
        // Set the from tool or custom location
        if (fromToolId != null) {
            toolRepository.findById(fromToolId).ifPresent(movingPart::setFromTool);
        } else if (fromCustomLocation != null && !fromCustomLocation.trim().isEmpty()) {
            movingPart.setFromCustomLocation(fromCustomLocation.trim());
        }
        
        // Set the destination chain (tool IDs)
        if (destinationToolIds != null && !destinationToolIds.isEmpty()) {
            movingPart.setDestinationToolIds(destinationToolIds);
        }
        
        // Set the custom location chain
        if (toCustomLocations != null && !toCustomLocations.isEmpty()) {
            movingPart.setToCustomLocationsList(toCustomLocations);
        }
        
        // Link note if provided
        if (noteId != null) {
            noteRepository.findById(noteId).ifPresent(movingPart::setLinkedNote);
        }

        // Set the RMA if provided
        if (rma != null) {
            movingPart.setRma(rma);
        }
        
        return movingPartRepository.save(movingPart);
    }
    
    @Transactional
    public Optional<MovingPart> linkNoteToMovingPart(Long movingPartId, Long noteId) {
        Optional<MovingPart> movingPartOpt = movingPartRepository.findById(movingPartId);
        Optional<Note> noteOpt = noteRepository.findById(noteId);
        
        if (movingPartOpt.isPresent() && noteOpt.isPresent()) {
            MovingPart movingPart = movingPartOpt.get();
            movingPart.setLinkedNote(noteOpt.get());
            return Optional.of(movingPartRepository.save(movingPart));
        }
        
        return Optional.empty();
    }
    
    public Optional<MovingPart> getMovingPartById(Long id) {
        return movingPartRepository.findById(id);
    }
    
    @Transactional
    public void deleteMovingPart(Long id) {
        movingPartRepository.deleteById(id);
    }
    
    @Transactional
    public Optional<MovingPart> updateMovingPart(Long id, String partName, Long fromToolId, List<Long> destinationToolIds, String notes, Rma rma) {
        Optional<MovingPart> movingPartOpt = movingPartRepository.findById(id);
        
        if (movingPartOpt.isPresent()) {
            MovingPart movingPart = movingPartOpt.get();
            movingPart.setPartName(partName);
            movingPart.setNotes(notes);
            
            // Update fromTool if provided
            if (fromToolId != null) {
                toolRepository.findById(fromToolId).ifPresent(movingPart::setFromTool);
            } else {
                movingPart.setFromTool(null); // Allow unsetting
            }
            
            // Update destination chain
            if (destinationToolIds != null && !destinationToolIds.isEmpty()) {
                movingPart.setDestinationToolIds(destinationToolIds);
            } else {
                movingPart.setDestinationChain(null); // Clear chain if no destinations
            }

            // Update Rma if provided (could be null to unset)
            movingPart.setRma(rma);
            
            return Optional.of(movingPartRepository.save(movingPart));
        }
        
        return Optional.empty();
    }
    
    public List<MovingPart> getMovingPartsByRmaId(Long rmaId) {
        return movingPartRepository.findByRmaId(rmaId);
    }
    
    /**
     * OPTIMIZATION: Bulk gets moving parts for multiple RMAs to avoid N+1 queries
     * @param rmaIds The list of RMA IDs
     * @return List of moving parts for the specified RMAs
     */
    public List<MovingPart> getMovingPartsByRmaIds(List<Long> rmaIds) {
        if (rmaIds == null || rmaIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        logger.debug("Bulk searching for moving parts for {} RMA IDs", rmaIds.size());
        List<MovingPart> results = movingPartRepository.findByRmaIdIn(rmaIds);
        logger.debug("Found {} moving parts for {} RMA IDs", results.size(), rmaIds.size());
        return results;
    }

    @Transactional
    public boolean linkMovingPartToTrackTrend(Long movingPartId, Long trackTrendId) {
        Optional<MovingPart> movingPartOpt = movingPartRepository.findById(movingPartId);
        Optional<TrackTrend> trackTrendOpt = trackTrendRepository.findById(trackTrendId);

        if (movingPartOpt.isPresent() && trackTrendOpt.isPresent()) {
            MovingPart movingPart = movingPartOpt.get();
            TrackTrend trackTrend = trackTrendOpt.get();
            movingPart.setLinkedTrackTrend(trackTrend);
            movingPartRepository.save(movingPart);
            return true;
        }
        return false;
    }
    
    @Transactional
    public MovingPart save(MovingPart movingPart) {
        return movingPartRepository.save(movingPart);
    }

    @Transactional
    public List<MovingPart> saveAll(List<MovingPart> movingParts) {
        return movingPartRepository.saveAll(movingParts);
    }
    
    /**
     * Get the destination chain for a moving part
     * @param movingPartId The ID of the moving part
     * @return List of tools in the destination chain, or empty list if none
     */
    public List<Tool> getDestinationChainForMovingPart(Long movingPartId) {
        Optional<MovingPart> movingPartOpt = movingPartRepository.findById(movingPartId);
        if (movingPartOpt.isPresent()) {
            MovingPart movingPart = movingPartOpt.get();
            List<Long> destinationIds = movingPart.getDestinationToolIds();
            if (destinationIds != null && !destinationIds.isEmpty()) {
                return toolRepository.findAllById(destinationIds);
            }
        }
        return List.of();
    }

    /**
     * Add a new destination to an existing moving part's chain
     * @param movingPartId The ID of the moving part
     * @param newDestinationToolId The ID of the new destination tool
     * @return Updated MovingPart if successful, empty if not found
     */
    @Transactional
    public Optional<MovingPart> addDestinationToMovingPart(Long movingPartId, Long newDestinationToolId) {
        Optional<MovingPart> movingPartOpt = movingPartRepository.findById(movingPartId);
        if (movingPartOpt.isPresent()) {
            MovingPart movingPart = movingPartOpt.get();
            movingPart.addDestination(newDestinationToolId);
            return Optional.of(movingPartRepository.save(movingPart));
        }
        return Optional.empty();
    }

    /**
     * Get a formatted movement path string for display (e.g., "Tool A → Tool B → Tool C")
     * @param movingPart The moving part
     * @return Formatted movement path string
     */
    public String getFormattedMovementPath(MovingPart movingPart) {
        StringBuilder path = new StringBuilder();
        
        // Add from tool
        if (movingPart.getFromTool() != null) {
            path.append(movingPart.getFromTool().getName());
        } else {
            path.append("Unknown");
        }
        
        // Add destination chain
        List<Long> destinationIds = movingPart.getDestinationToolIds();
        if (destinationIds != null && !destinationIds.isEmpty()) {
            List<Tool> destinationTools = toolRepository.findAllById(destinationIds);
            for (Tool tool : destinationTools) {
                path.append(" → ").append(tool.getName());
            }
        }
        
        return path.toString();
    }
} 