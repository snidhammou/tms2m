package com.tms.server.config;

import com.tms.server.domain.TerminalStatus;
import com.tms.server.repository.TerminalRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authentifie les appels de l'agent Android via l'en-tête {@code X-Device-Token}.
 * Le principal positionné est l'id (Long) du terminal.
 */
public class DeviceTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Device-Token";

    private final TerminalRepository terminals;

    public DeviceTokenFilter(TerminalRepository terminals) {
        this.terminals = terminals;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/device/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = request.getHeader(HEADER);
        if (token != null && !token.isBlank()) {
            terminals.findByDeviceTokenHash(Tokens.sha256(token))
                    .filter(t -> t.getStatus() == TerminalStatus.ACTIVE)
                    .ifPresent(t -> {
                        var auth = new UsernamePasswordAuthenticationToken(t.getId(), null,
                                List.of(new SimpleGrantedAuthority("ROLE_DEVICE")));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    });
        }
        chain.doFilter(request, response);
    }
}
