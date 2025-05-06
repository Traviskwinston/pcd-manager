package com.pcd.manager.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = true)
    private String password;
    
    @Column(nullable = true)
    private String name;
    
    @Column(unique = true, nullable = false)
    private String email;
    
    @Column(nullable = true)
    private String role;
    
    @Column(nullable = true)
    private Boolean active = true;
    
    @Column(nullable = true)
    private String phoneNumber;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "active_site_id")
    private Location activeSite;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "active_tool_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Tool activeTool;
} 