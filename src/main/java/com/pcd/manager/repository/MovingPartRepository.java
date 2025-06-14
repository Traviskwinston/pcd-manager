package com.pcd.manager.repository;

import com.pcd.manager.model.MovingPart;
import com.pcd.manager.model.Tool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovingPartRepository extends JpaRepository<MovingPart, Long> {
    
    List<MovingPart> findByFromToolOrderByMoveDateDesc(Tool fromTool);
    
    @Query("SELECT mp FROM MovingPart mp WHERE mp.fromTool.id = :toolId OR " +
           "(mp.destinationChain IS NOT NULL AND " +
           "(mp.destinationChain LIKE CONCAT('[', :toolId, ']') OR " +
           "mp.destinationChain LIKE CONCAT('[', :toolId, ',%') OR " +
           "mp.destinationChain LIKE CONCAT('%,', :toolId, ',%') OR " +
           "mp.destinationChain LIKE CONCAT('%,', :toolId, ']'))) " +
           "ORDER BY mp.moveDate DESC")
    List<MovingPart> findAllByToolId(Long toolId);
    
    @Query("SELECT mp FROM MovingPart mp WHERE mp.fromTool = :tool OR " +
           "(mp.destinationChain IS NOT NULL AND " +
           "(mp.destinationChain LIKE CONCAT('[', :#{#tool.id}, ']') OR " +
           "mp.destinationChain LIKE CONCAT('[', :#{#tool.id}, ',%') OR " +
           "mp.destinationChain LIKE CONCAT('%,', :#{#tool.id}, ',%') OR " +
           "mp.destinationChain LIKE CONCAT('%,', :#{#tool.id}, ']'))) " +
           "ORDER BY mp.moveDate DESC")
    List<MovingPart> findAllByTool(Tool tool);
    
    List<MovingPart> findByRmaId(Long rmaId);
} 