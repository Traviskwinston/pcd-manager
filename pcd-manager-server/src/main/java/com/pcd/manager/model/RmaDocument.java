package com.pcd.manager.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "rma_documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RmaDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName;
    private String filePath; // Path relative to the base upload directory
    private String fileType; // e.g., application/pdf, image/jpeg
    private Long fileSize; // Size in bytes

    @Column(nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rma_id", nullable = false)
    private Rma rma;

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
    }
} 