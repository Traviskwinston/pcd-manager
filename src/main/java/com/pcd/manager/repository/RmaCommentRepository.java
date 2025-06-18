package com.pcd.manager.repository;

import com.pcd.manager.model.RmaComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RmaCommentRepository extends JpaRepository<RmaComment, Long> {
    List<RmaComment> findByRmaId(Long rmaId);
    List<RmaComment> findByRmaIdOrderByCreatedDateDesc(Long rmaId);
    
    /**
     * Lightweight bulk query for RMA list view - only loads essential comment data
     * Returns: rmaId, commentCount
     */
    @Query("SELECT rc.rma.id, COUNT(rc.id) FROM RmaComment rc WHERE rc.rma.id IN :rmaIds GROUP BY rc.rma.id")
    List<Object[]> findCommentCountsByRmaIds(@Param("rmaIds") List<Long> rmaIds);
    
    /**
     * Bulk query for RMA list view tooltips - loads comment content for tooltips
     * Returns: rmaId, content, createdDate, user.name
     */
    @Query("SELECT rc.rma.id, rc.content, rc.createdDate, rc.user.name FROM RmaComment rc WHERE rc.rma.id IN :rmaIds ORDER BY rc.rma.id, rc.createdDate DESC")
    List<Object[]> findCommentContentByRmaIds(@Param("rmaIds") List<Long> rmaIds);
} 