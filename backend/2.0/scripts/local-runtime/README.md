# Local Runtime Runner

Use these scripts for all local long-running services on Windows/PowerShell.

Rules:
- Do not run `mvn spring-boot:run`, `java -jar`, `npm run dev`, `uvicorn`, or any dev server directly in the foreground.
- Run `status-all.ps1` before replay, frontend checks, or local service work.
- If a service is down, use the matching `start-*.ps1` script.
- Start scripts run services in the background, write logs, save PID files, and wait for readiness with a timeout.
- If readiness fails, scripts print the last 100 log lines, clean up the process/port, and return non-zero.
- Stop scripts stop by PID first, then by port. They do not touch PostgreSQL or production.

Paths:
- Logs: `backend/2.0/.local-run/logs`
- PIDs: `backend/2.0/.local-run/pids`

Ports:
- Backend: `8080` by default, or `BACKEND_PORT=8081`
- Frontend: `5173`
- Worker: `8095`
- Local PostgreSQL: `127.0.0.1:5433`

Commands:
- `./status-all.ps1`
- `./start-backend.ps1`
- `./start-worker.ps1`
- `./start-frontend.ps1`
- `./start-all.ps1`
- `./stop-all.ps1`
- `./tail-logs.ps1 -Service all -Tail 100`
