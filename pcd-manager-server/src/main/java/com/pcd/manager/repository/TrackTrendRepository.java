package com.pcd.manager.repository;

import com.pcd.manager.model.TrackTrend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrackTrendRepository extends JpaRepository<TrackTrend, Long> {
    // additional query methods can be added here
    List<TrackTrend> findByAffectedToolsId(Long toolId);
} 