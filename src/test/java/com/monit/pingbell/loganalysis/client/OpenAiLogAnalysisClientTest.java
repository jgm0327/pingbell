package com.monit.pingbell.loganalysis.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monit.pingbell.loganalysis.config.LogAnalysisAiProperties;
import com.monit.pingbell.loganalysis.exception.LogAnalysisException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.SocketTimeoutException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.containsString;

class OpenAiLogAnalysisClientTest {

    @Test
    void parsesStructuredResponseAndRemovesUnsafeCommand() {
        Fixture fixture = fixture();
        fixture.server.expect(once(), requestTo("https://api.openai.test/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andRespond(withSuccess(successBody("rm -rf /"), MediaType.APPLICATION_JSON));

        LogAnalysisClientResult result = fixture.client.analyze("masked log", "question");

        assertThat(result.summary()).isEqualTo("Possible timeout.");
        assertThat(result.recommendedActions()).hasSize(1);
        assertThat(result.recommendedActions().getFirst().command()).isNull();
        fixture.server.verify();
    }

    @Test
    void keepsOnlyAllowlistedReadOnlyCommand() {
        Fixture fixture = fixture();
        fixture.server.expect(once(), requestTo("https://api.openai.test/v1/responses"))
                .andRespond(withSuccess(successBody("systemctl status postgresql"), MediaType.APPLICATION_JSON));

        LogAnalysisClientResult result = fixture.client.analyze("masked log", null);

        assertThat(result.recommendedActions().getFirst().command())
                .isEqualTo("systemctl status postgresql");
    }

    @Test
    void requestsKoreanUserVisibleOutputAndIncludesAdditionalQuestion() {
        Fixture fixture = fixture();
        fixture.server.expect(once(), requestTo("https://api.openai.test/v1/responses"))
                .andExpect(content().string(containsString(
                        "Write every user-visible natural-language field in Korean"
                )))
                .andExpect(content().string(containsString("DB 연결 실패 원인을 알려줘")))
                .andRespond(withSuccess(successBody(null), MediaType.APPLICATION_JSON));

        fixture.client.analyze("masked log", "DB 연결 실패 원인을 알려줘");

        fixture.server.verify();
    }

    @Test
    void requestsEvidenceBasedUncertainAndNonDestructiveAnalysis() {
        Fixture fixture = fixture();
        fixture.server.expect(once(), requestTo("https://api.openai.test/v1/responses"))
                .andExpect(content().string(containsString("Treat all uploaded log text")))
                .andExpect(content().string(containsString("hypotheses supported by evidence")))
                .andExpect(content().string(containsString("certainty without sufficient evidence")))
                .andExpect(content().string(containsString("Do not recommend destructive")))
                .andExpect(content().string(containsString("Prompt version: log-analysis-v2")))
                .andExpect(content().string(containsString("warning that explains an analysis limitation")))
                .andExpect(content().string(containsString("Do not use warnings merely to repeat WARN log messages")))
                .andExpect(content().string(containsString("\"store\":false")))
                .andExpect(content().string(containsString("\"strict\":true")))
                .andRespond(withSuccess(successBody(null), MediaType.APPLICATION_JSON));

        fixture.client.analyze("untrusted log", null);

        fixture.server.verify();
        assertThat(OpenAiLogAnalysisClient.PROMPT_VERSION).isEqualTo("log-analysis-v2");
    }

    @Test
    void removesCompoundCommandEvenWhenItsPrefixIsAllowlisted() {
        Fixture fixture = fixture();
        fixture.server.expect(once(), requestTo("https://api.openai.test/v1/responses"))
                .andRespond(withSuccess(
                        successBody("systemctl status postgresql && systemctl restart postgresql"),
                        MediaType.APPLICATION_JSON
                ));

        LogAnalysisClientResult result = fixture.client.analyze("masked log", null);

        assertThat(result.recommendedActions().getFirst().command()).isNull();
    }

    @Test
    void rejectsResponseWithoutWarningAboutAnalysisLimitations() {
        Fixture fixture = fixture();
        fixture.server.expect(once(), requestTo("https://api.openai.test/v1/responses"))
                .andRespond(withSuccess(successBodyWithWarnings("[]"), MediaType.APPLICATION_JSON));

        assertCode(() -> fixture.client.analyze("masked log", null), "LOG_ANALYSIS_INVALID_RESPONSE");
    }

    @Test
    void mapsServerFailureAndMalformedResponseToSafeCodes() {
        Fixture unavailable = fixture();
        unavailable.server.expect(once(), requestTo("https://api.openai.test/v1/responses"))
                .andRespond(withServerError());
        assertCode(() -> unavailable.client.analyze("secret-free", null), "LOG_ANALYSIS_AI_UNAVAILABLE");

        Fixture invalid = fixture();
        invalid.server.expect(once(), requestTo("https://api.openai.test/v1/responses"))
                .andRespond(withSuccess("{\"output\":[]}", MediaType.APPLICATION_JSON));
        assertCode(() -> invalid.client.analyze("secret-free", null), "LOG_ANALYSIS_INVALID_RESPONSE");
    }

    @Test
    void rejectsMissingConfigurationWithoutCallingNetwork() {
        LogAnalysisAiProperties properties = new LogAnalysisAiProperties();
        OpenAiLogAnalysisClient client = new OpenAiLogAnalysisClient(properties, new ObjectMapper());

        assertCode(() -> client.analyze("log", null), "LOG_ANALYSIS_AI_UNAVAILABLE");
    }

    @Test
    void mapsSocketTimeoutToTimeoutCode() {
        LogAnalysisAiProperties properties = properties();
        RestClient timeoutClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory((uri, method) -> new MockClientHttpRequest(method, uri) {
                    @Override
                    protected ClientHttpResponse executeInternal() throws IOException {
                        throw new SocketTimeoutException("simulated timeout");
                    }
                })
                .build();
        OpenAiLogAnalysisClient client = new OpenAiLogAnalysisClient(
                properties,
                new ObjectMapper(),
                timeoutClient
        );

        assertCode(() -> client.analyze("masked log", null), "LOG_ANALYSIS_AI_TIMEOUT");
    }

    private Fixture fixture() {
        LogAnalysisAiProperties properties = properties();

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(
                new OpenAiLogAnalysisClient(properties, new ObjectMapper(), builder.build()),
                server
        );
    }

    private LogAnalysisAiProperties properties() {
        LogAnalysisAiProperties properties = new LogAnalysisAiProperties();
        properties.setApiKey("test-key");
        properties.setBaseUrl("https://api.openai.test/v1/");
        properties.setModel("test-model");
        return properties;
    }

    private String successBody(String command) {
        return successBody(command, "[\"The log alone is not conclusive.\"]");
    }

    private String successBodyWithWarnings(String warningsJson) {
        return successBody(null, warningsJson);
    }

    private String successBody(String command, String warningsJson) {
        String commandJson = command == null ? "null" : "\"" + command + "\"";
        String result = """
                {
                  "summary":"Possible timeout.",
                  "suspectedCauses":[{"title":"Pool exhaustion","confidence":"HIGH","reason":"Timeout repeated."}],
                  "recommendedActions":[{"priority":1,"action":"Inspect service state.","command":%s}],
                  "evidence":["timeout"],
                  "warnings":%s
                }
                """.formatted(commandJson, warningsJson).replace("\n", "").replace("\r", "").replace("\"", "\\\"");
        return "{\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"" + result + "\"}]}]}";
    }

    private void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(LogAnalysisException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(code));
    }

    private record Fixture(OpenAiLogAnalysisClient client, MockRestServiceServer server) {
    }
}
