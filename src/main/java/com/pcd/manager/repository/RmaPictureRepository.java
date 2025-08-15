package com.pcd.manager.repository;

import com.pcd.manager.model.RmaPicture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface RmaPictureRepository extends JpaRepository<RmaPicture, Long> {
    // findById is inherited from JpaRepository

    @Query("SELECT COUNT(p) FROM RmaPicture p WHERE p.rma.id = :rmaId")
    long countByRmaId(@Param("rmaId") Long rmaId);
} 