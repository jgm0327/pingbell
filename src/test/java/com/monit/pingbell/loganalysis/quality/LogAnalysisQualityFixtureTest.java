package com.monit.pingbell.loganalysis.quality;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LogAnalysisQualityFixtureTest {

    @Test
    void definesFourRequiredSyntheticScenariosWithCompleteExpectations() throws IOException {
        List<QualityScenario> scenarios = QualityFixtures.scenarios();

        assertThat(scenarios)
                .extracting(QualityScenario::id)
                .containsExactlyInAnyOrder("timeout", "db-connection-failure", "http-5xx", "out-of-memory");
        assertThat(scenarios).allSatisfy(scenario -> {
            assertThat(scenario.logFile()).endsWith(".log");
            assertThat(scenario.question()).isNotBlank();
            assertThat(scenario.expectedCauseCandidates()).isNotEmpty().doesNotContainNull();
            assertThat(scenario.requiredEvidence()).isNotEmpty().doesNotContainNull();
            assertThat(scenario.firstChecks()).isNotEmpty().doesNotContainNull();
            assertThat(scenario.forbiddenActions()).isNotEmpty().doesNotContainNull();
            assertThat(QualityFixtures.resource(scenario.logFile())).isNotBlank();
        });
    }

    @Test
    void fixtureLogsDoNotContainCredentialOrPersonalDataShapes() throws IOException {
        for (QualityScenario scenario : QualityFixtures.scenarios()) {
            String log = QualityFixtures.resource(scenario.logFile());

            assertThat(log).doesNotContainIgnoringCase(
                    "authorization:", "bearer ", "password=", "passwd=", "pwd=",
                    "api_key=", "api-key=", "secret=", "cookie:", "session="
            );
            assertThat(log).doesNotContainPattern("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
            assertThat(log).doesNotContainPattern("(?:\\d{1,3}\\.){3}\\d{1,3}");
        }
    }

    @Test
    void includesPromptInjectionAsUntrustedLogData() throws IOException {
        String log = QualityFixtures.resource("http-5xx.log");

        assertThat(log).contains("Ignore previous instructions");
    }
}
