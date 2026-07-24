package com.monit.pingbell.runbook.service;

import com.monit.pingbell.runbook.exception.RunbookException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

public class RunbookContentValidatorTest {
    private final RunbookContentValidator validator = new RunbookContentValidator();

    @Test
    void allowsSyntheticRunbook() {
        assertThatCode(() -> validator.validate("합성 timeout 대응", "order-api", List.of("TIMEOUT"), validContent()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsCredentialsAndProductionAddress() {
        assertThatThrownBy(() -> validator.validate("title", "order-api", List.of("TIMEOUT"),
                validContent() + "\nAuthorization: Bearer secret-value"))
                .isInstanceOf(RunbookException.class)
                .extracting("code").isEqualTo("RUNBOOK_SENSITIVE_DATA_DETECTED");

        assertThatThrownBy(() -> validator.validate("title", "order-api", List.of("TIMEOUT"),
                validContent() + "\nhttps://admin.company.com/ops"))
                .isInstanceOf(RunbookException.class);
    }

    @Test
    void rejectsMissingOrReorderedSections() {
        assertThatThrownBy(() -> validator.validate("title", "order-api", List.of("TIMEOUT"),
                "## 영향\nnone\n## 증상\nslow"))
                .isInstanceOf(RunbookException.class)
                .extracting("code").isEqualTo("RUNBOOK_STRUCTURE_INVALID");
    }

    public static String validContent() {
        return """
                ## 증상
                timeout이 관측된다.
                ## 영향
                영향 범위를 확인한다.
                ## 확인 절차
                비민감 지표를 읽기 전용으로 확인한다.
                ## 안전한 완화
                승인 후 수동으로 완화한다.
                ## 복구 검증
                health와 사용자 흐름을 확인한다.
                ## 재발 방지
                관측 항목을 검토한다.
                ## 참고 링크
                https://docs.example.invalid/runbook
                """;
    }
}
