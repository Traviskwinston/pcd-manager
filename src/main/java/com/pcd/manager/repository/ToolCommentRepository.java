package com.pcd.manager.repository;

import com.pcd.manager.model.ToolComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ToolCommentRepository extends JpaRepository<ToolComment, Long> {
    List<ToolComment> findByToolId(Long toolId);
    List<ToolComment> findByToolIdOrderByCreatedDateDesc(Long toolId);
    
    // Bulk loading method for multiple tool IDs
    List<ToolComment> findByToolIdInOrderByCreatedDateDesc(List<Long> toolIds);
    
    /**
     * Lightweight query for tools list view - only loads essential Comment fields
     * Returns: id, createdDate, user.name, content (first 100 chars), tool.id
     */
    @Query("SELECT c.id, c.createdDate, c.user.name, " +
           "CASE WHEN LENGTH(c.content) > 100 THEN CONCAT(SUBSTRING(c.content, 1, 100), '...') ELSE c.content END, " +
           "c.tool.id FROM ToolComment c WHERE c.tool.id IN :toolIds ORDER BY c.createdDate DESC")
    List<Object[]> findCommentListDataByToolIds(List<Long> toolIds);
} 