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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);
    
    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        
        // Add additional logging
        authProvider.setHideUserNotFoundExceptions(false); // Show user not found exceptions
        
        return authProvider;
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
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error=true")
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
                .sessionFixation().migrateSession()
                .maximumSessions(1)
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