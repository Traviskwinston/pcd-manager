package com.pcd.manager.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "moving_parts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovingPart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String partName;

    @ManyToOne
    @JoinColumn(name = "from_tool_id")
    private Tool fromTool;

    @ManyToOne
    @JoinColumn(name = "to_tool_id")
    private Tool toTool;

    @Column(nullable = false)
    private LocalDateTime moveDate;

    @Column(length = 1000)
    private String notes;

    @ManyToOne
    @JoinColumn(name = "note_id")
    private Note linkedNote;

    @Override
    public String toString() {
        return "MovingPart{" +
               "id=" + id +
               ", partName='" + partName + '\'' +
               ", fromToolId=" + (fromTool != null ? fromTool.getId() : null) +
               ", toToolId=" + (toTool != null ? toTool.getId() : null) +
               ", moveDate=" + moveDate +
               '}';
    }
} 