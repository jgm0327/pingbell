package com.monit.pingbell.notification.service;

import com.monit.pingbell.member.domain.Member;
import com.monit.pingbell.notification.domain.NotificationChannel;
import com.monit.pingbell.notification.dto.NotificationMessage;
import com.monit.pingbell.notification.repository.NotificationChannelRepository;
import com.monit.pingbell.notification.sender.NotificationSender;
import com.monit.pingbell.notification.type.NotificationChannelType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationChannelTestSendServiceTest {

    @Mock
    private NotificationChannelRepository channelRepository;

    @Mock
    private NotificationSender sender;

    @Test
    void sendTestUsesOwnedChannelSender() {
        Member member = Member.builder()
                .email("user@example.com")
                .password("password")
                .build();
        NotificationChannel channel = new NotificationChannel(member, NotificationChannelType.EMAIL, "user@example.com");
        NotificationChannelTestSendService service = new NotificationChannelTestSendService(channelRepository, List.of(sender));

        when(channelRepository.findByPublicIdAndMemberId(channel.getPublicId(), member.getId()))
                .thenReturn(Optional.of(channel));
        when(sender.supports(NotificationChannelType.EMAIL)).thenReturn(true);

        var response = service.sendTest(member.getId(), channel.getPublicId());

        ArgumentCaptor<NotificationMessage> messageCaptor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(sender).send(org.mockito.ArgumentMatchers.eq(channel), messageCaptor.capture());
        assertThat(messageCaptor.getValue().title()).contains("Notification channel test");
        assertThat(response.success()).isTrue();
        assertThat(response.publicId()).isEqualTo(channel.getPublicId());
        assertThat(response.type()).isEqualTo(NotificationChannelType.EMAIL);
    }

    @Test
    void sendTestReturnsFailureResponseWhenSenderFails() {
        Member member = Member.builder()
                .email("user@example.com")
                .password("password")
                .build();
        NotificationChannel channel = new NotificationChannel(member, NotificationChannelType.SLACK, "https://hooks.slack.com/services/test");
        NotificationChannelTestSendService service = new NotificationChannelTestSendService(channelRepository, List.of(sender));

        when(channelRepository.findByPublicIdAndMemberId(channel.getPublicId(), member.getId()))
                .thenReturn(Optional.of(channel));
        when(sender.supports(NotificationChannelType.SLACK)).thenReturn(true);
        doThrow(new IllegalStateException("webhook rejected")).when(sender)
                .send(org.mockito.ArgumentMatchers.eq(channel), org.mockito.ArgumentMatchers.any(NotificationMessage.class));

        var response = service.sendTest(member.getId(), channel.getPublicId());

        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("webhook rejected");
    }

    @Test
    void sendTestRejectsDisabledChannel() {
        Member member = Member.builder()
                .email("user@example.com")
                .password("password")
                .build();
        NotificationChannel channel = new NotificationChannel(member, NotificationChannelType.EMAIL, "user@example.com");
        channel.disable();
        NotificationChannelTestSendService service = new NotificationChannelTestSendService(channelRepository, List.of(sender));

        when(channelRepository.findByPublicIdAndMemberId(channel.getPublicId(), member.getId()))
                .thenReturn(Optional.of(channel));

        assertThatThrownBy(() -> service.sendTest(member.getId(), channel.getPublicId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Disabled notification channels");
    }
}
