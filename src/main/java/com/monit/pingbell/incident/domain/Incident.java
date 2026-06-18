package com.monit.pingbell.incident.domain;

import com.monit.pingbell.global.common.BaseTimeEntity;
import com.monit.pingbell.monitor.domain.Monitor;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "incidents")
@EntityListeners(AuditingEntityListener.class)
public class Incident extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "monitor_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Monitor monitor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime resolvedAt;

    @Column(length = 1000)
    private String lastErrorMessage;

    @Builder
    public Incident(Monitor monitor, IncidentStatus status, LocalDateTime startedAt, String lastErrorMessage) {
        this.monitor = monitor;
        this.status = status;
        this.startedAt = startedAt;
        this.lastErrorMessage = lastErrorMessage;
    }

    public void resolve(LocalDateTime resolvedAt) {
        this.status = IncidentStatus.RESOLVED;
        this.resolvedAt = resolvedAt;
    }
}
