package com.pcd.manager.config;

import com.pcd.manager.service.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.session.ConcurrentSessionFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);
    
    @Autowired
    private CustomUserDetailsService userDetailsService;
    @Autowired(required = false)
    private UserDetailsPasswordService passwordUpgradeService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        LoggingDaoAuthenticationProvider authProvider = new LoggingDaoAuthenticationProvider(logger, passwordEncoder());
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        if (passwordUpgradeService != null) {
            authProvider.setUserDetailsPasswordService(passwordUpgradeService);
        }
        
        // Add additional logging
        authProvider.setHideUserNotFoundExceptions(false); // Show user not found exceptions
        
        return authProvider;
    }
    
    // Custom DaoAuthenticationProvider with logging
    private static class LoggingDaoAuthenticationProvider extends DaoAuthenticationProvider {
        private final Logger log;
        
        public LoggingDaoAuthenticationProvider(Logger log, PasswordEncoder encoder) {
            this.log = log;
            setPasswordEncoder(encoder);
        }
        
        @Override
        protected void additionalAuthenticationChecks(org.springframework.security.core.userdetails.UserDetails userDetails,
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken authentication)
                throws org.springframework.security.core.AuthenticationException {
            log.info("=== PASSWORD COMPARISON ===");
            log.info("Username: {}", userDetails.getUsername());
            log.info("Password from form (raw): '{}'", authentication.getCredentials());
            log.info("Password hash from DB: {}", userDetails.getPassword());
            log.info("Attempting BCrypt comparison...");
            try {
                super.additionalAuthenticationChecks(userDetails, authentication);
                log.info("✓ PASSWORD MATCH SUCCESS");
            } catch (Exception e) {
                log.error("✗ PASSWORD MATCH FAILED: {}", e.getMessage());
                throw e;
            }
        }
    }
    
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }
    
    @Bean
    public AuthenticationSuccessHandler customAuthenticationSuccessHandler() {
        return (HttpServletRequest request, HttpServletResponse response, Authentication authentication) -> {
            try {
                logger.info("=== LOGIN SUCCESS HANDLER ===");
                logger.info("User '{}' logged in successfully", authentication.getName());
                
                // Create a new session (Spring Security will handle session fixation with newSession() strategy)
                // No need to manually invalidate - the sessionFixation().newSession() does this safely
                
                logger.info("New session created by Spring Security");
                logger.info("Redirecting to /dashboard");
                response.sendRedirect("/dashboard");
            } catch (Exception e) {
                logger.error("ERROR in success handler: {}", e.getMessage(), e);
                response.sendRedirect("/login?error=true");
            }
        };
    }
    
    @Bean
    public AuthenticationFailureHandler customAuthenticationFailureHandler() {
        return (HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) -> {
            logger.error("=== LOGIN FAILURE HANDLER ===");
            logger.error("Authentication failed: {}", exception.getMessage(), exception);
            response.sendRedirect("/login?error=true");
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authenticationProvider(authenticationProvider())   // Ensure our DAO provider is registered
            .csrf(csrf -> csrf.disable()) // Disable CSRF for now until we debug the login issues
            .authorizeHttpRequests(auth -> auth
                // Explicitly permit necessary static resources and login
                .requestMatchers("/login", "/manual-login", "/css/**", "/js/**", "/images/**", "/login.css").permitAll()
                .requestMatchers("/h2-console/**").permitAll() // Allow access to H2 console
                .requestMatchers("/api/public/**").permitAll() // Public endpoints
                .requestMatchers("/api/auth/**").permitAll() // Authentication endpoints
                .requestMatchers("/api/auth/clear-my-session", "/api/auth/clear-session").permitAll()
                .requestMatchers("/admin/emergency-reset").permitAll() // Emergency reset endpoint
                .requestMatchers("/create-simple-admin").permitAll() // Simple admin creation endpoint
                .requestMatchers("/direct-login").permitAll() // Direct login endpoint
                // Any other request must be authenticated (including root '/')
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login") // This is the page users are redirected to
                .loginProcessingUrl("/login") // URL to submit the login form
                .successHandler(customAuthenticationSuccessHandler()) // Use custom success handler
                .failureHandler(customAuthenticationFailureHandler()) // Use custom failure handler
                .usernameParameter("username") // Field name for the username in the form
                .passwordParameter("password") // Field name for the password in the form
                .permitAll() // Allow access to the login processing URL itself
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .sessionManagement(session -> session
                .invalidSessionUrl("/login?timeout=true")
                .sessionFixation().newSession() // Create a completely new session on login
                .maximumSessions(-1) // Allow unlimited sessions - we'll manage cleanup ourselves
                    .maxSessionsPreventsLogin(false)
                    .expiredUrl("/login?expired=true")
                    .sessionRegistry(sessionRegistry())
            );
            
        // Allow frames for H2 console
        http.headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()));
        
        logger.info("Security filter chain configured successfully");
            
        return http.build();
    }
} 