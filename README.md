# barbu-server

Authoritative real-time backend for **Barbu**, an online multiplayer card game for **2–10 players**.
Java 21 · Micronaut (REST + WebSocket). It holds a dependency-free game engine, runs server-side
bots, and is the **producer of the OpenAPI contract** consumed by the web client.

## The game

A Barbu variant with **five penalty contracts** played in a fixed order, followed by the *montante*:

- Contracts: **no tricks**, **no hearts**, **no queens**, **no red kings** (♥+♦), then the **montante** —
  a domino-style round opening on the 8s (the player to the dealer's left opens; first to empty their
  hand ranks first).
- **Deck adapted to N**: start from 52 cards and drop `52 mod N`, weakest first and red before black
  (e.g. 5 players → drop 2♥ and 2♦). The 8 is never removed, so the montante can always open.
- Each penalty contract distributes exactly **60 points**; the montante is a **zero-sum** ranking
  interpolated linearly from **+30 to −30**. Scoring is centralized and tunable (`ScoringConfig`).

## Architecture

Gradle multi-module:

| Module | Responsibility |
|---|---|
| `game-engine` | Pure, dependency-free rules engine (2–10 players, 5 contracts + montante). Deterministic; unit- and property-tested (jqwik). |
| `bot` | Heuristic AI and a CLI match simulator. |
| `app` | Micronaut server: authoritative WebSocket rooms, per-seat redaction, server-side bots, ELO-ranked matchmaking, reconnection, JWT accounts, persistence, and the OpenAPI producer. |

## Engineering highlights

- **Authoritative server** — all state lives server-side and each client sees only its own seat
  (per-seat redaction), so a tampered client cannot cheat.
- **Stateless & horizontally scalable** — room and matchmaking state are externalized to **Redis**,
  so pods scale out and rehydrate on restart. Covered by chaos and self-healing integration tests.
- **Code-first contract** — `micronaut-openapi` generates `build/openapi.yml` from the controllers →
  Orval generates the `@barbu-game/barbu-api` package → the web client pins it and its typecheck
  **breaks on any drift**.
- **Tested** — 120+ backend tests, including property-based invariants on the engine.

## Tech stack

Java 21 · Micronaut (REST + WebSocket) · Micronaut Data JDBC + Flyway (H2 by default, PostgreSQL via
env) · Micronaut Security (JWT, bcrypt) · Redis (Lettuce) · Micrometer/Prometheus metrics ·
Gradle 8.10.2 · Spotless (palantir-java-format).

## Getting started

Prerequisite: **JDK 21** (Temurin).

```bash
# build and run the full test suite
./gradlew build

# run the server — in-memory H2, no setup, on http://localhost:8080
JAVA_HOME=/path/to/temurin-21 ./gradlew :app:run

# simulate bot-only matches from the CLI
./gradlew :bot:run
```

## Configuration

Everything runs out of the box; production overrides the following:

| Variable | Default | Purpose |
|---|---|---|
| `JWT_SECRET` | dev fallback | HMAC secret for JWT signing (use ≥ 256 bits in production). |
| `JDBC_URL` / `JDBC_DRIVER` / `JDBC_USER` / `JDBC_PASSWORD` | in-memory H2 | Datasource; point at PostgreSQL in production. |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Allowed web origin. |
| `REDIS_URI` | — | External state store for rooms and matchmaking (enables horizontal scaling). |
| `BARBU_BOT_DELAY_MS` | `650` | Pacing of server-side bot moves (`barbu.bot-delay-ms`). |
| `POD_ID` | — | Identity used for sticky per-pod WebSocket routing. |

## API surface

- **WebSocket** `/ws/game` — gameplay: join, play, montante, chat, reconnection, vote-to-stop.
- **REST** `/auth` (register / login), `/me`, `/leaderboard`, `/variants`, `/health`.
- **Metrics** `/prometheus` (Micrometer).

## Related repositories

- `barbu-web` — Next.js / React client that consumes `@barbu-game/barbu-api`.
- `barbu-deploy` — Helm charts, Terraform (Hetzner k3s) and ArgoCD GitOps.
- `barbu-actions` — reusable CI workflows (OpenAPI publish).

## License

[MIT](./LICENSE) © 2026 Lucas Laurent
