# VeriVote-J

A verifiable voting system that guarantees ballot secrecy through blind signatures,
vote integrity through a hash-chained append-only ledger, and public verifiability
through Merkle inclusion proofs and signed checkpoints — without requiring voters
to trust the operator.

## Build

```bash
mvn -q clean verify
```

Requires Java 21 and Maven 3.9+.

## Module layout

| Module | Purpose |
|--------|---------|
| `verivote-core` | Crypto primitives, ledger block types, domain constants |
| `verivote-data` | JPA entities, repositories, H2 schema |
| `verivote-service` | Business-logic services, token issuance, vote processing |
| `verivote-web` | Spring Boot REST API endpoints |
| `verivote-client` | JavaFX voter client |

## Part Tracker

> Parts 1–14 are built and tested sequentially. Do not skip.

- [x] Part 1  — Skeleton
- [ ] Part 2  — Hashing & Encoding
- [ ] Part 3  — Merkle Tree
- [ ] Part 4  — Block Hash
- [ ] Part 5  — Blind Sig & Shamir
- [ ] Part 6  — DB & RecoveryData
- [ ] Part 7  — Ledger
- [ ] Part 8  — Auth & Token Issuance
- [ ] Part 9  — Vote Endpoint
- [ ] Part 10 — Checkpoints & Sinks
- [ ] Part 11 — AuditService
- [ ] Part 12 — Operator & Recovery
- [ ] Part 13 — Tally & Bulletin Board
- [ ] Part 14 — JavaFX Clients & Demo

## Documentation

| File | Contents |
|------|---------|
| [`docs/DOMAIN.md`](docs/DOMAIN.md) | Frozen domain-separation strings and encoding rules (source of truth) |
| [`docs/THREAT-MODEL.md`](docs/THREAT-MODEL.md) | Threat model — T1–T27 |
| [`docs/LIMITATIONS.md`](docs/LIMITATIONS.md) | Known limitations — L1–L15 |
