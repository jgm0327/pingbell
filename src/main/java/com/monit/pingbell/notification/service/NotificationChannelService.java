package com.monit.pingbell.notification.service;

import com.monit.pingbell.member.domain.Member;
import com.monit.pingbell.member.repository.MemberRepository;
import com.monit.pingbell.notification.domain.NotificationChannel;
import com.monit.pingbell.notification.dto.NotificationChannelCreateRequest;
import com.monit.pingbell.notification.dto.NotificationChannelResponse;
import com.monit.pingbell.notification.dto.NotificationChannelUpdateRequest;
import com.monit.pingbell.notification.repository.NotificationChannelRepository;
import com.monit.pingbell.notification.type.NotificationChannelType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class NotificationChannelService {

    private static final Pattern SIMPLE_EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

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
    public NotificationChannelResponse createChannel(Long memberId, NotificationChannelCreateRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authentication context."));

        validateTarget(request.type(), request.target());

        NotificationChannel channel = new NotificationChannel(
                member,
                request.type(),
                request.target()
        );

        return NotificationChannelResponse.from(channelRepository.save(channel));
    }

    @Transactional(readOnly = true)
    public List<NotificationChannelResponse> getChannels(Long memberId) {
        return channelRepository.findAllByMemberIdOrderByIdDesc(memberId)
                .stream()
                .map(NotificationChannelResponse::from)
                .toList();
    }

    @Transactional
    public NotificationChannelResponse updateChannel(
            Long memberId,
            UUID publicId,
            NotificationChannelUpdateRequest request
    ) {
        NotificationChannel channel = findOwnedChannel(publicId, memberId);
        validateTarget(channel.getType(), request.target());
        channel.updateTarget(request.target());
        channel.enable();
        return NotificationChannelResponse.from(channel);
    }

    @Transactional
    public void deleteChannel(Long memberId, UUID publicId) {
        NotificationChannel channel = findOwnedChannel(publicId, memberId);
        channel.disable();
    }

    private NotificationChannel findOwnedChannel(UUID publicId, Long memberId) {
        return channelRepository.findByPublicIdAndMemberId(publicId, memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification channel not found."));
    }

    private void validateTarget(NotificationChannelType type, String target) {
        switch (type) {
            case EMAIL -> validateEmailTarget(target);
            case SLACK, DISCORD -> validateWebhookUrl(target, type);
        }
    }

    private void validateEmailTarget(String target) {
        if (!SIMPLE_EMAIL_PATTERN.matcher(target).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email notification target.");
        }
    }

    private void validateWebhookUrl(String target, NotificationChannelType type) {
        try {
            URI uri = new URI(target);
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) || uri.getHost() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + type + " webhook URL.");
            }
        } catch (URISyntaxException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + type + " webhook URL.");
        }
    }
}
