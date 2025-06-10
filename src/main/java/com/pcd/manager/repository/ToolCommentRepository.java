package com.pcd.manager.repository;

import com.pcd.manager.model.ToolComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ToolCommentRepository extends JpaRepository<ToolComment, Long> {
    List<ToolComment> findByToolId(Long toolId);
    List<ToolComment> findByToolIdOrderByCreatedDateDesc(Long toolId);
} 