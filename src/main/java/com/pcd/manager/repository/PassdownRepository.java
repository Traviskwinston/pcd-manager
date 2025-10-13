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
    
    /**
     * Find all passdowns with user, tools, and assignedTechs eagerly loaded to avoid lazy initialization
     */
    @Query("SELECT DISTINCT p FROM Passdown p " +
           "LEFT JOIN FETCH p.user " +
           "LEFT JOIN FETCH p.tools " +
           "LEFT JOIN FETCH p.assignedTechs " +
           "ORDER BY p.date DESC")
    List<Passdown> findAllWithUserAndToolOrderByDateDesc();
    
    List<Passdown> findByDateOrderByDateDesc(LocalDate date);
    
    /**
     * Find passdowns by date with user, tools, and assignedTechs eagerly loaded to avoid lazy initialization
     */
    @Query("SELECT DISTINCT p FROM Passdown p " +
           "LEFT JOIN FETCH p.user " +
           "LEFT JOIN FETCH p.tools " +
           "LEFT JOIN FETCH p.assignedTechs " +
           "WHERE p.date = :date " +
           "ORDER BY p.date DESC")
    List<Passdown> findByDateWithUserAndToolOrderByDateDesc(@Param("date") LocalDate date);
    
    List<Passdown> findByDateBetweenOrderByDateDesc(LocalDate startDate, LocalDate endDate);
    
    /**
     * Find passdowns between dates with user, tools, and assignedTechs eagerly loaded to avoid lazy initialization
     */
    @Query("SELECT DISTINCT p FROM Passdown p " +
           "LEFT JOIN FETCH p.user " +
           "LEFT JOIN FETCH p.tools " +
           "LEFT JOIN FETCH p.assignedTechs " +
           "WHERE p.date BETWEEN :startDate AND :endDate " +
           "ORDER BY p.date DESC")
    List<Passdown> findByDateBetweenWithUserAndToolOrderByDateDesc(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    /**
     * Find passdowns by tool ID (ManyToMany relationship)
     */
    @Query("SELECT DISTINCT p FROM Passdown p " +
           "LEFT JOIN FETCH p.user " +
           "LEFT JOIN FETCH p.tools t " +
           "LEFT JOIN FETCH p.assignedTechs " +
           "WHERE t.id = :toolId " +
           "ORDER BY p.date DESC")
    List<Passdown> findByToolIdWithUserAndToolOrderByDateDesc(@Param("toolId") Long toolId);
    
    /**
     * OPTIMIZATION: Bulk find passdowns for multiple tools to avoid N+1 queries
     */
    @Query("SELECT DISTINCT p FROM Passdown p " +
           "LEFT JOIN p.tools t " +
           "WHERE t.id IN :toolIds " +
           "ORDER BY p.date DESC")
    List<Passdown> findByToolIdInOrderByDateDesc(@Param("toolIds") List<Long> toolIds);
    
    /**
     * Lightweight query for tools list view - only loads essential Passdown fields
     * Returns: id, date, user.name, comment (first 100 chars), tool.id
     */
    @Query("SELECT p.id, p.date, p.user.name, " +
           "CASE WHEN LENGTH(p.comment) > 100 THEN CONCAT(SUBSTRING(p.comment, 1, 100), '...') ELSE p.comment END, " +
           "t.id FROM Passdown p " +
           "LEFT JOIN p.tools t " +
           "WHERE t.id IN :toolIds " +
           "ORDER BY p.date DESC")
    List<Object[]> findPassdownListDataByToolIds(@Param("toolIds") List<Long> toolIds);
    
    /**
     * Check for duplicate passdowns (same date, comment, and tools)
     */
    @Query("SELECT p FROM Passdown p " +
           "LEFT JOIN p.tools t " +
           "WHERE p.date = :date " +
           "AND p.comment = :comment " +
           "AND t.id IN :toolIds")
    List<Passdown> findPotentialDuplicates(@Param("date") LocalDate date, 
                                           @Param("comment") String comment, 
                                           @Param("toolIds") List<Long> toolIds);
} 