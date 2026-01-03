package com.patniom.api_guardian.config;

import com.patniom.api_guardian.ratelimit.RateLimitingFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/test/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated()
                )

                .addFilterBefore(rateLimitingFilter,
UsernamePasswordAuthenticationFilter.class); //Inserts RateLimitingFilter into Spring Security’s filter chain before UsernamePasswordAuthenticationFilter
//Rate limit even unauthenticated requests
//Block abusive traffic early
//Avoid unnecessary DB / JWT processing
//Save CPU & memory

        return http.build();
        //Freezes the configuration
        //Spring uses this exact chain for all requests
    }
}

