# VeriVote-J Limitations

This document enumerates known limitations of the VeriVote-J design as of Part 1.
Limitations are numbered L1–L15 and cross-referenced in the threat model.

- **L1** — **No coercion resistance.** The system does not prevent a coercer from observing or dictating a voter's choice. Coercion-resistant voting requires cryptographic protocols (e.g., re-voting, receipt-freeness) that are out of scope for this iteration.

- **L2** — **Trusted tallier.** The tally authority is trusted to count correctly. Without a verifiable shuffle or mix-net, the tallier can in principle misreport results while publishing a superficially consistent chain.

- **L3** — **Admin holds signing key.** The election administrator retains the blind-signature private key throughout the election. Compromise of that key allows unlimited token forgery (see T6). Threshold key management or HSM custody is a future enhancement.

- **L4a** — **IP anonymity not guaranteed.** The server necessarily observes voter IP addresses at authentication and token-issuance time. Mixnets or Tor integration are not provided.

- **L4b** — **IP correlation risk remains.** Even with client-side random delays (T14), a network-level adversary with access to both issuance and vote-submission traffic can attempt timing correlation.

- **L5** — **No zero-knowledge proofs.** The system does not use ZK-SNARKs or ZK-STARKs. Ballot secrecy and tally correctness rely on procedural controls rather than cryptographic proofs.

- **L6** — **TOTP only for 2FA.** Multi-factor authentication is limited to TOTP. Hardware security keys (FIDO2/WebAuthn) and backup code schemes are not implemented.

- **L7** — **Tally not cryptographically verifiable.** There is no verifiable shuffle or homomorphic tally. Voters must trust that the published shuffle corresponds to actual cast ballots.

- **L8** — **Ledger file is local.** The append-only ledger is stored on the server filesystem. While external anchors can be used for integrity, the ledger itself is not replicated or distributed by default.

- **L9** — **No formal receipt-freeness.** Voters receive a verifiable receipt, but this receipt can be shown to a coercer as proof of vote choice. True receipt-freeness requires additional protocol steps.

- **L10** — **Revocation not supported.** There is no mechanism to revoke an issued token or invalidate a ballot once cast, short of a full election reset.

- **L11** — **Single election authority.** The design assumes a single election authority. Multi-party ceremony for key generation and threshold signing are not implemented.

- **L12** — **Audit log not tamper-evident by default.** Server-side application logs (outside the ledger) are standard files and not hash-chained. External log shipping is recommended but not enforced.

- **L13** — **Client binary trust.** The JavaFX client binary must be distributed through a trusted channel. There is no auto-update mechanism with code-signing verification.

- **L14** — **No ballot ordering privacy against timing analysis beyond delays.** The 30–300 s random delay reduces but does not eliminate timing-based ballot ordering attacks against a passive network adversary.

- **L15** — **Admin key custody is a residual risk for T6.** Because the administrator holds the blind-signature key, ballot-stuffing by a malicious admin is partially mitigated (issuance reconciliation) but not fully prevented until key-ceremony / threshold signing is added.
