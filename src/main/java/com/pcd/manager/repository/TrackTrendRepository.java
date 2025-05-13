package com.pcd.manager.repository;

import com.pcd.manager.model.TrackTrend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrackTrendRepository extends JpaRepository<TrackTrend, Long> {
    // additional query methods can be added here
    List<TrackTrend> findByAffectedToolsId(Long toolId);
    
    // Find track trends related to the specified track trend
    List<TrackTrend> findByRelatedTrackTrendsId(Long trackTrendId);
    
    // Find all track trends and eagerly load affected tools
    @Query("SELECT DISTINCT tt FROM TrackTrend tt LEFT JOIN FETCH tt.affectedTools")
    List<TrackTrend> findAllWithAffectedTools();
} 