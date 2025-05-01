package com.pcd.manager.repository;

import com.pcd.manager.model.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.List;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {
    
    // Simple query with a completely different name to avoid any conflicts
    @Query(value = "SELECT id FROM locations WHERE default_location = TRUE LIMIT 1", nativeQuery = true)
    Long findDefaultLocationId();
    
    @Modifying
    @Transactional
    @Query(value = "UPDATE locations SET default_location = FALSE", nativeQuery = true)
    void clearDefaultLocations();
} 