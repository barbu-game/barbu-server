# game-engine

Pure-Java, deterministic rules engine for *le Barbu* (2–10 players). No Micronaut,
no network, no database — only the game logic. Consumed by `app` (authoritative
server) and `bot`.

## Contracts

- `NO_TRICKS`, `NO_HEARTS`, `NO_QUEENS`, `NO_RED_KINGS` (trick-taking)
- `MONTANTE` (domino: opens on the 8s, 8♦ holder starts, must-play-or-pass)

The deck is reduced to a multiple of `N` by dropping the `52 mod N` weakest cards
(red before black). Scoring lives in `model/ScoringConfig` — change balance there,
never in the rules.

## Public API

- `RoundEngine.legalMoves / applyMove / score` — single round.
- `MatchEngine.newMatch / chooseContract / applyMove / playOut / standings /
  isDealerBoundary` — full `5 × N` match (configurable length).

Everything is reproducible from `(playerCount, seed)`.

## Test

    ./gradlew test
