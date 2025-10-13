package com.pcd.manager.repository;

import com.pcd.manager.model.CustomLocation;
import com.pcd.manager.model.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomLocationRepository extends JpaRepository<CustomLocation, Long> {

    /**
     * Find all custom locations for a specific location
     */
    List<CustomLocation> findByLocationOrderByNameAsc(Location location);

    /**
     * Find a custom location by name and location
     */
    Optional<CustomLocation> findByNameAndLocation(String name, Location location);

    /**
     * Find or create a custom location (for backward compatibility with text-based custom locations)
     */
    @Query("SELECT cl FROM CustomLocation cl WHERE LOWER(cl.name) = LOWER(:name) AND cl.location = :location")
    Optional<CustomLocation> findByNameIgnoreCaseAndLocation(@Param("name") String name, @Param("location") Location location);

    /**
     * Get custom locations with part counts (via moving parts)
     * This query counts incoming moving parts (parts moved TO this location)
     */
    @Query("SELECT cl, " +
           "(SELECT COUNT(DISTINCT mp.id) FROM MovingPart mp WHERE mp.toCustomLocationEntity = cl) as partCount " +
           "FROM CustomLocation cl WHERE cl.location = :location ORDER BY cl.name ASC")
    List<Object[]> findByLocationWithPartCounts(@Param("location") Location location);
}

