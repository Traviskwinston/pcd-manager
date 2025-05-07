package com.pcd.manager.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ncsrs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NCSR {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "tool_id")
    private Tool tool;

    @ManyToMany
    @JoinTable(
        name = "ncsr_parts",
        joinColumns = @JoinColumn(name = "ncsr_id"),
        inverseJoinColumns = @JoinColumn(name = "part_id")
    )
    private List<Part> parts = new ArrayList<>();

    @Column
    private LocalDate actualShipDate;

    @Column
    private Boolean isOpen = true;

    @Column
    private Integer quantity;

    @Column
    private String customerPO;

    @Column
    private String ecrOrNcsrTag; // ECR#/NCSR tag#

    @Column
    private String factoryNumber;

    @Column
    private String forwarder;

    @Column
    private String trackingNumber;
} 