package com.pcd.manager.repository;

import com.pcd.manager.model.PassdownPicture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PassdownPictureRepository extends JpaRepository<PassdownPicture, Long> {
    List<PassdownPicture> findByPassdownId(Long passdownId);
} 