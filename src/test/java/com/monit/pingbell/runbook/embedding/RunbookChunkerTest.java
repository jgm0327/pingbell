package com.monit.pingbell.runbook.embedding;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RunbookChunkerTest {
    private final RunbookChunker chunker = new RunbookChunker(180);

    @Test
    void sameInputProducesStableBoundariesIdsOrdersAndHashes() {
        String content = content("timeout이 반복된다. ".repeat(30));

        List<RunbookChunk> first = chunker.chunk(10L, "rb-timeout", 3, content);
        List<RunbookChunk> second = chunker.chunk(10L, "rb-timeout", 3,
                content.replace("\n", "\r\n"));

        assertThat(second).isEqualTo(first);
        assertThat(first).extracting(RunbookChunk::chunkOrder)
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, first.size()).boxed().toList());
        assertThat(first).extracting(RunbookChunk::chunkId).doesNotHaveDuplicates();
        assertThat(first).allSatisfy(chunk -> {
            assertThat(chunk.tenantId()).isEqualTo(10L);
            assertThat(chunk.documentId()).isEqualTo("rb-timeout");
            assertThat(chunk.version()).isEqualTo(3);
            assertThat(chunk.contentHash()).hasSize(64);
        });
    }

    @Test
    void preservesRequiredSectionMeaningAcrossLongAndEmptySections() {
        List<RunbookChunk> chunks = chunker.chunk(20L, "rb-sections", 1,
                content("긴 증상 설명과 관측값. ".repeat(40)).replace("영향 내용", ""));

        assertThat(chunks.size()).isGreaterThan(7);
        assertThat(chunks).extracting(RunbookChunk::sectionTitle)
                .containsAll(RunbookChunker.REQUIRED_SECTIONS);
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.content()).startsWith("## " + chunk.sectionTitle()));
        assertThat(chunks.stream()
                .filter(chunk -> chunk.sectionTitle().equals("영향"))
                .map(RunbookChunk::content))
                .containsExactly("## 영향");
    }

    private String content(String symptom) {
        return """
                # 합성 Runbook
                ## 증상
                %s
                ## 영향
                영향 내용
                ## 확인 절차
                1. 읽기 전용 지표를 확인한다.
                2. 기대 결과를 비교한다.
                ## 안전한 완화
                승인 후 수동으로 완화한다.
                ## 복구 검증
                health와 사용자 흐름을 검증한다.
                ## 재발 방지
                관측 항목을 개선한다.
                ## 참고 링크
                https://docs.example.invalid/runbook
                """.formatted(symptom);
    }
}
