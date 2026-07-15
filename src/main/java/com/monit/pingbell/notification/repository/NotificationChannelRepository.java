package com.monit.pingbell.notification.repository;

import com.monit.pingbell.member.domain.Member;
import com.monit.pingbell.notification.domain.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationChannelRepository extends JpaRepository<NotificationChannel, Long> {

    List<NotificationChannel> findAllByMemberAndEnabledTrue(Member member);

    List<NotificationChannel> findAllByMemberIdOrderByIdDesc(Long memberId);

    List<NotificationChannel> findAllByMemberIdAndEnabledOrderByIdDesc(Long memberId, boolean enabled);

    Optional<NotificationChannel> findByPublicIdAndMemberId(UUID publicId, Long memberId);
}
