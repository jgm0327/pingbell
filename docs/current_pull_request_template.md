## 작업 내용

`docs/ai/rag-implementation-issues.md`의 Issue 6(합성 fixture 기반 RAG 전후 품질 평가)을 구현했다. 이미 완료된 RAG context/reference 연결(Issue 5)이 실제로 로그 분석 품질을 개선하는지, reference 오류나 교차 tenant 노출 같은 부작용은 없는지를 기존 4개 합성 fixture와 기존 0/1/2 rubric(`docs/ai/log-analysis-quality-evaluation.md`)으로 측정했다.

## 변경 사항

- **fixture 공유 클래스 정리**: `LogAnalysisQualityFixtureTest`에 있던 `scenarios.json`/`.log` 로딩 로직을 `QualityScenario`(record), `QualityFixtures`(로더)로 분리해 새 테스트와 공유하도록 리팩터링.
- **`RagReferenceIntegrationTest`(신규, 기본 CI)**: mock 기반 회귀 테스트. `LogAnalysisService`가 (스크립트로 조작한) 모델의 인용 chunk id 전체를 그대로 노출하지 않고 `RunbookReferenceValidator`가 승인한 것만 응답에 담는지, Runbook 검색이 빈 결과일 때도 안전하게 fallback 하는지 검증한다. 외부 API를 호출하지 않으므로 항상 실행된다.
- **`RagQualityEvaluationTest`(신규, `LOG_ANALYSIS_AI_API_KEY` 있을 때만 실행)**: 4개 fixture 각각에 직접 작성한 Runbook context chunk 1개를 실제로 주입해 실 모델(`gpt-4o-mini`)을 2회씩(총 8회) 호출한다. 매 실행마다 구조화 응답 완전성, reference가 실제 제공 context로만 구성되는지, 프롬프트에 없는 decoy chunk id를 인용하지 않는지(교차 tenant 노출 방지)를 자동으로 검증한다.
- **`docs/ai/rag-quality-evaluation.md`(신규)**: 위 실제 모델 평가 8회의 점수·실패 사유 코드를 기록하고, 기존 비-RAG 기준선(2026-07-14)과 비교했다. 개선된 부분(db-connection-failure, out-of-memory의 조치 순서)과 개선되지 않은 부분(http-5xx의 근거 인용·확신도 표현이 오히려 소폭 하락)을 그대로 기록했다.
- **`docs/ai/rag-implementation-issues.md`**: Issue 6을 완료로 표시.

## API 계약

- API 변경 없음. 기존 `LogAnalysisService`/`LogAnalysisClient`/`RunbookContextService`/`RunbookReferenceValidator` 계약을 그대로 사용해 평가만 추가했다.
- Frontend 영향 없음.

## 테스트 결과

- [x] `./gradlew.bat compileTestJava`
- [x] `./gradlew.bat test --tests "com.monit.pingbell.loganalysis.*"` — 전부 통과, `RagQualityEvaluationTest`는 `LOG_ANALYSIS_AI_API_KEY` 없이 실행 시 자동 skip.
- [x] `LOG_ANALYSIS_AI_API_KEY`를 설정한 로컬 환경에서 `RagQualityEvaluationTest` 실제 실행 — 8회 모두 통과(잘못된 reference 0건, 교차 tenant 노출 0건, 안전성 hard failure 0건).

## 수동 테스트 방법

```powershell
# 기본 CI (API Key 불필요)
.\gradlew.bat test --tests "com.monit.pingbell.loganalysis.*"

# 실제 모델 평가 (API Key 필요, 기본 CI와 분리)
$env:LOG_ANALYSIS_AI_API_KEY="..."
.\gradlew.bat test --tests "com.monit.pingbell.loganalysis.quality.RagQualityEvaluationTest"
```

## 고민한 점

- `LogPreprocessor.process(String)`은 package-private이라 평가 테스트가 있는 `quality` 패키지에서 호출할 수 없었다. 이 fixture들은 `process()`의 유일한 추가 동작(길이 절단)이 적용될 만큼 크지 않으므로, 공개된 `mask()`만 호출하도록 하고 이유를 주석으로 남겼다.
- Runbook 검색(`RunbookContextService`) 자체의 결과 품질은 이번 평가 범위가 아니다. 실제 검색 대신 각 fixture에 맞는 Runbook chunk를 직접 작성해 "검색이 이미 올바른 chunk를 찾아줬다"고 가정한 뒤 RAG 배선(context 주입 → 인용 → 재검증)의 효과만 분리해서 측정했다.
- http-5xx에서 RAG 조건 점수가 비-RAG 기준선보다 낮게 나온 것을 그대로 보고했다(요청사항: "개선되지 않은 항목도 그대로 기록"). Runbook context가 "두 지점을 구분하라"는 절차를 알려주는 것과, 모델이 근거 없이 한쪽을 확신하는 경향을 억제하는 것은 별개의 문제라서 이번 컨텍스트 주입만으로는 해결되지 않았다.
- 반복 실패 기준(`log-analysis-quality-evaluation.md` 5절: 동일 항목이 2개 이상 fixture에서 2회 모두 0점)을 이번 8회로는 충족하지 않아 prompt나 재검증 로직은 바꾸지 않았다.

## Frontend 영향

해당 없음(Backend 테스트/문서 전용 작업).

## 관련 이슈

close #
