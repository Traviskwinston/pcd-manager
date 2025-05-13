package com.pcd.manager.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

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
    
    @Column(name = "first_name")
    private String firstName;
    
    @Column(name = "last_name")
    private String lastName;
    
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
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "default_location_id")
    private Location defaultLocation;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "active_tool_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Tool activeTool;
    
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_tool_assignments",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "tool_id")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Tool> assignedTools = new ArrayList<>();
    
    @PostLoad
    @PostPersist
    public void syncNameFields() {
        if (name != null && !name.isEmpty() && (firstName == null || lastName == null)) {
            String[] parts = name.split(" ", 2);
            if (parts.length > 0) {
                firstName = parts[0];
                lastName = parts.length > 1 ? parts[1] : "";
            }
        }
        
        if ((firstName != null || lastName != null) && (name == null || name.isEmpty())) {
            StringBuilder fullName = new StringBuilder();
            if (firstName != null) {
                fullName.append(firstName);
            }
            if (lastName != null && !lastName.isEmpty()) {
                if (fullName.length() > 0) {
                    fullName.append(" ");
                }
                fullName.append(lastName);
            }
            name = fullName.toString();
        }
        
        if (role != null && (roles == null || roles.isEmpty())) {
            roles = role;
        } else if (roles != null && (role == null || role.isEmpty())) {
            role = roles;
        }
        
        if (activeSite != null && defaultLocation == null) {
            defaultLocation = activeSite;
        } else if (defaultLocation != null && activeSite == null) {
            activeSite = defaultLocation;
        }
    }
    
    private String roles;
} 