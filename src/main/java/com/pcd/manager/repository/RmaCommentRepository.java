package com.pcd.manager.repository;

import com.pcd.manager.model.RmaComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RmaCommentRepository extends JpaRepository<RmaComment, Long> {
    List<RmaComment> findByRmaId(Long rmaId);
    List<RmaComment> findByRmaIdOrderByCreatedDateDesc(Long rmaId);
} 