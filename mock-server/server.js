'use strict';

/**
 * Dependency-free mock HTTP target for manually testing Pingbell's monitoring/incident flow.
 * Register this server's URL as a Monitor, then use the /_control panel (or query params) to
 * flip its behavior between healthy, slow, erroring, flaky, and hanging (timeout) without
 * restarting anything or touching the real target.
 *
 * Run: node server.js            (PORT env var overrides the default 4000)
 */

const http = require('http');
const { URL } = require('url');

const PORT = Number(process.env.PORT) || 4000;

const state = {
  mode: 'ok', // ok | error | slow | flaky | hang
  statusCode: 500,
  delayMs: 8000,
  failRate: 0.5,
};

let requestCount = 0;
let lastRequestAt = null;

function json(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(payload);
}

function controlPage() {
  const option = (value, label) =>
    `<label><input type="radio" name="mode" value="${value}" ${state.mode === value ? 'checked' : ''}> ${label}</label><br>`;

  return `<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<title>Mock Target Control</title>
<style>
  body { font-family: system-ui, sans-serif; max-width: 640px; margin: 32px auto; padding: 0 16px; }
  fieldset { margin-bottom: 16px; }
  code { background: #eee; padding: 1px 5px; border-radius: 4px; }
  .status { background: #f5f5f5; border: 1px solid #ddd; border-radius: 6px; padding: 12px 16px; }
</style>
</head>
<body>
  <h1>Mock Target Control</h1>
  <p>Pingbell Monitor로 등록한 이 서버의 응답 동작을 바꿉니다. 등록한 URL 전체(예: <code>/</code>, <code>/health</code>, 아무 경로)가 이 설정을 그대로 따릅니다.</p>

  <div class="status">
    <strong>현재 상태</strong><br>
    mode = <code>${state.mode}</code>, statusCode = <code>${state.statusCode}</code>,
    delayMs = <code>${state.delayMs}</code>, failRate = <code>${state.failRate}</code><br>
    받은 요청 수: ${requestCount}, 마지막 요청: ${lastRequestAt ?? '-'}
  </div>

  <form method="get" action="/_control/set">
    <fieldset>
      <legend>동작 모드</legend>
      ${option('ok', '정상 (즉시 200 OK)')}
      ${option('slow', '지연 응답 (delayMs 만큼 기다린 뒤 200 OK — SLOW_RESPONSE / TIMEOUT 테스트)')}
      ${option('error', '에러 응답 (statusCode 로 즉시 실패 — HTTP_ERROR 테스트)')}
      ${option('flaky', '불안정 (failRate 확률로 실패, 나머지는 정상)')}
      ${option('hang', '응답 안 함 (연결만 열어두고 끝없이 대기 — TIMEOUT 테스트)')}
    </fieldset>
    <label>statusCode: <input type="number" name="statusCode" value="${state.statusCode}" min="400" max="599"></label><br><br>
    <label>delayMs: <input type="number" name="delayMs" value="${state.delayMs}" min="0" max="120000"></label><br><br>
    <label>failRate (0.0 ~ 1.0): <input type="number" name="failRate" value="${state.failRate}" min="0" max="1" step="0.1"></label><br><br>
    <button type="submit">적용</button>
  </form>

  <p>API로 직접 바꾸려면: <code>GET /_control/set?mode=slow&amp;delayMs=6000</code>, 상태 확인은
  <code>GET /_control/status</code> (JSON).</p>
</body>
</html>`;
}

const server = http.createServer((req, res) => {
  const parsed = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const path = parsed.pathname;

  if (path === '/_control') {
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(controlPage());
    return;
  }

  if (path === '/_control/set') {
    const q = parsed.searchParams;
    if (q.has('mode')) state.mode = q.get('mode');
    if (q.has('statusCode')) state.statusCode = Number(q.get('statusCode')) || state.statusCode;
    if (q.has('delayMs')) state.delayMs = Number(q.get('delayMs')) || 0;
    if (q.has('failRate')) state.failRate = Math.min(1, Math.max(0, Number(q.get('failRate'))));
    res.writeHead(302, { Location: '/_control' });
    res.end();
    return;
  }

  if (path === '/_control/status') {
    json(res, 200, { ...state, requestCount, lastRequestAt });
    return;
  }

  // Everything else is the "monitored" endpoint — whatever path the Monitor URL points at.
  requestCount += 1;
  lastRequestAt = new Date().toISOString();
  console.log(`[${lastRequestAt}] ${req.method} ${req.url} -> mode=${state.mode}`);

  switch (state.mode) {
    case 'hang':
      // Never write a response. The Monitor's own timeoutMillis is what ends this.
      return;

    case 'error':
      json(res, state.statusCode, { status: 'error', statusCode: state.statusCode });
      return;

    case 'slow':
      setTimeout(() => {
        json(res, 200, { status: 'ok', delayedMs: state.delayMs });
      }, state.delayMs);
      return;

    case 'flaky': {
      const failed = Math.random() < state.failRate;
      json(res, failed ? state.statusCode : 200, {
        status: failed ? 'flaky-fail' : 'flaky-ok',
        failRate: state.failRate,
      });
      return;
    }

    case 'ok':
    default:
      json(res, 200, { status: 'ok', timestamp: lastRequestAt });
  }
});

server.listen(PORT, () => {
  console.log(`Mock target server listening on :${PORT}`);
  console.log(`Control panel: http://localhost:${PORT}/_control`);
});
