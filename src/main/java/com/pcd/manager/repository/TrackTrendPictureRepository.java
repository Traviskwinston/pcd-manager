package com.pcd.manager.repository;

import com.pcd.manager.model.TrackTrendPicture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrackTrendPictureRepository extends JpaRepository<TrackTrendPicture, Long> {
    List<TrackTrendPicture> findByTrackTrendId(Long trackTrendId);
} 