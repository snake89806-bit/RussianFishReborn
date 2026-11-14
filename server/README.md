# Russian Fishing Reborn — online API (alpha)

Go standard-library HTTP API for accounts, all-time leaderboard and online tournament participation. It is a separate server service; Android remains the only client app. This is a deployable single-instance alpha, not a production-ready MMO backend.

## Implemented

- Account registration and login; usernames are case-insensitive.
- Passwords hashed with PBKDF2-HMAC-SHA256 (210,000 rounds, random 16-byte salt).
- Signed HS256 bearer sessions (72-hour expiry); role is reloaded from stored account on each request.
- Player role is assigned by public registration. Public requests cannot choose or promote their role.
- Owner/admin account bootstrap via `ADMIN_USERNAME` and `ADMIN_PASSWORD` environment variables on first startup.
- Authenticated player profile endpoint.
- Shared all-time leaderboard: best fish weight, total weight, catch count.
- Catch submission with strict input bounds and idempotency key (`clientEventId`).
- Admin-only ban/unban routes. Banned accounts cannot authenticate or submit scores and are excluded from standings.
- JSON persistence with atomic file replacement; file permissions are restricted to the server account.

## Run locally

```sh
cd server
export JWT_SECRET="$(openssl rand -hex 32)"
export ADMIN_USERNAME="owner"
export ADMIN_PASSWORD="use-a-unique-long-password"
export DATA_FILE="./data/game.json"
go test ./...
go run ./cmd/server
```

The API listens on `:8080`. Put it behind a reverse proxy with HTTPS before connecting Android clients. Do not expose the plain HTTP listener directly to the internet. Use a strong unique JWT secret and protect the data volume and backups.

## Docker

```sh
docker build -t rf-reborn-api .
docker run --name rf-api -p 127.0.0.1:8080:8080 \
  -e JWT_SECRET="$JWT_SECRET" -e ADMIN_USERNAME="$ADMIN_USERNAME" \
  -e ADMIN_PASSWORD="$ADMIN_PASSWORD" \
  -e DATA_FILE=/app/data/game.json -v rf-data:/app/data rf-reborn-api
```

Terminate TLS at Caddy, nginx, Traefik, or a managed HTTPS load balancer. Do not use example credentials in deployment.

## API

All requests and responses are JSON. `Authorization: Bearer <token>` is required except health, registration, login, public feature/news endpoints and the all-time leaderboard.

| Method | Path | Access | Purpose |
|---|---|---|---|
| GET | `/health` | public | health check |
| POST | `/v1/register` | public | create player account |
| POST | `/v1/login` | public | create session |
| GET | `/v1/me` | player/admin | current account |
| GET | `/v1/leaderboard?limit=50` | public | shared all-time rankings (1–100) |
| POST | `/v1/catches` | player/admin | submit catch |
| POST | `/v1/tournaments/join` | player/admin | join the currently active tournament (idempotent) |
| GET | `/v1/tournaments/leaderboard` | player/admin | current tournament participants and standings |
| POST | `/v1/admin/users/{id}/ban` | admin | ban account |
| POST | `/v1/admin/users/{id}/unban` | admin | restore account |

Registration body:

```json
{"username":"angler_01","displayName":"Рыбак","password":"at-least-10-characters"}
```

Catch body:

```json
{"clientEventId":"unique-event-id-123","fishId":"pike","weightKg":4.25,"locationId":"deep_pool","caughtAt":"2026-11-14T10:00:00Z"}
```

`clientEventId` makes retries idempotent. The server currently checks shape, timestamps, weight bounds and duplicate IDs. **It does not yet prove that a catch was earned by the game simulation.** A modified client can submit fabricated but in-range catches; do not use this alpha for valuable prizes or paid competitions. Authoritative server-side catch simulation, stronger anti-cheat/rate limits, moderation audit logs, password reset, refresh/revocation, chat, and database-backed multi-instance deployment are future work.

## Storage limitation

The alpha uses a mutex-protected JSON file and is intended for one server process on one persistent volume. Do not run multiple replicas against the same file. Before public launch, migrate users/catches to PostgreSQL, add migrations and backups, request throttling, operational monitoring, and a reviewed privacy policy.

## Admin API (0.27.0)

All `/v1/admin/*` routes require a valid bearer session whose current server-side role is `admin`. The Android registration endpoint always creates `player` accounts; a client cannot self-assign admin rights.

| Method | Path | Access | Purpose |
|---|---|---|---|
| GET | `/v1/features` | public | current event/tournament switches |
| GET | `/v1/announcements` | public | latest 30 announcements |
| GET | `/v1/admin/users` | admin | up to 200 accounts, newest first; no password hashes |
| GET | `/v1/admin/audit` | admin | latest 200 moderation/economy/config actions |
| POST | `/v1/admin/users/{id}/coins` | admin | grant 1–100,000 coins |
| PUT | `/v1/admin/events` | admin | update event enabled/title/message |
| PUT | `/v1/admin/tournaments` | admin | update tournament enabled/title/message |
| POST | `/v1/admin/announcements` | admin | publish a 2–80 character title and 2–500 character message |

Coin grants, event/tournament updates and announcements are written to the persistent audit log. Ban/unban actions are also recorded. The Android REST client and in-game admin dashboard expose these operations; server-side role checks remain authoritative.


## Online tournament (0.27.0)

When an admin enables or updates the tournament configuration, its `updatedAt` timestamp identifies a new tournament cycle. An authenticated player joins with `POST /v1/tournaments/join`; repeated calls return the same join timestamp. The authenticated standings endpoint returns joined players and ranks them by best catch weight, then total weight. Only catches timestamped after the current cycle began and after that player's join are counted. Disabling or changing the configuration starts a different cycle.

**Fair-play limitation:** the server aggregates submitted catches but still cannot validate the actual fishing simulation. Do not award valuable prizes until catch outcomes are generated or verified authoritatively on the server.
