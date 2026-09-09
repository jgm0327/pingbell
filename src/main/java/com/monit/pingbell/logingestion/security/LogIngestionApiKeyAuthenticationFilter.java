package com.monit.pingbell.logingestion.security;

import com.monit.pingbell.logingestion.service.LogIngestionApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Authenticates a request carrying a log ingestion API key ("Bearer pgbl_..."), as opposed to
 * the human-facing JWT handled by {@code JwtAuthenticationFilter}. The two are mutually
 * exclusive by prefix, so either filter can run in either order safely - each only acts on
 * tokens shaped like its own. A successfully authenticated key gets ROLE_INGESTION only, never
 * ROLE_USER, so it cannot be used against the normal human-facing API surface. */
@Component
@RequiredArgsConstructor
public class LogIngestionApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_PREFIX = "pgbl_";

    private final LogIngestionApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = resolveToken(request.getHeader("Authorization"));

        if (token != null && token.startsWith(API_KEY_PREFIX)) {
            apiKeyService.authenticate(token).ifPresent(principal -> {
                List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_INGESTION"));
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring(7);
    }
}
