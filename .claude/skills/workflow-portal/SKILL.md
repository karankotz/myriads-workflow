---
name: workflow-portal
description: Build, launch, verify, and stop the Myriads Workflow portal (the embedded web UI) or the CLI demo for this project. Use when asked to run, start, serve, or open the app/portal/server, or to confirm a workflow change works in the running app.
---

# Myriads Workflow — run & portal

This project is a Java 21 / Maven app: a distributed agentic workflow engine with an
embedded web portal that visualizes workflow runs live (stage cards light up over SSE).

`Main` has two modes:
- **CLI demo** — no args: runs one workflow and logs the result.
- **Web portal** — `serve [port]`: starts the HTTP server (default port `8080`).

## Build first (if `target/` is missing or sources changed)

```bash
mvn -q clean package
```

This compiles, runs tests, and produces the runnable fat jar `target/myriads-workflow.jar`.

## Launch the portal

Prefer running it in the background so the session isn't blocked, capturing logs:

```bash
java -jar target/myriads-workflow.jar serve 8080 > /tmp/myriads-portal.log 2>&1 &
echo "pid: $!"
```

From source instead of the jar: `mvn -q exec:java -Dexec.args="serve 8080"`.

The URL is **http://localhost:8080** — always surface this clickable URL to the user.

If port 8080 is busy, pick another port (e.g. `serve 8090`) and report the matching URL.

## Verify it's up

Poll readiness, then probe the API and a live run. Do NOT use a bare `sleep`; loop until ready:

```bash
for i in $(seq 1 10); do curl -fs http://localhost:8080/api/workflows >/dev/null && break; sleep 0.5; done
curl -s http://localhost:8080/api/workflows            # lists workflows + stages
curl -sN --max-time 8 "http://localhost:8080/api/workflows/research-and-ship/stream?goal=demo"
```

A healthy stream emits `event: started`, alternating `stage-started` / `stage-completed`,
and a final `event: completed`.

## HTTP API (for scripted checks)

| Method & path | Purpose |
|---------------|---------|
| `GET /api/workflows` | List workflows and their stage names. |
| `GET /api/workflows/{name}` | One workflow's definition. |
| `POST /api/workflows/{name}/run` | Run it, return the full result as JSON. |
| `GET /api/workflows/{name}/stream` | Run it, streaming live SSE events. |

Both run endpoints accept `?goal=...`, seeded into the run's `WorkflowContext`.

Bundled demo workflows: `research-and-ship` (all succeed), `kyc-onboarding` (halts at a
review gate), `ci-cd-deploy` (fails at smoke-test), `security-scan` (parallel fan-out),
and `ai-research-crew` (real Claude agents) — useful for confirming success / halt /
failure / concurrent rendering.

The `ai-research-crew` workflow calls the Claude API and needs `ANTHROPIC_API_KEY` set in
the environment (optional `MYRIADS_MODEL`, default `claude-opus-4-8`). Without the key it
fails on the first stage with a clear message; the other workflows are simulated and run
regardless. CLI form: `java -jar target/myriads-workflow.jar ai "<goal>"`.

## Run the CLI demo instead

```bash
java -jar target/myriads-workflow.jar        # or: mvn -q exec:java
```

## Stop the portal

Kill the PID captured at launch; confirm the port is free:

```bash
kill <pid> || pkill -f myriads-workflow.jar
curl -s -o /dev/null --max-time 2 http://localhost:8080/ || echo "server down"
```

## Tests only

```bash
mvn test
```
