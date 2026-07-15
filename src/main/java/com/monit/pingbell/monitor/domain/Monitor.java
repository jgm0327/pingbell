
package com.monit.pingbell.monitor.domain;

import com.monit.pingbell.global.common.BaseTimeEntity;
import com.monit.pingbell.member.domain.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "monitors")
public class Monitor extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Member member;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(nullable = false)
    private Integer intervalSeconds;

    @Column(nullable = false)
    private Integer timeoutMillis;

    @Column(nullable = false)
    private Integer failureThreshold;

    @Column(nullable = false)
    private Integer recoveryThreshold;

    @Column(nullable = false)
    private int recoveryCount;

    @Column(nullable = false)
    private int failureCount;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private MonitorStatus status;

    @Column(nullable = false)
    private LocalDateTime nextCheckAt;

    private LocalDateTime deletedAt;

    @Builder
    public Monitor(
            Member member,
            String name,
            String url,
            Integer intervalSeconds,
            Integer timeoutMillis,
            Integer failureThreshold,
            Integer recoveryThreshold,
            MonitorStatus status,
            LocalDateTime nextCheckAt
    ) {
        this.member = member;
        this.name = name;
        this.url = url;
        this.intervalSeconds = intervalSeconds;
        this.timeoutMillis = timeoutMillis;
        this.failureThreshold = failureThreshold;
        this.recoveryThreshold = recoveryThreshold;
        this.status = status;
        this.nextCheckAt = nextCheckAt;
    }

    public void updateNextCheckedAt(LocalDateTime now) {
        this.nextCheckAt = now;
    }

    public void update(
            String name,
            String url,
            Integer intervalSeconds,
            Integer timeoutMillis,
            Integer failureThreshold,
            Integer recoveryThreshold,
            LocalDateTime nextCheckAt
    ) {
        this.name = name;
        this.url = url;
        this.intervalSeconds = intervalSeconds;
        this.timeoutMillis = timeoutMillis;
        this.failureThreshold = failureThreshold;
        this.recoveryThreshold = recoveryThreshold;
        this.nextCheckAt = nextCheckAt;
    }

    public void pause() {
        this.status = MonitorStatus.PAUSED;
    }

    public void activate(LocalDateTime nextCheckAt) {
        this.status = MonitorStatus.ACTIVE;
        this.nextCheckAt = nextCheckAt;
        this.failureCount = 0;
        this.recoveryCount = 0;
    }

    public void delete(LocalDateTime deletedAt) {
        this.status = MonitorStatus.PAUSED;
        this.deletedAt = deletedAt;
    }

    public boolean canOpenIncident() {
        return this.status == MonitorStatus.ACTIVE
                && this.failureCount >= this.failureThreshold;
    }

    public boolean canRecover() {
        return this.status == MonitorStatus.DOWN
                && this.recoveryCount >= this.recoveryThreshold;
    }

    public void recover() {
        this.recoveryCount = 0;
        this.failureCount = 0;
        this.status = MonitorStatus.ACTIVE;
    }

    public void recordSuccess() {
        this.recoveryCount++;
        this.failureCount = 0;
    }

    public void recordFailure() {
        this.failureCount++;
        this.recoveryCount = 0;
    }

    public void markDown() {
        this.status = MonitorStatus.DOWN;
    }
}
