package com.monit.pingbell.notification.service;

import com.monit.pingbell.notification.dto.NotificationHistoryResponse;
import com.monit.pingbell.notification.repository.NotificationHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationHistoryQueryService {

    private final NotificationHistoryRepository historyRepository;

    @Transactional(readOnly = true)
    public List<NotificationHistoryResponse> getHistories(Long memberId) {
        return historyRepository.findAllByChannelMemberIdOrderByIdDesc(memberId)
                .stream()
                .map(NotificationHistoryResponse::from)
                .toList();
    }
}
