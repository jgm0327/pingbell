package com.monit.pingbell.auth.service;

import com.monit.pingbell.auth.dto.RefreshTokenRequest;
import com.monit.pingbell.auth.dto.SignupRequest;
import com.monit.pingbell.auth.dto.TokenResponse;
import com.monit.pingbell.global.security.jwt.JwtTokenProvider;
import com.monit.pingbell.member.domain.Member;
import com.monit.pingbell.member.repository.MemberRepository;
import com.monit.pingbell.notification.domain.NotificationChannel;
import com.monit.pingbell.notification.repository.NotificationChannelRepository;
import com.monit.pingbell.notification.type.NotificationChannelType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private NotificationChannelRepository notificationChannelRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    @Test
    void signupCreatesDefaultEmailNotificationChannel() {
        SignupRequest request = new SignupRequest("user@example.com", "password123");
        Member savedMember = Member.builder()
                .email(request.email())
                .password("encoded-password")
                .build();
        ReflectionTestUtils.setField(savedMember, "id", 1L);

        when(memberRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("encoded-password");
        when(memberRepository.save(any(Member.class))).thenReturn(savedMember);
        when(jwtTokenProvider.createAccessToken(savedMember.getId(), savedMember.getEmail())).thenReturn("access-token");
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(3600000L);
        when(refreshTokenService.create(savedMember.getId(), true)).thenReturn(new RefreshToken("refresh-token", 2592000000L));

        authService.signup(request);

        ArgumentCaptor<NotificationChannel> captor = ArgumentCaptor.forClass(NotificationChannel.class);
        verify(notificationChannelRepository).save(captor.capture());

        NotificationChannel channel = captor.getValue();
        assertThat(channel.getMember()).isSameAs(savedMember);
        assertThat(channel.getType()).isEqualTo(NotificationChannelType.EMAIL);
        assertThat(channel.getTarget()).isEqualTo(request.email());
        assertThat(channel.isEnabled()).isTrue();
    }

    @Test
    void refreshConsumesRefreshTokenAndRotatesTokens() {
        RefreshTokenRequest request = new RefreshTokenRequest("old-refresh-token");
        Member member = Member.builder()
                .email("user@example.com")
                .password("encoded-password")
                .build();
        ReflectionTestUtils.setField(member, "id", 1L);

        when(refreshTokenService.consume(request.refreshToken())).thenReturn(new RefreshTokenPayload(1L, true));
        when(memberRepository.findById(1L)).thenReturn(java.util.Optional.of(member));
        when(jwtTokenProvider.createAccessToken(member.getId(), member.getEmail())).thenReturn("new-access-token");
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(3600000L);
        when(refreshTokenService.create(member.getId(), true)).thenReturn(new RefreshToken("new-refresh-token", 2592000000L));

        TokenResponse response = authService.refresh(request);

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(response.refreshTokenExpiresIn()).isEqualTo(2592000000L);
        verify(refreshTokenService).consume("old-refresh-token");
    }
}
