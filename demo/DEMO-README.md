# VeriVote-J Demo Script (§8.4)

## Prerequisites
1. Server running: `cd verivote-web && mvn spring-boot:run`
2. At least 3 votes cast via the JavaFX voter client
3. Admin client ready (JavaFX "Admin" button, or curl)

## Quick reference: what each scenario proves

| # | Attack | Expected finding | Severity | Threat model |
|---|--------|-----------------|----------|--------------|
| 1 | UPDATE vote.encrypted_vote | DB_CIPHERTEXT_MISMATCH | RED | T3 |
| 2 | DELETE vote row | BLOCK_WITHOUT_VOTE_ROW | ALERT | T4 (partial) |
| 2b | (recovery after #2) | clean after RECOVER_FROM_LEDGER | — | T4, T6 |
| 3 | Truncate ledger mid-file | startup refuses / CHAIN_TRUNCATED | RED | T4, T12 |
| 4 | Append garbage line | startup refuses / MALFORMED_LINE | RED | T3 |

## Running the demo
Full walkthrough: `bash demo/demo.sh` (interactive, pauses between steps)

Non-interactive (for a viva examiner with no patience): copy commands from 
the script into a terminal — each scenario is 2-3 commands.

## What the demo proves for the report
- The ledger is the truth anchor — DB mutations are detected
- External checkpoints catch truncation
- Parse-time fatal conditions refuse startup (no silent divergence)
- Recovery path restores DB from ledger without losing audit trail

## Caveats (documented for honesty)
- Recovery requires 2 distinct AUDIT operators (T13, L13)
- The demo uses pre-generated operator keys (see demo/operator-keys.json 
  generated on first recovery)
- Client-side signing is a demo limitation — real production uses HSM
