package com.pcd.manager.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = true)
    private String username;
    
    @Column(nullable = true)
    private String password;
    
    @Column(nullable = true)
    private String name;
    
    @Column(unique = true, nullable = true)
    private String email;
    
    @Column(nullable = true)
    private String role;
    
    @Column(nullable = true)
    private Boolean active = true;
} 