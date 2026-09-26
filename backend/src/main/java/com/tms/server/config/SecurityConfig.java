package com.tms.server.config;

import com.tms.server.repository.AdminUserRepository;
import jakarta.servlet.DispatcherType;
import com.tms.server.repository.TerminalRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, TerminalRepository terminals) throws Exception {
        // 401 sans en-tête WWW-Authenticate : évite la popup Basic du navigateur sur la console web
        var entryPoint = new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Reprise asynchrone du long polling (/api/device/v1/wait) : la requête
                        // initiale a déjà été authentifiée ; la redispatch ASYNC n'a pas de contexte
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/device/v1/enroll").permitAll()
                        .requestMatchers("/api/device/**").hasRole("DEVICE")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico",
                                "/actuator/health", "/error").permitAll()
                        .anyRequest().denyAll())
                .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
                .addFilterBefore(new DeviceTokenFilter(terminals), BasicAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(AdminUserRepository users) {
        return username -> users.findByUsername(username)
                .map(u -> User.withUsername(u.getUsername())
                        .password(u.getPasswordHash())
                        .roles(u.getRole())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
