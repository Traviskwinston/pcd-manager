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
     * Create a new user with encoded password
     */
    public User createUser(User user) {
        // Encode the password before saving
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        
        return userRepository.save(user);
    }

    /**
     * Update an existing user
     */
    public User updateUser(User user) {
        // Get the existing user to check if password needs updating
        Optional<User> existingUserOpt = userRepository.findById(user.getId());
        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();
            
            // If password is empty in the form, keep the existing password
            if (user.getPassword() == null || user.getPassword().isEmpty()) {
                user.setPassword(existingUser.getPassword());
            } else {
                // Password was provided, encode the new password
                user.setPassword(passwordEncoder.encode(user.getPassword()));
            }
        }
        
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
     * Delete a user by id
     */
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    /**
     * Find users by active tool
     * @param toolId The ID of the tool
     * @return List of users with the specified tool as their active tool
     */
    public List<User> getUsersByActiveTool(Long toolId) {
        return userRepository.findByActiveToolId(toolId);
    }
} 