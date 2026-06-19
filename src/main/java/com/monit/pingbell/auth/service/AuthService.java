package com.monit.pingbell.auth.service;

import com.monit.pingbell.auth.dto.LoginRequest;
import com.monit.pingbell.auth.dto.SignupRequest;
import com.monit.pingbell.auth.dto.TokenResponse;
import com.monit.pingbell.global.security.jwt.JwtTokenProvider;
import com.monit.pingbell.member.domain.Member;
import com.monit.pingbell.member.repository.MemberRepository;
import com.monit.pingbell.notification.domain.NotificationChannel;
import com.monit.pingbell.notification.repository.NotificationChannelRepository;
import com.monit.pingbell.notification.type.NotificationChannelType;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final MemberRepository memberRepository;
    private final NotificationChannelRepository notificationChannelRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(
            MemberRepository memberRepository,
            NotificationChannelRepository notificationChannelRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.memberRepository = memberRepository;
        this.notificationChannelRepository = notificationChannelRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public TokenResponse signup(SignupRequest request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered.");
        }

        Member member = Member.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .build();

        Member saved = memberRepository.save(member);
        notificationChannelRepository.save(new NotificationChannel(
                saved,
                NotificationChannelType.EMAIL,
                saved.getEmail()
        ));

        String accessToken = jwtTokenProvider.createAccessToken(saved.getId(), saved.getEmail());
        return TokenResponse.bearer(accessToken, jwtTokenProvider.getAccessTokenExpirationMs());
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password."));

        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }

        String accessToken = jwtTokenProvider.createAccessToken(member.getId(), member.getEmail());
        return TokenResponse.bearer(accessToken, jwtTokenProvider.getAccessTokenExpirationMs());
    }
}
