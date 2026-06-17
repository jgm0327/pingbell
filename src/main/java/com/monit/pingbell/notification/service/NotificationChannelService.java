package com.monit.pingbell.notification.service;

import com.monit.pingbell.member.domain.Member;
import com.monit.pingbell.member.repository.MemberRepository;
import com.monit.pingbell.notification.domain.NotificationChannel;
import com.monit.pingbell.notification.dto.NotificationChannelCreateRequest;
import com.monit.pingbell.notification.dto.NotificationChannelResponse;
import com.monit.pingbell.notification.repository.NotificationChannelRepository;
import com.monit.pingbell.notification.type.NotificationChannelType;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class NotificationChannelService {

    private final NotificationChannelRepository channelRepository;
    private final MemberRepository memberRepository;

    public NotificationChannelService(
            NotificationChannelRepository channelRepository,
            MemberRepository memberRepository
    ) {
        this.channelRepository = channelRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public NotificationChannelResponse createChannel(NotificationChannelCreateRequest request) {
        Long memberId = resolveAuthenticatedMemberId();
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authentication context."));

        if (request.type() != NotificationChannelType.EMAIL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only EMAIL notification channel is supported in MVP.");
        }

        NotificationChannel channel = new NotificationChannel(
                member,
                request.type(),
                request.target()
        );

        return NotificationChannelResponse.from(channelRepository.save(channel));
    }

    @Transactional(readOnly = true)
    public List<NotificationChannelResponse> getChannels() {
        Long memberId = resolveAuthenticatedMemberId();
        return channelRepository.findAllByMemberIdOrderByIdDesc(memberId)
                .stream()
                .map(NotificationChannelResponse::from)
                .toList();
    }

    private Long resolveAuthenticatedMemberId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getCredentials() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        }

        try {
            return Long.valueOf(authentication.getCredentials().toString());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authentication context.");
        }
    }
}
