package com.pcd.manager.repository;

import com.pcd.manager.model.ReturnAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for managing return addresses
 */
@Repository
public interface ReturnAddressRepository extends JpaRepository<ReturnAddress, Long> {
    
    List<ReturnAddress> findAllByOrderByNameAsc();
    
    Optional<ReturnAddress> findByName(String name);
    
    Optional<ReturnAddress> findByIsDefaultTrue();
}




