package com.pcd.manager.repository;

import com.pcd.manager.model.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoteRepository extends JpaRepository<Note, Long> {
    
    /**
     * Find all notes associated with a specific tool
     * @param toolId The ID of the tool
     * @return List of notes for the specified tool, ordered by creation time (newest first)
     */
    List<Note> findByToolIdOrderByCreatedAtDesc(Long toolId);
    
    /**
     * Find all notes created by a specific user
     * @param userId The ID of the user
     * @return List of notes created by the specified user, ordered by creation time (newest first)
     */
    List<Note> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    /**
     * Find notes by both tool and user
     * @param toolId The ID of the tool
     * @param userId The ID of the user
     * @return List of notes for the specified tool and user, ordered by creation time (newest first)
     */
    List<Note> findByToolIdAndUserIdOrderByCreatedAtDesc(Long toolId, Long userId);
    
    /**
     * OPTIMIZATION: Bulk find notes for multiple tools to avoid N+1 queries
     * @param toolIds The list of tool IDs
     * @return List of notes for any of the specified tools, ordered by creation time (newest first)
     */
    List<Note> findByToolIdInOrderByCreatedAtDesc(List<Long> toolIds);
} 