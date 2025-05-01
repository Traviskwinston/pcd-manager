package com.pcd.manager.repository;

import com.pcd.manager.model.Passdown;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PassdownRepository extends JpaRepository<Passdown, Long> {
    
    List<Passdown> findAllByOrderByDateDesc();
    
    List<Passdown> findByDateOrderByDateDesc(LocalDate date);
    
    List<Passdown> findByDateBetweenOrderByDateDesc(LocalDate startDate, LocalDate endDate);
} 