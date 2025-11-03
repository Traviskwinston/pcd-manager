package com.pcd.manager.service;

import com.pcd.manager.model.Tool;
import com.pcd.manager.model.User;
import com.pcd.manager.repository.ToolRepository;
import com.pcd.manager.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ToolRepository toolRepository;

    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, ToolRepository toolRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.toolRepository = toolRepository;
    }

    /**
     * Create a new user with encoded password
     */
    @CacheEvict(value = {"users-list", "dropdown-data"}, allEntries = true)
    public User createUser(User user) {
        // Normalize email to lowercase and trim whitespace
        if (user.getEmail() != null) {
            user.setEmail(user.getEmail().trim().toLowerCase());
        }

        // Encode the password before saving
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }

        logger.debug("Creating user with normalized email: {} and evicting caches", user.getEmail());
        return userRepository.save(user);
    }

    /**
     * Update an existing user
     */
    @CacheEvict(value = {"users-list", "dropdown-data"}, allEntries = true)
    public User updateUser(User user) {
        // Normalize email to lowercase and trim whitespace
        if (user.getEmail() != null) {
            user.setEmail(user.getEmail().trim().toLowerCase());
        }

        // Get the existing user to check if password needs updating
        Optional<User> existingUserOpt = userRepository.findById(user.getId());
        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();

            // If password is empty in the form, keep the existing password
            if (user.getPassword() == null || user.getPassword().isEmpty()) {
                user.setPassword(existingUser.getPassword());
            } else {
                // Password was provided - check if it's already encoded
                // If it's already encoded (bcrypt hash), use it as-is to prevent double-encoding
                // If it's plaintext, encode it
                if (isPasswordEncoded(user.getPassword())) {
                    logger.debug("Password appears to be already encoded for user {}, using as-is", user.getEmail());
                    // Password is already encoded, use it as-is
                } else {
                    // Password is plaintext, encode it
                    user.setPassword(passwordEncoder.encode(user.getPassword()));
                    logger.debug("Password encoded for user {}", user.getEmail());
                }
            }
        }

        logger.debug("Updating user with normalized email: {}", user.getEmail());
        return userRepository.save(user);
    }

    /**
     * Get all users
     */
    @Cacheable(value = "users-list", key = "'all-users'")
    public List<User> getAllUsers() {
        logger.debug("Fetching all users (cacheable)");
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
        return userRepository.findByEmailIgnoreCase(email);
    }

    /**
     * Find user by username (using email as username)
     */
    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByEmailIgnoreCase(username);
    }

    /**
     * Delete a user by id
     */
    @CacheEvict(value = {"users-list", "dropdown-data"}, allEntries = true)
    public void deleteUser(Long id) {
        logger.debug("Deleting user {} and evicting caches", id);
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
    
    /**
     * Loads the assigned tools for a user if they aren't already loaded
     * @param user The user to load tools for
     */
    @Transactional
    public void loadUserTools(User user) {
        if (user.getId() == null) {
            return;
        }
        
        // If user has an active tool but it's not in the assigned tools list, add it
        if (user.getActiveTool() != null) {
            boolean activeToolFound = false;
            
            if (user.getAssignedTools() != null) {
                for (Tool tool : user.getAssignedTools()) {
                    if (tool.getId().equals(user.getActiveTool().getId())) {
                        activeToolFound = true;
                        break;
                    }
                }
            }
            
            if (!activeToolFound) {
                user.getAssignedTools().add(user.getActiveTool());
            }
        }
        
        // If the user has tools assigned to them in the database but they're not loaded yet
        if (user.getAssignedTools() == null || user.getAssignedTools().isEmpty()) {
            List<Tool> assignedTools = toolRepository.findToolsAssignedToUser(user.getId());
            if (assignedTools != null && !assignedTools.isEmpty()) {
                user.setAssignedTools(assignedTools);
            }
        }
    }
    
    /**
     * Check if a raw password matches the encoded password
     * @param rawPassword The raw password from login form
     * @param encodedPassword The encoded password from the database
     * @return True if the passwords match, false otherwise
     */
    public boolean checkPassword(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            logger.warn("Cannot check null passwords");
            return false;
        }
        
        try {
            boolean matches = passwordEncoder.matches(rawPassword, encodedPassword);
            if (!matches) {
                logger.debug("Password mismatch for encoded password: {}", encodedPassword);
            }
            return matches;
        } catch (Exception e) {
            logger.error("Error checking password: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if a password is already bcrypt encoded
     * Bcrypt hashes start with $2a$, $2b$, or $2y$ followed by the cost parameter
     * @param password The password string to check
     * @return True if the password appears to be already encoded, false otherwise
     */
    private boolean isPasswordEncoded(String password) {
        if (password == null || password.isEmpty()) {
            return false;
        }
        // Bcrypt hashes start with $2a$, $2b$, or $2y$ followed by a number
        // Example: $2a$10$... (60 characters total)
        return password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$");
    }
    
    /**
     * Set password for a user, encoding it if it's not already encoded
     * This prevents double-encoding issues
     * @param user The user to set the password for
     * @param plaintextPassword The plaintext password to set
     */
    public void setUserPassword(User user, String plaintextPassword) {
        if (plaintextPassword == null || plaintextPassword.isEmpty()) {
            logger.warn("Attempted to set empty password for user {}", user.getEmail());
            return;
        }
        
        // Check if it's already encoded (shouldn't be, but defensive check)
        if (isPasswordEncoded(plaintextPassword)) {
            logger.warn("Password appears to already be encoded for user {}. Setting as-is, but this may cause issues.", user.getEmail());
            user.setPassword(plaintextPassword);
        } else {
            // Encode the plaintext password
            user.setPassword(passwordEncoder.encode(plaintextPassword));
            logger.debug("Password encoded and set for user {}", user.getEmail());
        }
    }
    
    /**
     * Update an existing user's password explicitly
     * This method ensures the password is properly encoded
     * @param user The user to update
     * @param newPlaintextPassword The new plaintext password
     * @return The updated user
     */
    @CacheEvict(value = {"users-list", "dropdown-data"}, allEntries = true)
    public User updateUserPassword(Long userId, String newPlaintextPassword) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new RuntimeException("User not found with ID: " + userId);
        }
        
        User user = userOpt.get();
        setUserPassword(user, newPlaintextPassword);
        user.setActive(true); // Ensure user is active when password is reset
        
        logger.info("Password updated for user {} (ID: {})", user.getEmail(), userId);
        return userRepository.save(user);
    }
} 