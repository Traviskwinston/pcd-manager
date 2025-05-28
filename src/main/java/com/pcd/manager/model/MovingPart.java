package com.pcd.manager.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

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

    @Column
    private String serialNumber;

    @Column
    private String partNumber;

    @Column
    private Integer quantity;

    @ManyToOne
    @JoinColumn(name = "from_tool_id")
    private Tool fromTool;

    @ManyToOne
    @JoinColumn(name = "to_tool_id")
    private Tool toTool;

    @Column(name = "destination_chain", columnDefinition = "TEXT")
    private String destinationChain;

    @ManyToOne
    @JoinColumn(name = "rma_id", nullable = true)
    @JsonBackReference
    private Rma rma;

    @Column(nullable = false)
    private LocalDateTime moveDate;

    @Column(length = 1000)
    private String notes;

    @ManyToOne
    @JoinColumn(name = "note_id")
    private Note linkedNote;

    @ManyToOne
    @JoinColumn(name = "linked_track_trend_id", nullable = true)
    private TrackTrend linkedTrackTrend;

    @ManyToOne
    @JoinColumn(name = "additionally_linked_tool_id", nullable = true)
    private Tool additionallyLinkedTool;

    @ManyToOne
    @JoinColumn(name = "additionally_linked_rma_id", nullable = true)
    @JsonBackReference("additionalRmaLink")
    private Rma additionallyLinkedRma;

    // Helper methods for destination chain
    public List<Long> getDestinationToolIds() {
        if (destinationChain == null || destinationChain.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(destinationChain, new TypeReference<List<Long>>() {});
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    public void setDestinationToolIds(List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            this.destinationChain = null;
            return;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            this.destinationChain = mapper.writeValueAsString(toolIds);
        } catch (JsonProcessingException e) {
            this.destinationChain = null;
        }
    }

    public boolean hasDestinationChain() {
        return destinationChain != null && !destinationChain.trim().isEmpty();
    }

    @Override
    public String toString() {
        return "MovingPart{" +
               "id=" + id +
               ", partName='" + partName + '\'' +
               ", fromToolId=" + (fromTool != null ? fromTool.getId() : null) +
               ", toToolId=" + (toTool != null ? toTool.getId() : null) +
               ", rmaId=" + (rma != null ? rma.getId() : null) +
               ", moveDate=" + moveDate +
               '}';
    }
} 