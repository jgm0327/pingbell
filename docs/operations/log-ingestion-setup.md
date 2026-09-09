# 로그 자동 수집 설정 (Fluent Bit)

Incident가 열리는 순간 사람이 로그를 다시 찾아 업로드하지 않아도 AI가 자동으로 분석하게 하려면,
모니터링 대상 서버에서 최근 로그를 Pingbell로 계속 흘려보내야 한다. 이 문서는 그 설정 절차다.

전체 구현 배경과 이슈 분해는 `docs/ai/log-ingestion-implementation-issues.md` 참고.

## 왜 설정이 필요한가

Pingbell은 지금도 등록한 URL 하나만 호출하는 헬스체크 서비스라, 모니터링 대상 서버 안에 들어가서
로그를 직접 읽을 방법이 없다. 그래서 그 서버 쪽에서 뭔가가 로그를 Pingbell로 밀어줘야(push) 하고,
이 최초 설정은 사람이 한 번은 직접 해야 한다 — Datadog, Sentry 같은 다른 관측 제품도 마찬가지다.

다만 사용자가 실제로 채워야 하는 값은 **로그 파일 경로(또는 Docker 볼륨 경로) 하나뿐**이다. 나머지
(Monitor ID, API Key, Pingbell ingestion URL)는 아래 1번 단계에서 자동으로 채워진다.

## 1. API Key 발급 — 설정 파일도 같이 받는다

```
POST /api/monitors/{monitorId}/api-keys
```

응답에 발급된 키(`apiKey`)뿐 아니라, 그 키가 이미 채워진 설정 파일 두 종류가 함께 온다.

```json
{
  "id": 1,
  "apiKey": "pgbl_...",
  "keyPrefix": "pgbl_abc123",
  "createdAt": "2026-08-25T00:00:00",
  "fluentBitConfig": "[SERVICE]\n    Flush        5\n...",
  "dockerComposeSnippet": "services:\n  fluent-bit-monitor-1:\n..."
}
```

**`apiKey`와 두 설정 필드는 이 응답에서만 볼 수 있다.** 원문 키는 저장하지 않고 해시만 저장하기
때문에 나중에 다시 조회할 수 없다 — 지금 복사해두거나, 아래 설정 파일을 지금 저장해둬야 한다.

## 2. 독립 실행형 (`fluent-bit.conf`)

`fluentBitConfig` 값을 파일로 저장하고, `[INPUT] Path` 한 줄만 실제 로그 파일 경로로 바꾼다.

```ini
[INPUT]
    Name         tail
    Path         /path/to/your/app.log   # <- 여기만 수정
    Tag          pingbell.monitor-12
```

실행:

```bash
fluent-bit -c fluent-bit-monitor-12.conf
```

## 3. Docker 환경 (sidecar)

모니터링 대상 앱이 이미 docker-compose로 떠 있다면 `dockerComposeSnippet`을 추가한다. 2번에서 만든
`.conf` 파일과 같은 디렉터리에 두고, 볼륨의 로그 경로만 실제 위치로 바꾼다.

```yaml
services:
  fluent-bit-monitor-12:
    image: fluent/fluent-bit:3.1
    volumes:
      - ./fluent-bit-monitor-12.conf:/fluent-bit/etc/fluent-bit.conf:ro
      - /path/to/your/app/logs:/var/log/app:ro   # <- 여기만 수정
    restart: unless-stopped
```

### 로그 파일 경로도 몰라도 되는 경우

앱이 파일이 아니라 컨테이너 stdout에만 로그를 찍는다면, Docker의 `fluentd` logging driver로
파일 경로 자체를 없앨 수 있다.

```yaml
services:
  your-app:
    logging:
      driver: fluentd
      options:
        fluentd-address: localhost:24224
```

이 경우 `fluent-bit-monitor-12` 서비스의 `[INPUT]`을 다음으로 바꾼다.

```ini
[INPUT]
    Name    forward
    Listen  0.0.0.0
    Port    24224
```

## 4. 확인

로그가 도착하면 Pingbell이 짧은 TTL(기본 15분, 최근 500줄)의 Redis 버퍼에 담아두고, 이후 이
Monitor에 장애가 발생하는 순간 자동으로 분석한다. 분석 결과는 아래로 조회한다.

```
GET /api/incidents/{incidentId}/log-analysis
```

- 버퍼가 비어 있었으면(로그가 도착하지 않았으면) 404 — 조용히 건너뛴 것이다.
- 분석 중이면 `status: PROCESSING`, 끝나면 `COMPLETED` 또는 `FAILED`.

## 5. 키 관리

```
GET    /api/monitors/{monitorId}/api-keys        # 목록 (원문 키는 안 보임)
DELETE /api/monitors/{monitorId}/api-keys/{keyId} # 즉시 폐기
```

키를 재발급하면(다시 `POST`) 새 설정 파일도 같이 새로 받아야 한다 — 이전 파일의 키는 여전히
유효하니 필요 없어진 키는 명시적으로 폐기한다.

## 참고

- Ingestion endpoint는 `POST /api/v1/monitors/{monitorId}/logs`이며, API Key(`ROLE_INGESTION`)로만
  인증된다 — 사람 로그인 JWT로는 호출할 수 없다.
- 요청 크기 상한(기본 512KB)과 분당 요청 수 상한(기본 60회)이 있다 — `pingbell.log-ingestion.*`
  설정으로 조정 가능하다(`docs/ai/log-ingestion-implementation-issues.md` 참고).
- 로그는 도착 즉시 마스킹된 뒤에만 버퍼에 들어가고, 영구 저장하지 않는다.
