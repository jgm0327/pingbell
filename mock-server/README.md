# Mock Target Server

A tiny, dependency-free mock HTTP target for manually testing Pingbell's monitoring /
incident-detection flow without needing a real server to point at.

Register its URL as a Monitor, then flip its behavior at runtime via `/_control` — no restart
needed, and Pingbell's scheduler will pick up the new behavior on its next check.

## Run

**With Docker Compose** (reachable from the `app` container as `http://mock-server:4000`):

```bash
docker compose up -d mock-server
```

Host access: `http://localhost:${MOCK_SERVER_PORT:-4100}` (control panel at `/_control`).

**Directly with Node** (no install step — zero dependencies):

```bash
cd mock-server
node server.js            # listens on :4000, override with PORT=xxxx
```

If the backend itself runs directly on the host (not via `docker compose up -d app`), register
`http://localhost:4000/health` (or whatever `PORT` you used) as the Monitor URL instead of the
`mock-server` service name.

## Usage

Register any path on this server as a Monitor URL — `/health`, `/`, anything except `/_control*`
behaves identically and follows whatever mode is currently set.

Open `http://localhost:4100/_control` (or `:4000` if run directly) in a browser for a simple form,
or drive it directly:

```bash
# Healthy again
curl "http://localhost:4100/_control/set?mode=ok"

# Fixed error status — triggers HTTP_ERROR / incident OPEN
curl "http://localhost:4100/_control/set?mode=error&statusCode=500"

# Delayed response — triggers SLOW_RESPONSE or TIMEOUT depending on the Monitor's timeoutMillis
curl "http://localhost:4100/_control/set?mode=slow&delayMs=6000"

# Randomly fails a fraction of requests — triggers flapping OPEN/RESOLVED incidents
curl "http://localhost:4100/_control/set?mode=flaky&failRate=0.5&statusCode=500"

# Never responds at all — triggers a real connection TIMEOUT (not a fast error)
curl "http://localhost:4100/_control/set?mode=hang"

# Current mode + request count, as JSON
curl "http://localhost:4100/_control/status"
```

## Notes

- State is in-memory only — restarting the server (or the container) resets it to `mode: ok`.
- Not part of `app`'s `depends_on` graph; it only starts when you name it explicitly
  (`docker compose up -d mock-server`), so it never blocks or slows down a normal
  `docker compose up -d`.
- This has been manually verified end-to-end: registering `http://mock-server:4000/health` as a
  Monitor produces real `SUCCESS`/`HTTP_ERROR` `CheckResult`s and opens/resolves real `Incident`s
  through the actual scheduler — not just a standalone HTTP smoke test.
