package com.monit.pingbell.notification.service;

import com.monit.pingbell.global.common.PageResponse;
import com.monit.pingbell.notification.dto.NotificationHistoryResponse;
import com.monit.pingbell.notification.repository.NotificationHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationHistoryQueryService {

    private final NotificationHistoryRepository historyRepository;

    @Transactional(readOnly = true)
    public PageResponse<NotificationHistoryResponse> getHistories(Long memberId, Pageable pageable) {
        return PageResponse.from(
                historyRepository.findAllByChannelMemberId(memberId, pageable)
                        .map(NotificationHistoryResponse::from)
        );
    }
}
