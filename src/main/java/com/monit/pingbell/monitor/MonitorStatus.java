package com.monit.pingbell.monitor;

public enum MonitorStatus {
    ACTIVE,     // 정상적으로 주기적 헬스체크 수행 중
    PAUSED,     // 사용자가 일시 중지한 상태
    DOWN        // 장애로 판단된 상태
}