package com.pcd.manager.config;

import com.pcd.manager.service.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

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
        return authProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Disable CSRF for REST API
            .authorizeHttpRequests(auth -> auth
                // Explicitly permit necessary static resources and login
                .requestMatchers("/login", "/css/**", "/js/**", "/images/**", "/login.css").permitAll()
                .requestMatchers("/h2-console/**").permitAll() // Allow access to H2 console
                .requestMatchers("/api/public/**").permitAll() // Public endpoints
                .requestMatchers("/api/auth/**").permitAll() // Authentication endpoints
                // Any other request must be authenticated (including root '/')
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login") // This is the page users are redirected to
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error=true")
                .permitAll() // Allow access to the login processing URL itself
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            );
            
        // Allow frames for H2 console
        http.headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()));
            
        return http.build();
    }
} 