package com.monit.pingbell.notification.domain;

import com.monit.pingbell.global.common.BaseTimeEntity;
import com.monit.pingbell.member.domain.Member;
import com.monit.pingbell.notification.type.NotificationChannelType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "notification_channels")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationChannel extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 외부 API에 노출할 식별자
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationChannelType type;

    // EMAIL이면 이메일 주소, SLACK/DISCORD면 Webhook URL
    @Column(name = "target", nullable = false, length = 500)
    private String target;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    public NotificationChannel(
            Member member,
            NotificationChannelType type,
            String target
    ) {
        this.publicId = UUID.randomUUID();
        this.member = member;
        this.type = type;
        this.target = target;
        this.enabled = true;
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    public void updateTarget(String target) {
        this.target = target;
    }
}
