
package com.monit.pingbell.monitor;

import com.monit.pingbell.global.common.BaseTimeEntity;
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

    @Column(nullable = false)
    private Long userId;

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

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private MonitorStatus status;

    @Column(nullable = false)
    private LocalDateTime nextCheckAt;

    @Builder
    public Monitor(
            Long userId,
            String name,
            String url,
            Integer intervalSeconds,
            Integer timeoutMillis,
            Integer failureThreshold,
            Integer recoveryThreshold,
            MonitorStatus status,
            LocalDateTime nextCheckAt
    ) {
        this.userId = userId;
        this.name = name;
        this.url = url;
        this.intervalSeconds = intervalSeconds;
        this.timeoutMillis = timeoutMillis;
        this.failureThreshold = failureThreshold;
        this.recoveryThreshold = recoveryThreshold;
        this.status = status;
        this.nextCheckAt = nextCheckAt;
    }
}
