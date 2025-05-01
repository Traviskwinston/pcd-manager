package com.pcd.manager.service;

import com.pcd.manager.model.User;
import com.pcd.manager.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Create a new user with proper username and encoded password
     */
    public User createUser(User user) {
        // Ensure username is set to email if not provided
        if (user.getUsername() == null || user.getUsername().isEmpty()) {
            user.setUsername(user.getEmail());
        }
        
        // Encode the password before saving
        if (user.getPassword() != null) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        
        return userRepository.save(user);
    }

    /**
     * Update an existing user
     */
    public User updateUser(User user) {
        // For updates, we also ensure username matches email
        if (user.getUsername() == null || user.getUsername().isEmpty()) {
            user.setUsername(user.getEmail());
        }
        
        // Only encode password if it has changed (a non-encoded value was provided)
        // This would need more sophisticated handling in a real application
        // to determine if the password was changed
        
        return userRepository.save(user);
    }

    /**
     * Get all users
     */
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /**
     * Find user by id
     */
    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    /**
     * Find user by email
     */
    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Find user by username (which is typically the email)
     */
    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * Delete a user by id
     */
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
} 