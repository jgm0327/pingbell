package com.monit.pingbell.notification.crypto;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class NotificationTargetEncryptionBackfill implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public NotificationTargetEncryptionBackfill(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<LegacyTargetRow> legacyRows = jdbcTemplate.query(
                """
                        select id, target
                        from notification_channels
                        where target is not null
                          and target not like 'enc:v1:%'
                        """,
                (rs, rowNum) -> new LegacyTargetRow(rs.getLong("id"), rs.getString("target"))
        );

        for (LegacyTargetRow row : legacyRows) {
            jdbcTemplate.update(
                    "update notification_channels set target = ? where id = ?",
                    NotificationTargetCrypto.encrypt(row.target()),
                    row.id()
            );
        }
    }

    private record LegacyTargetRow(Long id, String target) {
    }
}
