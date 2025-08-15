package com.pcd.manager.service;

import com.pcd.manager.model.User;
import com.pcd.manager.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Upgrades legacy plaintext passwords to bcrypt transparently after a successful login
 */
@Service
public class LegacyPasswordUpgradeService implements UserDetailsPasswordService {

    private static final Logger logger = LoggerFactory.getLogger(LegacyPasswordUpgradeService.class);

    private final UserRepository userRepository;

    @Autowired
    public LegacyPasswordUpgradeService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails updatePassword(UserDetails user, String newEncodedPassword) {
        try {
            Optional<User> userOpt = userRepository.findByEmailIgnoreCase(user.getUsername());
            if (userOpt.isPresent()) {
                User u = userOpt.get();
                u.setPassword(newEncodedPassword); // already encoded
                userRepository.save(u);
                logger.info("Upgraded legacy password hash for user {}", user.getUsername());
            } else {
                logger.warn("Could not upgrade password: user not found for {}", user.getUsername());
            }
        } catch (Exception e) {
            logger.error("Error upgrading legacy password for {}: {}", user.getUsername(), e.getMessage());
        }
        return user;
    }
}


