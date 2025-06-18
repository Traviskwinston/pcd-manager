package com.pcd.manager.repository;

import com.pcd.manager.model.Passdown;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PassdownRepository extends JpaRepository<Passdown, Long> {
    
    List<Passdown> findAllByOrderByDateDesc();
    
    List<Passdown> findByDateOrderByDateDesc(LocalDate date);
    
    List<Passdown> findByDateBetweenOrderByDateDesc(LocalDate startDate, LocalDate endDate);
    
    /**
     * Find passdowns between dates with user and tool eagerly loaded to avoid lazy initialization
     */
    @Query("SELECT DISTINCT p FROM Passdown p " +
           "LEFT JOIN FETCH p.user " +
           "LEFT JOIN FETCH p.tool " +
           "WHERE p.date BETWEEN :startDate AND :endDate " +
           "ORDER BY p.date DESC")
    List<Passdown> findByDateBetweenWithUserAndToolOrderByDateDesc(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    List<Passdown> findByToolIdOrderByDateDesc(Long toolId);
    
    /**
     * OPTIMIZATION: Bulk find passdowns for multiple tools to avoid N+1 queries
     */
    List<Passdown> findByToolIdInOrderByDateDesc(List<Long> toolIds);
    
    /**
     * Lightweight query for tools list view - only loads essential Passdown fields
     * Returns: id, date, user.name, comment (first 100 chars), tool.id
     */
    @Query("SELECT p.id, p.date, p.user.name, " +
           "CASE WHEN LENGTH(p.comment) > 100 THEN CONCAT(SUBSTRING(p.comment, 1, 100), '...') ELSE p.comment END, " +
           "p.tool.id FROM Passdown p WHERE p.tool.id IN :toolIds ORDER BY p.date DESC")
    List<Object[]> findPassdownListDataByToolIds(@Param("toolIds") List<Long> toolIds);
} 