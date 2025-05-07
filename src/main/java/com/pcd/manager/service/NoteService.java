package com.pcd.manager.service;

import com.pcd.manager.model.Note;
import com.pcd.manager.model.Tool;
import com.pcd.manager.model.User;
import com.pcd.manager.repository.NoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class NoteService {
    
    private static final Logger logger = LoggerFactory.getLogger(NoteService.class);
    
    private final NoteRepository noteRepository;
    
    @Autowired
    public NoteService(NoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }
    
    /**
     * Get all notes
     */
    public List<Note> getAllNotes() {
        return noteRepository.findAll();
    }
    
    /**
     * Get a note by ID
     */
    public Optional<Note> getNoteById(Long id) {
        return noteRepository.findById(id);
    }
    
    /**
     * Get all notes for a specific tool
     */
    public List<Note> getNotesByToolId(Long toolId) {
        logger.debug("Getting notes for tool ID: {}", toolId);
        return noteRepository.findByToolIdOrderByCreatedAtDesc(toolId);
    }
    
    /**
     * Get all notes created by a specific user
     */
    public List<Note> getNotesByUserId(Long userId) {
        return noteRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
    
    /**
     * Get notes for a specific tool created by a specific user
     */
    public List<Note> getNotesByToolAndUser(Long toolId, Long userId) {
        return noteRepository.findByToolIdAndUserIdOrderByCreatedAtDesc(toolId, userId);
    }
    
    /**
     * Save a note
     */
    @Transactional
    public Note saveNote(Note note) {
        logger.debug("Saving note for tool ID: {} by user ID: {}", 
                    note.getTool() != null ? note.getTool().getId() : "null", 
                    note.getUser() != null ? note.getUser().getId() : "null");
        return noteRepository.save(note);
    }
    
    /**
     * Create a new note
     */
    @Transactional
    public Note createNote(String content, Tool tool, User user) {
        logger.debug("Creating new note for tool ID: {} by user ID: {}", 
                    tool != null ? tool.getId() : "null", 
                    user != null ? user.getId() : "null");
        
        Note note = new Note(content, tool, user);
        return noteRepository.save(note);
    }
    
    /**
     * Delete a note
     */
    @Transactional
    public void deleteNote(Long id) {
        logger.debug("Deleting note ID: {}", id);
        noteRepository.deleteById(id);
    }
} 