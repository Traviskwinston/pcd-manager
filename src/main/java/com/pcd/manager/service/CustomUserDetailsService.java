package com.pcd.manager.service;

import com.pcd.manager.model.User;
import com.pcd.manager.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(CustomUserDetailsService.class);
    private final UserRepository userRepository;

    @Autowired
    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        logger.info("=== AUTHENTICATION ATTEMPT ===");
        logger.info("Attempting to load user with email/username: '{}'", username);
        
        // In Spring Security, 'username' parameter is the identifier provided in login form
        // Since we're using email as the identifier, we'll search by email
        User user = userRepository.findByEmailIgnoreCase(username)
                .orElseThrow(() -> {
                    logger.error("USER NOT FOUND: No user exists with email '{}'", username);
                    return new UsernameNotFoundException("User not found with email: " + username);
                });
        
        logger.info("USER FOUND: email={}, active={}, role={}", user.getEmail(), user.getActive(), user.getRole());
        if (user.getActive() == null || !user.getActive()) {
            logger.warn("SECURITY ALERT: User '{}' is INACTIVE and will be rejected by Spring Security", user.getEmail());
        }
        
        // Validate password hash format to detect corruption
        String passwordHash = user.getPassword();
        if (passwordHash == null || passwordHash.isEmpty()) {
            logger.error("CRITICAL: User '{}' has NULL or EMPTY password hash - authentication will fail!", user.getEmail());
        } else if (!passwordHash.startsWith("$2a$") && !passwordHash.startsWith("$2b$") && !passwordHash.startsWith("$2y$")) {
            logger.error("CRITICAL: User '{}' password hash does not appear to be bcrypt encoded (starts with: {}) - authentication may fail!", 
                    user.getEmail(), passwordHash.length() > 10 ? passwordHash.substring(0, 10) : passwordHash);
        } else {
            logger.debug("Password hash format valid for user {}", user.getEmail());
        }
        logger.info("Password hash length: {} characters", passwordHash != null ? passwordHash.length() : 0);

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        
        // Add role based on user's role
        if (user.getRole() != null && !user.getRole().isEmpty()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
        } else {
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }

        boolean enabled = user.getActive() != null ? user.getActive() : true;
        logger.info("Creating UserDetails - enabled={}, authorities={}", enabled, authorities);
        logger.info("=== END AUTHENTICATION ATTEMPT ===");
        
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(), // Use email as the username
                user.getPassword(),
                enabled,   // enabled
                true,   // accountNonExpired
                true,   // credentialsNonExpired
                true,   // accountNonLocked
                authorities
        );
    }
} 