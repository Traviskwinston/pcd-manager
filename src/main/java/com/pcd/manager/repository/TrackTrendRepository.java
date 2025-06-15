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
    
    // Bulk loading method for multiple tool IDs
    List<TrackTrend> findByAffectedToolsIdIn(List<Long> toolIds);
    
    // Bulk loading method for multiple tool IDs with eager loading of affectedTools
    @Query("SELECT DISTINCT tt FROM TrackTrend tt LEFT JOIN FETCH tt.affectedTools WHERE tt.id IN (SELECT DISTINCT tt2.id FROM TrackTrend tt2 JOIN tt2.affectedTools t WHERE t.id IN :toolIds)")
    List<TrackTrend> findByAffectedToolsIdInWithAffectedTools(List<Long> toolIds);
    
    // Find track trends related to the specified track trend
    List<TrackTrend> findByRelatedTrackTrendsId(Long trackTrendId);
    
    // Find all track trends and eagerly load affected tools
    @Query("SELECT DISTINCT tt FROM TrackTrend tt LEFT JOIN FETCH tt.affectedTools")
    List<TrackTrend> findAllWithAffectedTools();
} 