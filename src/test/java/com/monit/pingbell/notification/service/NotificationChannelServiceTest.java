package com.monit.pingbell.notification.service;

import com.monit.pingbell.member.domain.Member;
import com.monit.pingbell.member.repository.MemberRepository;
import com.monit.pingbell.notification.domain.NotificationChannel;
import com.monit.pingbell.notification.dto.NotificationChannelCreateRequest;
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
import static org.mockito.ArgumentMatchers.any;
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
    void createChannelMasksEmailTargetInResponse() {
        Member member = Member.builder()
                .email("user@example.com")
                .password("password")
                .build();

        when(memberRepository.findById(member.getId())).thenReturn(Optional.of(member));
        when(channelRepository.save(any(NotificationChannel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = channelService.createChannel(
                member.getId(),
                new NotificationChannelCreateRequest(NotificationChannelType.EMAIL, "notify@example.com")
        );

        assertThat(response.type()).isEqualTo(NotificationChannelType.EMAIL);
        assertThat(response.maskedTarget()).isEqualTo("no****@example.com");
        assertThat(response.enabled()).isTrue();
    }

    @Test
    void createChannelSupportsSlackWebhookUrl() {
        Member member = Member.builder()
                .email("user@example.com")
                .password("password")
                .build();

        when(memberRepository.findById(member.getId())).thenReturn(Optional.of(member));
        when(channelRepository.save(any(NotificationChannel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = channelService.createChannel(
                member.getId(),
                new NotificationChannelCreateRequest(NotificationChannelType.SLACK, "https://hooks.slack.com/services/test")
        );

        assertThat(response.type()).isEqualTo(NotificationChannelType.SLACK);
        assertThat(response.maskedTarget()).isEqualTo("****test");
        assertThat(response.enabled()).isTrue();
    }

    @Test
    void createChannelSupportsDiscordWebhookUrl() {
        Member member = Member.builder()
                .email("user@example.com")
                .password("password")
                .build();

        when(memberRepository.findById(member.getId())).thenReturn(Optional.of(member));
        when(channelRepository.save(any(NotificationChannel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = channelService.createChannel(
                member.getId(),
                new NotificationChannelCreateRequest(NotificationChannelType.DISCORD, "https://discord.com/api/webhooks/test")
        );

        assertThat(response.type()).isEqualTo(NotificationChannelType.DISCORD);
        assertThat(response.maskedTarget()).isEqualTo("****test");
        assertThat(response.enabled()).isTrue();
    }

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
    void updateSlackChannelChangesWebhookUrlAndEnablesChannel() {
        Member member = Member.builder()
                .email("user@example.com")
                .password("password")
                .build();
        NotificationChannel channel = new NotificationChannel(member, NotificationChannelType.SLACK, "https://hooks.slack.com/services/old");
        channel.disable();

        when(channelRepository.findByPublicIdAndMemberId(channel.getPublicId(), member.getId()))
                .thenReturn(Optional.of(channel));

        channelService.updateChannel(
                member.getId(),
                channel.getPublicId(),
                new NotificationChannelUpdateRequest("https://hooks.slack.com/services/new")
        );

        assertThat(channel.getTarget()).isEqualTo("https://hooks.slack.com/services/new");
        assertThat(channel.isEnabled()).isTrue();
    }

    @Test
    void updateDiscordChannelChangesWebhookUrlAndEnablesChannel() {
        Member member = Member.builder()
                .email("user@example.com")
                .password("password")
                .build();
        NotificationChannel channel = new NotificationChannel(member, NotificationChannelType.DISCORD, "https://discord.com/api/webhooks/old");
        channel.disable();

        when(channelRepository.findByPublicIdAndMemberId(channel.getPublicId(), member.getId()))
                .thenReturn(Optional.of(channel));

        channelService.updateChannel(
                member.getId(),
                channel.getPublicId(),
                new NotificationChannelUpdateRequest("https://discord.com/api/webhooks/new")
        );

        assertThat(channel.getTarget()).isEqualTo("https://discord.com/api/webhooks/new");
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
