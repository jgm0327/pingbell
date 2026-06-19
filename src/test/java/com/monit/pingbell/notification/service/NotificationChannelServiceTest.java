package com.monit.pingbell.notification.service;

import com.monit.pingbell.member.domain.Member;
import com.monit.pingbell.member.repository.MemberRepository;
import com.monit.pingbell.notification.domain.NotificationChannel;
import com.monit.pingbell.notification.dto.NotificationChannelUpdateRequest;
import com.monit.pingbell.notification.repository.NotificationChannelRepository;
import com.monit.pingbell.notification.type.NotificationChannelType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationChannelServiceTest {

    @Mock
    private NotificationChannelRepository channelRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private NotificationChannelService channelService;

    @Test
    void updateChannelChangesTargetAndEnablesChannel() {
        Member member = Member.builder()
                .email("old@example.com")
                .password("password")
                .build();
        NotificationChannel channel = new NotificationChannel(member, NotificationChannelType.EMAIL, "old@example.com");
        channel.disable();

        when(channelRepository.findByPublicIdAndMemberId(channel.getPublicId(), member.getId()))
                .thenReturn(Optional.of(channel));

        channelService.updateChannel(
                member.getId(),
                channel.getPublicId(),
                new NotificationChannelUpdateRequest("new@example.com")
        );

        assertThat(channel.getTarget()).isEqualTo("new@example.com");
        assertThat(channel.isEnabled()).isTrue();
    }

    @Test
    void deleteChannelDisablesChannel() {
        Member member = Member.builder()
                .email("user@example.com")
                .password("password")
                .build();
        NotificationChannel channel = new NotificationChannel(member, NotificationChannelType.EMAIL, "user@example.com");

        when(channelRepository.findByPublicIdAndMemberId(channel.getPublicId(), member.getId()))
                .thenReturn(Optional.of(channel));

        channelService.deleteChannel(member.getId(), channel.getPublicId());

        assertThat(channel.isEnabled()).isFalse();
    }
}
