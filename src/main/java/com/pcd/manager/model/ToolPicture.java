package com.pcd.manager.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "tool_pictures")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolPicture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName;
    private String filePath; // Path relative to the base upload directory
    private String fileType; // e.g., image/jpeg, image/png
    private Long fileSize; // Size in bytes

    @Column(nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_user_id")
    private User uploadedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tool_id", nullable = false)
    private Tool tool;

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
    }
} 