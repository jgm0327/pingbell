package com.monit.pingbell.logingestion.security;

import com.monit.pingbell.logingestion.service.LogIngestionApiKeyService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogIngestionApiKeyAuthenticationFilterTest {

    @Mock
    private LogIngestionApiKeyService apiKeyService;

    @Mock
    private FilterChain filterChain;

    private LogIngestionApiKeyAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new LogIngestionApiKeyAuthenticationFilter(apiKeyService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesValidApiKeyWithIngestionOnlyAuthority() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer pgbl_validkey");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticatedIngestionKey principal = new AuthenticatedIngestionKey(5L, 9L);
        when(apiKeyService.authenticate("pgbl_validkey")).thenReturn(Optional.of(principal));

        filter.doFilter(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(principal);
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_INGESTION");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void leavesSecurityContextEmptyForUnknownOrRevokedKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer pgbl_unknown");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(apiKeyService.authenticate("pgbl_unknown")).thenReturn(Optional.empty());

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void ignoresNonApiKeyTokensWithoutCallingTheService() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer some.jwt.token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(apiKeyService, never()).authenticate(any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void ignoresMissingAuthorizationHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(apiKeyService, never()).authenticate(any());
        verify(filterChain).doFilter(request, response);
    }
}
