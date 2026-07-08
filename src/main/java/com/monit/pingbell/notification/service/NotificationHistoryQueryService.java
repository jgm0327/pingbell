package com.monit.pingbell.notification.service;

import com.monit.pingbell.global.common.PageResponse;
import com.monit.pingbell.notification.domain.NotificationHistory;
import com.monit.pingbell.notification.dto.NotificationHistoryResponse;
import com.monit.pingbell.notification.repository.NotificationHistoryRepository;
import com.monit.pingbell.notification.type.NotificationFailureType;
import com.monit.pingbell.notification.type.NotificationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationHistoryQueryService {

    private final NotificationHistoryRepository historyRepository;

    @Transactional(readOnly = true)
    public PageResponse<NotificationHistoryResponse> getHistories(
            Long memberId,
            NotificationStatus status,
            NotificationFailureType failureType,
            Pageable pageable
    ) {
        validateFailureType(status, failureType);

        var histories = failureType == null
                ? findByStatus(memberId, status, pageable)
                : findByFailureType(memberId, failureType, pageable);

        return PageResponse.from(
                histories.map(NotificationHistoryResponse::from)
        );
    }

    private void validateFailureType(NotificationStatus status, NotificationFailureType failureType) {
        if (failureType != null && status != NotificationStatus.FAILED) {
            throw new IllegalArgumentException("failureType can only be used with status=FAILED");
        }
    }

    private Page<NotificationHistory> findByStatus(
            Long memberId,
            NotificationStatus status,
            Pageable pageable
    ) {
        return status == null
                ? historyRepository.findAllByChannelMemberId(memberId, pageable)
                : historyRepository.findAllByChannelMemberIdAndStatus(memberId, status, pageable);
    }

    private Page<NotificationHistory> findByFailureType(
            Long memberId,
            NotificationFailureType failureType,
            Pageable pageable
    ) {
        return switch (failureType) {
            case CHANNEL_DISABLED -> historyRepository.findChannelDisabledFailuresByChannelMemberId(memberId, pageable);
            case SEND_FAILED -> historyRepository.findSendFailedFailuresByChannelMemberId(memberId, pageable);
            case RETRY_EXHAUSTED -> historyRepository.findRetryExhaustedFailuresByChannelMemberId(memberId, pageable);
        };
    }
}
