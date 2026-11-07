# VeriVote-J Threat Model

> **Status legend:** Mitigated · Partial · Future · Reserved

| ID  | Asset | Adversary | Attack | Mitigation | Status |
|-----|-------|-----------|--------|------------|--------|
| T1  | Ballot secrecy | Curious admin | Read vote table | No FK voter→vote; identity severed at issuance | Mitigated |
| T2  | Ballot secrecy | Network | Sniff vote | RSA-OAEP client-side encryption | Mitigated |
| T3  | Vote integrity | DB admin | UPDATE vote row | Hash-chained ledger; per-block hash check | Mitigated |
| T4  | Vote integrity | DB admin | DELETE vote / truncate chain | Signed checkpoints; external sinks | Mitigated |
| T5  | Double voting | Voter | Vote twice | spent_token PK; has_voted at issuance | Mitigated |
| T6  | Ballot stuffing | Admin | Fabricated votes | Valid blind sig required; issuance reconciliation; admin still holds signing key (L15) | Partial → L15 |
| T7  | Coercion | Coercer | Forced vote | Not mitigated → L1 | Future |
| T8  | Verification failure | Lazy voter | No verification | ≤3 clicks; auto-link | Mitigated |
| T9  | Tally integrity | Tally authority | Wrong count | Publishes counts + chain root + shuffled ballots; no ZK → L7 | Partial |
| T10 | Credential theft | Network | Password steal | TOTP 2FA | Mitigated |
| T11 | Ballot secrecy | Admin w/logs | Correlate order+timestamps | Shuffle; minute timestamps; vote/spent_token have no timestamps | Mitigated |
| T12 | Chain integrity | Admin w/server | Rewrite chain + re-sign | External anchors | Mitigated if anchors used |
| T13 | Client integrity | Server compromise | Lies included: true | Client verifies Merkle proof + checkpoint sig (pinned) | Mitigated |
| T14 | Ballot secrecy | Network observer | IP/timing/TLS correlation | Client random delay 30-300s; IP → L4b | Partial |
| T15 | Ballot integrity | Admin | Flip has_voted | Published issuance counter; regression detection | Partial (delayed) |
| T16 | — | — | — | reserved for future review | Reserved |
| T17 | — | — | — | reserved for future review | Reserved |
| T18 | — | — | — | reserved for future review | Reserved |
| T19 | — | — | — | reserved for future review | Reserved |
| T20 | — | — | — | reserved for future review | Reserved |
| T21 | — | — | — | reserved for future review | Reserved |
| T22 | — | — | — | reserved for future review | Reserved |
| T23 | — | — | — | reserved for future review | Reserved |
| T24 | — | — | — | reserved for future review | Reserved |
| T25 | — | — | — | reserved for future review | Reserved |
| T26 | — | — | — | reserved for future review | Reserved |
| T27 | — | — | — | reserved for future review | Reserved |
