package com.devops.dvmp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/denied", "/guide", "/error", "/style.css",
                                 "/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                // USER / ADMIN upload datasets
                .requestMatchers(HttpMethod.GET, "/datasets/new").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/datasets").hasAnyRole("USER", "ADMIN")
                // REVIEWER / ADMIN review
                .requestMatchers(HttpMethod.POST, "/datasets/*/approve", "/datasets/*/reject").hasAnyRole("REVIEWER", "ADMIN")
                // ADMIN only
                .requestMatchers(HttpMethod.POST, "/datasets/*/delete").hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(f -> f.loginPage("/login").defaultSuccessUrl("/", true).permitAll())
            .logout(l -> l.logoutSuccessUrl("/login?logout").permitAll())
            .exceptionHandling(e -> e.accessDeniedPage("/denied"));
        return http.build();
    }

    /** Demo accounts (in-memory). Replace with a database in a real system. */
    @Bean
    UserDetailsService users() {
        PasswordEncoder enc = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        return new InMemoryUserDetailsManager(
            User.withUsername("user").password(enc.encode("user123")).roles("USER").build(),
            User.withUsername("user2").password(enc.encode("user123")).roles("USER").build(),
            User.withUsername("reviewer").password(enc.encode("reviewer123")).roles("REVIEWER").build(),
            User.withUsername("admin").password(enc.encode("admin123")).roles("ADMIN").build());
    }
}
