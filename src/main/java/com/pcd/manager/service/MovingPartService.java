package com.pcd.manager.service;

import com.pcd.manager.model.MovingPart;
import com.pcd.manager.model.Note;
import com.pcd.manager.model.Tool;
import com.pcd.manager.repository.MovingPartRepository;
import com.pcd.manager.repository.NoteRepository;
import com.pcd.manager.repository.ToolRepository;
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
    
    public List<MovingPart> getAllMovingParts() {
        return movingPartRepository.findAll();
    }
    
    public List<MovingPart> getMovingPartsByTool(Tool tool) {
        return movingPartRepository.findByFromToolOrToToolOrderByMoveDateDesc(tool, tool);
    }
    
    public List<MovingPart> getMovingPartsByToolId(Long toolId) {
        return movingPartRepository.findAllByToolId(toolId);
    }
    
    @Transactional
    public MovingPart createMovingPart(String partName, Long fromToolId, Long toToolId, String notes, Long noteId) {
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
    public Optional<MovingPart> updateMovingPart(Long id, String partName, Long fromToolId, Long toToolId, String notes) {
        Optional<MovingPart> movingPartOpt = movingPartRepository.findById(id);
        
        if (movingPartOpt.isPresent()) {
            MovingPart movingPart = movingPartOpt.get();
            movingPart.setPartName(partName);
            movingPart.setNotes(notes);
            
            // Update fromTool if provided
            if (fromToolId != null) {
                toolRepository.findById(fromToolId).ifPresent(movingPart::setFromTool);
            }
            
            // Update toTool if provided
            if (toToolId != null) {
                toolRepository.findById(toToolId).ifPresent(movingPart::setToTool);
            }
            
            return Optional.of(movingPartRepository.save(movingPart));
        }
        
        return Optional.empty();
    }
} 