package com.pcd.manager.service;

import com.pcd.manager.model.ReturnAddress;
import com.pcd.manager.repository.ReturnAddressRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing return addresses
 */
@Service
@Slf4j
public class ReturnAddressService {
    
    @Autowired
    private ReturnAddressRepository returnAddressRepository;
    
    /**
     * Get all return addresses ordered by name
     */
    public List<ReturnAddress> getAllReturnAddresses() {
        return returnAddressRepository.findAllByOrderByNameAsc();
    }
    
    /**
     * Get a return address by ID
     */
    public Optional<ReturnAddress> getReturnAddressById(Long id) {
        return returnAddressRepository.findById(id);
    }
    
    /**
     * Get a return address by name
     */
    public Optional<ReturnAddress> getReturnAddressByName(String name) {
        return returnAddressRepository.findByName(name);
    }
    
    // Removed default address concept
    
    /**
     * Save or update a return address
     */
    @Transactional
    public ReturnAddress saveReturnAddress(ReturnAddress returnAddress) {
        return returnAddressRepository.save(returnAddress);
    }
    
    /**
     * Delete a return address
     */
    @Transactional
    public void deleteReturnAddress(Long id) {
        returnAddressRepository.deleteById(id);
    }
    
    /**
     * Check if a return address exists by name (case insensitive)
     */
    public boolean existsByName(String name, Long excludeId) {
        Optional<ReturnAddress> existing = returnAddressRepository.findByName(name);
        if (existing.isEmpty()) {
            return false;
        }
        // If we're excluding an ID (for updates), check if the found address is the excluded one
        if (excludeId != null) {
            return !existing.get().getId().equals(excludeId);
        }
        return true;
    }
}

