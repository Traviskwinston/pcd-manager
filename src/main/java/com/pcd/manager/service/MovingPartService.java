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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class MovingPartService {

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
        return movingPartRepository.findAllByToolId(toolId);
    }
    
    @Transactional
    public MovingPart createMovingPart(String partName, Long fromToolId, Long toToolId, String notes, Long noteId, Rma rma) {
        MovingPart movingPart = new MovingPart();
        movingPart.setPartName(partName);
        movingPart.setMoveDate(LocalDateTime.now());
        movingPart.setNotes(notes);
        
        // Set the from tool if provided
        if (fromToolId != null) {
            toolRepository.findById(fromToolId).ifPresent(movingPart::setFromTool);
        }
        
        // Set the to tool if provided
        if (toToolId != null) {
            toolRepository.findById(toToolId).ifPresent(movingPart::setToTool);
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
    public MovingPart createMovingPartWithDestinations(String partName, Long fromToolId, List<Long> destinationToolIds, String notes, Long noteId, Rma rma) {
        MovingPart movingPart = new MovingPart();
        movingPart.setPartName(partName);
        movingPart.setMoveDate(LocalDateTime.now());
        movingPart.setNotes(notes);
        
        // Set the from tool if provided
        if (fromToolId != null) {
            toolRepository.findById(fromToolId).ifPresent(movingPart::setFromTool);
        }
        
        // Set the primary destination (first tool) and destination chain
        if (destinationToolIds != null && !destinationToolIds.isEmpty()) {
            // Set the first destination as the primary toTool
            Long primaryDestinationId = destinationToolIds.get(0);
            toolRepository.findById(primaryDestinationId).ifPresent(movingPart::setToTool);
            
            // Store the full destination chain
            movingPart.setDestinationToolIds(destinationToolIds);
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
    public Optional<MovingPart> updateMovingPart(Long id, String partName, Long fromToolId, Long toToolId, String notes, Rma rma) {
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
            
            // Update toTool if provided
            if (toToolId != null) {
                toolRepository.findById(toToolId).ifPresent(movingPart::setToTool);
            } else {
                movingPart.setToTool(null); // Allow unsetting
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
} 