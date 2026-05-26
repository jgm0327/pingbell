package com.monit.pingbell.check;

import com.monit.pingbell.global.common.BaseTimeEntity;
import com.monit.pingbell.monitor.Monitor;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "check_results")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class CheckResult extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monitor_id")
    private Monitor monitor;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private CheckStatus status;

    private Integer httpStatus;

    @Column(nullable = false)
    private Long responseTimeMs;

    private String errorMessage;

    @Builder
    public CheckResult(Monitor monitor, CheckStatus status, Integer httpStatus,
                       Long responseTimeMs, String errorMessage) {
        this.monitor = monitor;
        this.status = status;
        this.httpStatus = httpStatus;
        this.responseTimeMs = responseTimeMs;
        this.errorMessage = errorMessage;
    }
}
