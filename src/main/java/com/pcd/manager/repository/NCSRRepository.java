package com.pcd.manager.repository;

import com.pcd.manager.model.NCSR;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NCSRRepository extends JpaRepository<NCSR, Long> {
    
    /**
     * Find all NCSR records for a specific tool
     */
    List<NCSR> findByToolId(Long toolId);
    
    /**
     * Find all NCSR records by equipment number (for matching with tool serial numbers)
     */
    List<NCSR> findByEquipmentNumber(String equipmentNumber);
    
    /**
     * Find all NCSR records by equipment number containing (for partial matches)
     */
    List<NCSR> findByEquipmentNumberContaining(String equipmentNumber);
    
    /**
     * Find NCSR records by status
     */
    List<NCSR> findByStatus(NCSR.NcsrStatus status);
    
    /**
     * Find NCSR records by installed status
     */
    List<NCSR> findByInstalled(Boolean installed);
    
    /**
     * Count NCSR records for a tool
     */
    long countByToolId(Long toolId);
    
    /**
     * Find all NCSR records with no tool assignment
     */
    List<NCSR> findByToolIsNull();
    
    /**
     * Custom query to find NCSR records matching tool serial numbers
     * Matches Equipment# with serialNumber1 or serialNumber2 (with or without suffix after "-")
     */
    @Query("SELECT n FROM NCSR n WHERE n.tool.id = :toolId OR " +
           "n.equipmentNumber = :serial1 OR n.equipmentNumber = :serial2 OR " +
           "(n.equipmentNumber IS NOT NULL AND :serial1 IS NOT NULL AND " +
           "(CONCAT(n.equipmentNumber, '-') = SUBSTRING(:serial1, 1, LENGTH(n.equipmentNumber) + 1) OR " +
           "CONCAT(:serial1, '-') = SUBSTRING(n.equipmentNumber, 1, LENGTH(:serial1) + 1))) OR " +
           "(n.equipmentNumber IS NOT NULL AND :serial2 IS NOT NULL AND " +
           "(CONCAT(n.equipmentNumber, '-') = SUBSTRING(:serial2, 1, LENGTH(n.equipmentNumber) + 1) OR " +
           "CONCAT(:serial2, '-') = SUBSTRING(n.equipmentNumber, 1, LENGTH(:serial2) + 1)))")
    List<NCSR> findByToolOrMatchingSerialNumbers(
        @Param("toolId") Long toolId,
        @Param("serial1") String serialNumber1,
        @Param("serial2") String serialNumber2
    );
}
