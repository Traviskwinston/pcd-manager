package com.pcd.manager.repository;

import com.pcd.manager.model.TrackTrendComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrackTrendCommentRepository extends JpaRepository<TrackTrendComment, Long> {
    List<TrackTrendComment> findByTrackTrendId(Long trackTrendId);
    List<TrackTrendComment> findByTrackTrendIdOrderByCreatedDateDesc(Long trackTrendId);
} 