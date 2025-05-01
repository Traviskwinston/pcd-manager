package com.pcd.manager.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "part_movements")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PartMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "tool_id", nullable = false)
    private Tool tool;

    @ManyToOne
    @JoinColumn(name = "part_id", nullable = false)
    private Part part;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MovementType type;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column
    private String sourceDestination; // Where the part came from or went to

    @Column
    private String comments;

    public enum MovementType {
        ADDED, // Part was added to the tool
        REMOVED // Part was removed from the tool
    }
} 