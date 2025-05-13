package com.pcd.manager.repository;

import com.pcd.manager.model.MovingPart;
import com.pcd.manager.model.Tool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovingPartRepository extends JpaRepository<MovingPart, Long> {
    
    List<MovingPart> findByFromToolOrToToolOrderByMoveDateDesc(Tool fromTool, Tool toTool);
    
    @Query("SELECT mp FROM MovingPart mp WHERE mp.fromTool.id = :toolId OR mp.toTool.id = :toolId ORDER BY mp.moveDate DESC")
    List<MovingPart> findAllByToolId(Long toolId);
    
    List<MovingPart> findByRmaId(Long rmaId);
} 