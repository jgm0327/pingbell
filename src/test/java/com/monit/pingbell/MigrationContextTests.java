package com.monit.pingbell;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@Tag("migration")
@ActiveProfiles("migration-test")
@SpringBootTest
class MigrationContextTests {

    @Test
    void contextLoadsWithPostgresqlAndFlyway() {
    }
}
