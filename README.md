# VeriVote-J: Cryptographically Verifiable End-to-End Electronic Voting System
### Complete Technical Architecture & System Specification Document

---

## 1. Executive Summary & System Overview

**VeriVote-J** is an enterprise-grade, cryptographically verifiable electronic voting platform built in Java 21 and Spring Boot. It resolves the fundamental dilemma of electronic elections: **guaranteeing absolute voter privacy (ballot secrecy) while simultaneously providing mathematically undeniable proof of election integrity and public verifiability (cast-as-intended, recorded-as-cast, and counted-as-recorded).**

Unlike conventional voting systems that demand blind trust in system administrators, database hosting providers, or centralized servers, VeriVote-J is architected around a **zero-trust threat model**. It enforces:

1. **Ballot Secrecy via Cryptographic Blind Signatures**: The server authorizes eligible voters through RSA Blind Signatures without ever seeing the plaintext voting token or being able to link a voter’s real-world identity to their cast ballot.
2. **Append-Only Tamper-Evident Ledger**: Ballots are appended to a hash-chained NDJSON ledger anchored by an incremental RFC 6962 Merkle tree. Every block is bound to its predecessor via SHA-256 chain links.
3. **Public Bulletin Board & Merkle Inclusion Proofs**: Voters and independent observers can cryptographically verify that any specific ballot receipt is included in the published Merkle tree root in $\mathcal{O}(\log N)$ steps without revealing the voter's candidate selection.
4. **Periodic Multi-Sink Signed Checkpoints**: The ledger state is periodically sealed by Ed25519 digital signatures and mirrored to independent external storage sinks (disk, local Git repository, and printed hardcopy) to prevent chain truncation or rewriting.
5. **Threshold Key Custody (2-of-3 Shamir's Secret Sharing)**: The election decryption private key is split across multiple independent trustees. No single operator can decrypt ballots or tally votes before the election is officially sealed and audited.
6. **Automated Forensic Audit & Dual-Operator Recovery**: An autonomous audit engine continuously verifies the physical ledger against checkpoints and database records. In the event of network disruption or orphan blocks, multi-party signed authorization ceremonies are required to execute recovery.

```mermaid
flowchart TD
    subgraph Voters["Voter Client Surfaces"]
        V1["Voter Web Portal (Tailwind SPA)"]
        V2["Voter JavaFX Desktop App"]
    end

    subgraph CoreBackend["VeriVote-J Core Platform (Spring Boot)"]
        AuthSvc["Auth & Token Issuance Engine\n(RSA Blind Signing)"]
        VoteSvc["Vote Processing Engine\n(OAEP Ciphertext & Spend Registry)"]
        LedgerSvc["Append-Only Ledger Engine\n(Hash Chaining & Merkle Roots)"]
        CkptSvc["Checkpoint & Sinks Service\n(Ed25519 Signing)"]
        AuditSvc["Autonomous Audit Service\n(Disk vs DB Reconciliation)"]
        TallySvc["Threshold Tally Engine\n(2-of-3 Shamir Reconstruction)"]
    end

    subgraph Storage["Persistence & Integrity Anchors"]
        H2DB[("Relational DB / Cache\n(H2 / PostgreSQL)")]
        DiskLedger[("Physical Append-Only Ledger\n(blocks.ndjson)")]
        ExtSinks[("External Sinks\n(Git Sink / File Sink / Print Sink)")]
    end

    subgraph Observers["Public & Administrative Surfaces"]
        BB["Public Bulletin Board\n(Receipts & Checkpoint Proofs)"]
        AdminCons["Admin & Auditor Console\n(Audit, Recovery, Tally, Operators)"]
        Trustees["Trustee Key Custodians\n(Shamir Shares)"]
    end

    Voters -->|"1. Blind Token Request"| AuthSvc
    Voters -->|"2. Anonymous Ballot Submit"| VoteSvc
    Voters -->|"3. Inclusion Proof Verification"| BB

    AuthSvc --> H2DB
    VoteSvc --> H2DB
    VoteSvc --> LedgerSvc

    LedgerSvc --> DiskLedger
    LedgerSvc --> CkptSvc
    CkptSvc --> ExtSinks
    CkptSvc --> H2DB

    AuditSvc --> DiskLedger
    AuditSvc --> H2DB
    AuditSvc --> ExtSinks

    Trustees -->|"Provide Shares (2-of-3)"| TallySvc
    TallySvc --> LedgerSvc
    TallySvc --> BB
    AdminCons --> AuditSvc
    AdminCons --> TallySvc
```

---

## 2. Core Architectural Principles & Security Philosophy

### 2.1 Anonymity by Structural Separation
The architecture guarantees anonymity by ensuring that voter identity and ballot data occupy completely isolated namespaces. 
- The **voter identity** table (`voter`) records whether a registered citizen has been issued a voting credential (`has_voted = TRUE`). It possesses **zero foreign key relationships** to the cast votes table or the ledger.
- The **token issuance protocol** operates on blinded data. The server signs a blinded hash without learning the underlying token plain value.
- The voter submits their encrypted vote over an unauthenticated session presenting only the unblinded token and signature. The server records the vote solely under the token’s cryptographic hash (`spent_token`), permanently severing the connection between national identity and ballot choice.

### 2.2 Tamper-Evidence over Tamper-Prevention
In physical and digital security, absolute prevention of local administrator access is infeasible. Therefore, VeriVote-J adopts the principle of **immutable tamper-evidence**:
- The append-only ledger file on disk (`blocks.ndjson`) is the **authoritative root of truth**.
- The relational database is treated strictly as an **ephemeral query cache**. If a malicious database administrator updates or deletes rows in the database, the discrepancy is immediately caught and flagged by the independent audit engine.
- If an adversary attempts to modify historical ledger blocks, the cryptographic hash chain breaks, Merkle inclusion proofs fail, and discrepancies with previously published external checkpoints reveal the exact point of tampering.

### 2.3 Public Verifiability in $\le 3$ Clicks
Security systems fail when verification is cumbersome. VeriVote-J enables any voter to paste their 64-character receipt hash (or click 1 button directly on their voting receipt card) to receive a full cryptographic audit showing:
1. Exact ledger block height.
2. Merkle audit path hashes.
3. Computed Merkle tree root.
4. Matching Ed25519 signature from the election authority's published checkpoint.

---

## 3. Multi-Module Project Topology

The codebase is organized into five decoupled Maven modules, maintaining strict layer boundaries and separation of concerns:

```
javaproject2026/
├── pom.xml                     # Multi-module parent POM (Java 21, Spring Boot 3.3.4)
├── Dockerfile                  # Multi-stage production container build (Temurin JRE 21)
├── docs/                       # Formal specifications (DOMAIN.md, THREAT-MODEL.md, LIMITATIONS.md)
│
├── verivote-core/              # Pure cryptographic primitives & low-level ledger engine
│   └── src/main/java/com/verivote/core/
│       ├── crypto/             # BlindSignatureService, MerkleTree, MerkleVerifier, Shamir, Sha256
│       ├── domain/             # DomainStrings, frozen domain preimages
│       ├── encoding/           # Base64Codec, Hex, BigEndian
│       └── ledger/             # Block, BlockHash, FileLedgerService, LedgerFileParser
│
├── verivote-data/              # Relational entities, Spring Data JPA repositories, schema
│   └── src/main/java/com/verivote/data/
│       ├── entity/             # Voter, Vote, SpentToken, Checkpoint, Operator, RecoveryEvent, etc.
│       ├── repo/               # Repositories with custom atomic update queries
│       └── JpaRecoveryData.java# State-recovery and event auditing bridge
│
├── verivote-service/           # Business logic, lifecycle coordination, security orchestration
│   └── src/main/java/com/verivote/service/
│       ├── audit/              # DefaultAuditService, CheckpointReader, TamperReport, Finding
│       ├── checkpoint/         # DefaultCheckpointService, CheckpointSigner, File/Git/Print Sinks
│       ├── recovery/           # DefaultRecoveryService, OperatorService, RecoveryOperatorSigner
│       ├── tally/              # DefaultTallyService, TallyKeyService, Ballot
│       ├── TokenService.java   # Blind token issuance & validation
│       └── VoteService.java    # Atomic ballot submission & ledger synchronization
│
├── verivote-web/               # Spring Boot Web API, REST controllers, static web UI
│   └── src/main/java/com/verivote/web/
│       ├── AuthController.java # Registration & 2FA login endpoints
│       ├── TokenController.java# Blind token issuance endpoints
│       ├── VoteController.java # Ballot submission endpoints
│       ├── BulletinBoardController.java # Public receipts & checkpoints endpoints
│       ├── AdminController.java# Forensic audit, recovery ceremony, operator endpoints
│       ├── TallyController.java# Election closure & Shamir tally endpoints
│       └── resources/static/   # Responsive Single Page Application (index.html)
│
└── verivote-client/            # Standalone JavaFX desktop client application
    └── src/main/java/com/verivote/client/
        ├── admin/              # JavaFX Admin Console (Audit, Recovery, Tally, Operators)
        ├── screen/             # JavaFX Voter screens (Login, Token, Ballot, Verify)
        └── crypto/             # Pinned keys and client-side Merkle verification
```

---

## 4. Cryptographic Foundations & Domain Specifications

VeriVote-J implements cryptographic protocols conforming to academic and industrial zero-knowledge and end-to-end verifiable voting standards.

### 4.1 RSA Blind Signatures (RFC 9474 Family)
To issue an authentic voting credential without learning the voter's token, VeriVote-J implements RSA Blind Signatures:

1. **Token Generation (Client)**:
   The client generates a high-entropy 32-byte secret token plain value:
   $$\text{tokenPlain} \stackrel{\$}{\leftarrow} \{0, 1\}^{256}$$
   The client constructs the domain-separated token preimage:
   $$M = \text{"VERIVOTE-TOKEN-v1"} \parallel \text{int64be}(\text{electionId}) \parallel \text{tokenPlain}$$
   The client computes the hash $m = \text{SHA-256}(M)$.
2. **Blinding (Client)**:
   The client picks a cryptographically secure random blinding factor $r \in \mathbb{Z}_N^*$ and computes the blinded message:
   $$m^* = (m \cdot r^e) \pmod N$$
   where $(e, N)$ is the election authority’s RSA public key.
3. **Blind Signing (Server)**:
   Upon verifying that the voter is registered and has not previously voted, the server signs $m^*$ using its private key $d$:
   $$s^* = (m^*)^d \equiv (m \cdot r^e)^d \equiv m^d \cdot r \pmod N$$
   The server increments the issuance counter and marks `has_voted = TRUE`.
4. **Unblinding (Client)**:
   The client unblinds the received signature:
   $$s = s^* \cdot r^{-1} \equiv (m^d \cdot r) \cdot r^{-1} \equiv m^d \pmod N$$
   The resulting pair $(\text{tokenPlain}, s)$ is a valid RSA signature over $m$ that can be verified by anyone using $(e, N)$, yet the server has never seen $\text{tokenPlain}$ or $s$.

### 4.2 Ballot Encryption (RSA-OAEP)
Ballots are encrypted on the client side prior to transmission using RSA-OAEP with SHA-256 and MGF1 padding:
- Plaintext payload: $\text{int64be}(\text{electionId}) \parallel \text{int32be}(\text{candidateId}) \parallel \text{ballotId}[32]$
- $\text{ballotId} \stackrel{\$}{\leftarrow} \{0, 1\}^{256}$ ensures that identical candidate choices produce completely distinct ciphertexts.
- Encrypted with the election tally public key. The private key does not exist in assembled form on any system during active voting.

### 4.3 Domain Separation & Frozen Preimages (Source of Truth)
To prevent cross-protocol replay attacks and ambiguity, every cryptographic hash and signature preimage is strictly prefixed with a domain identifier defined in [`docs/DOMAIN.md`](docs/DOMAIN.md):

| Domain / Purpose | Exact Preimage Byte Structure | Output Size |
| :--- | :--- | :--- |
| **Token Signature** | `"VERIVOTE-TOKEN-v1"` $\parallel$ `int64be(eid)` $\parallel$ `tokenPlain` | Signed via RSA |
| **Spend Hash** | $\text{SHA-256}(\text{"VERIVOTE-SPEND-v1"} \parallel \text{int64be(eid)} \parallel \text{tokenPlain})$ | 32 bytes (hex) |
| **Receipt Hash** | $\text{SHA-256}(\text{"VERIVOTE-RECEIPT-v1"} \parallel \text{int64be(eid)} \parallel \text{nonce}[32] \parallel \text{tokenHash}[32])$ | 32 bytes (hex) |
| **Block Hash** | $\text{SHA-256}(\text{"VERIVOTE-BLOCK-v1"} \parallel \text{typeByte} \parallel \text{u64be(height)} \parallel \text{u64be(refHeight)} \parallel \text{prevHash}[32] \parallel \text{voteHash}[32] \parallel \text{tokenHash}[32] \parallel \text{receiptHash}[32] \parallel \text{u64be(timestampSec)} \parallel \text{merkleRoot}[32])$ | 32 bytes (hex) |
| **Merkle Leaf** | $\text{SHA-256}(0\text{x}00 \parallel \text{data})$ | 32 bytes (raw) |
| **Merkle Node** | $\text{SHA-256}(0\text{x}01 \parallel \text{left}[32] \parallel \text{right}[32])$ *(RFC 6962 Standard)* | 32 bytes (raw) |
| **Empty Tree** | $\text{SHA-256}(0\text{x}02)$ | 32 bytes (raw) |
| **Orphan Leaf** | $\text{SHA-256}(0\text{x}00 \parallel \text{"VERIVOTE-ORPHAN-v1"} \parallel \text{u64be(refHeight)})$ | 32 bytes (raw) |
| **Checkpoint Sig**| $\text{"VERIVOTE-CKPT-v1"} \parallel \text{u32be(treeSize)} \parallel \text{merkleRoot}[32] \parallel \text{headHash}[32]$ | Ed25519 Sig |
| **Recovery Sig** | $\text{"VERIVOTE-RECOVERY-v1"} \parallel \text{u64be(height)} \parallel \text{ascii(action)} \parallel 0\text{x}00 \parallel \text{blockHash} \parallel \text{tokenHash} \parallel \text{reason}$ | Ed25519 Sig |
| **Issuance Pub Sig**| $\text{"VERIVOTE-ISSUANCE-v1"} \parallel \text{u64be(eid)} \parallel \text{u64be(issued)} \parallel \text{u64be(resets)} \parallel \text{u64be(votesCast)} \parallel \text{u64be(pubSec)}$ | Ed25519 Sig |

### 4.4 Shamir's Secret Sharing over $\text{GF}(256)$
The election private key ($2048$-bit RSA PKCS#8 private key) is split at election initialization into $n = 3$ shares with a threshold of $k = 2$.
- The field arithmetic is executed over the Galois Field $\text{GF}(256)$ with the AES irreducible polynomial $x^8 + x^4 + x^3 + x + 1$ ($0\text{x}11\text{B}$) using generator $\alpha = 3$.
- Any two authorized trustees (e.g., Election Official + Independent Civil Auditor) can combine their shares $(x_1, y_1)$ and $(x_2, y_2)$ using Lagrange interpolation to reconstruct the polynomial secret $P(0)$:
$$P(0) = \sum_{j=1}^{k} y_j \prod_{\substack{m=1 \\ m \neq j}}^{k} \frac{-x_m}{x_j - x_m} \pmod{\text{GF}(256)}$$
- Individual key shares reveal zero information regarding the private key ($\mathcal{H}(\text{Key} \mid \text{Share}_1) = \mathcal{H}(\text{Key})$).

### 4.5 Voter Details Protection, PII Isolation & Credential Cryptography
Protecting citizen identities, voter records (PII), and authentication credentials is a core security pillar of VeriVote-J:

1. **Zero Foreign-Key Namespace Isolation**:
   - The `voter` table stores eligible citizen registration records (National ID / EPIC, full name, constituency, and eligibility flags).
   - The schema enforces **strict structural zero-linkage**: there are no foreign keys, relational joins, or audit references connecting the `voter` table to the `vote` table, `spent_token` registry, or the append-only ledger blocks (`blocks.ndjson`).
   - The only state changed upon credential issuance is the boolean flag `has_voted = TRUE` and `token_issued_at`. Once marked, it is mathematically impossible for any database query or administrator to correlate a voter record with a specific cast ballot.

2. **Salted Credential & Password Hashing**:
   - Registered voter passwords and operator authentication secrets are secured using salted one-way cryptographic hashing, preventing credential exposure even in the event of an unauthorized database dump.

3. **HMAC-SHA256 Voter Token Authentication (`VERIVOTE_HMAC_MASTER_KEY`)**:
   - Voter authorization tokens and session signatures are validated using an authenticated HMAC-SHA256 construction governed by the `VERIVOTE_HMAC_MASTER_KEY`.
   - In production, the system strictly enforces a high-entropy 256-bit (64 hexadecimal character) master key. Startup will **fail fast** if the key is missing, blank, malformed, or using default development values, preventing session forgery or token tampering.

4. **Cryptographic Blinding Severance**:
   - Voter authentication only authorizes the signing of a client-blinded token hash $m^* = (m \cdot r^e) \pmod N$. The server never observes the plaintext voting token $m$ or blinding factor $r$.
   - When the ballot is subsequently cast, it is submitted over an unauthenticated endpoint presenting only the unblinded signature and RSA-OAEP encrypted ciphertext. The voter's real-world identity is never transmitted with the vote.

5. **In-Flight Encryption & Masked UI Presentation**:
   - All network exchanges occur over TLS 1.3 / HTTPS.
   - Voter identification numbers (such as EPIC cards) are masked on verification views and receipt summaries (e.g., `IND-****-8842`) to protect privacy against shoulder-surfing and client-side observation.

---

## 5. Append-Only Ledger & RFC 6962 Merkle Engine

### 5.1 Block Anatomy
Each block in the append-only ledger (`blocks.ndjson`) represents an atomic election event. The file is serialized as newline-delimited JSON (NDJSON):

```json
{
  "height": 0,
  "prevHash": "0000000000000000000000000000000000000000000000000000000000000000",
  "type": "VOTE",
  "refHeight": -1,
  "voteHash": "a1b2c3d4e5f6...",
  "tokenHash": "f6e5d4c3b2a1...",
  "receiptHash": "80aa06de5713...",
  "ciphertextB64": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...",
  "timestampSec": 1794172500,
  "merkleRoot": "bb2fd0c75852...",
  "blockHash": "4e7a89bc01df..."
}
```

### 5.2 Cryptographic Linking & Merkle Tree Growth
- **Block Hashing**: Every block hash incorporates the `prevHash` of the immediately preceding block, creating an unbroken SHA-256 chain.
- **Incremental Merkle Tree**: Every block computes an updated `merkleRoot` over all leaves from index $0$ to `height`.
  - For a `VOTE` block, the leaf hash is $\text{SHA-256}(0\text{x}00 \parallel \text{voteHash})$.
  - For an `ORPHAN` block, the leaf hash is $\text{SHA-256}(0\text{x}00 \parallel \text{"VERIVOTE-ORPHAN-v1"} \parallel \text{u64be}(\text{refHeight}))$.
- **Audit Path Generation**: For any leaf at index $i$, the engine produces a logarithmic proof path consisting of sibling node hashes. The client independently verifies that:
$$\text{ComputeRoot}(\text{Leaf}_i, \text{Path}_i) == \text{PublishedRoot}$$

### 5.3 Ledger Fault Tolerance & Torn-Tail Auto-Recovery
The `FileLedgerService` enforces robust crash recovery:
- **Torn-Tail (Trailing incomplete line without newline)**: If a power loss occurs during block write, leaving trailing bytes without a newline delimiter, the parser detects this on startup, logs a diagnostic warning, moves the file pointer to the last valid newline, archives the bad bytes into `blocks.ndjson.torn`, truncates the file in place, and safely resumes.
- **Corrupted Finished Block**: If any newline-terminated line fails JSON parsing, block hash validation, or chain link continuity, it is classified as **fatal tampering**. The server refuses to boot, halting all operations to prevent state divergence.

---

## 6. End-to-End Operational Lifecycle & Workflows

### 6.1 Phase 1: Voter Authentication & Blind Token Issuance
```mermaid
sequenceDiagram
    autonumber
    actor Voter as Registered Voter
    participant Client as Voter Client App
    participant Auth as Auth & Token Controller
    participant DB as System Database

    Voter->>Client: Enter National ID & Password
    Client->>Auth: POST /api/login {nationalId, password}
    Auth->>DB: Validate credentials & check TOTP
    DB-->>Auth: Voter verified (has_voted == FALSE)
    Auth-->>Client: Session JWT Token

    Note over Client: Generate 32-byte tokenPlain<br/>Blind m with random r -> m*
    Client->>Auth: POST /api/web/request-token (Bearer JWT, m*)
    Auth->>DB: Atomic Update: SET has_voted = TRUE
    Auth->>DB: Atomic Increment: issuance_counter.issued++
    Note over Auth: Sign blinded message:<br/>s* = (m*)^d mod N
    Auth-->>Client: Blind Signature s*

    Note over Client: Unblind signature:<br/>s = s* * r^(-1) mod N<br/>Store (tokenPlain, s)
```

### 6.2 Phase 2: Anonymous Ballot Submission
```mermaid
sequenceDiagram
    autonumber
    actor Voter as Voter
    participant Client as Voter Client App
    participant Vote as Vote Controller
    participant Ledger as Append-Only Ledger
    participant DB as System Database

    Note over Client: Select Candidate (e.g. ID #1)<br/>Encrypt with Election Tally PubKey<br/>Compute tokenHash & receiptHash
    Client->>Vote: POST /api/web/cast-vote {candidateId, tokenPlain, signature}
    Note over Vote: 1. Verify RSA signature against Election PubKey<br/>2. Compute tokenHash = SHA-256(SPEND || tokenPlain)

    Vote->>DB: Check & INSERT spent_token (tokenHash)
    Note over DB: Unique constraint prevents double-voting!

    Vote->>Ledger: Append Block (ciphertext, tokenHash, receiptHash)
    Note over Ledger: Compute Block Hash & Updated Merkle Root<br/>Sync to disk (blocks.ndjson)
    Ledger-->>Vote: Block Height #H, Block Hash

    Vote->>DB: INSERT vote cache (receiptHash, height, ciphertext)
    Vote-->>Client: HTTP 200 OK {receiptHash, blockHeight: #H}
    Note over Client: Display Receipt Card to Voter
```

### 6.3 Phase 3: Checkpointing & Multi-Sink External Anchoring
Checkpoints run automatically every 10 blocks or 60 seconds (and on-demand via `POST /bb/checkpoint`):
1. **State Sealing**: Reads current ledger head hash, block count (tree size), and Merkle tree root.
2. **Ed25519 Digital Signing**: The election authority signs the preimage:
   $$\text{"VERIVOTE-CKPT-v1"} \parallel \text{u32be}(\text{treeSize}) \parallel \text{merkleRoot} \parallel \text{headHash}$$
3. **Multi-Sink Distribution**:
   - **`FileSink`**: Saves `ledger/checkpoints/ckpt-{treeSize}.json` and appends to `sink.ndjson`.
   - **`GitSink`**: Commits the checkpoint JSON into an external Git repository with author metadata.
   - **`PrintSink`**: Formats human-readable ASCII / QR representation for physical paper printing.
4. **Database Registration**: Persists record into `checkpoint` table for fast client proof lookups.

### 6.4 Phase 4: Public Merkle Inclusion Proof Verification
```mermaid
sequenceDiagram
    autonumber
    actor Voter as Verifier / Voter
    participant Client as Browser / Client
    participant BB as Public Bulletin Board API
    participant Ledger as Ledger Engine

    Voter->>Client: Input 64-char Receipt Hash
    Client->>BB: GET /api/web/verify/{receiptHash}
    BB->>Ledger: Lookup block by receiptHash
    Ledger-->>BB: Block #H, treeSize N, Merkle Audit Path
    BB->>BB: Lookup latest Checkpoint covering height #H
    BB-->>Client: {status: VERIFIED, blockHeight: #H, treeSize: N, merkleRoot, path: [...], checkpointSignature}

    Note over Client: Client-Side Independent Verification:<br/>1. Evaluate Merkle Path from leaf to root<br/>2. Verify Ed25519 signature over root + treeSize<br/>3. Assert computed root == checkpoint root
    Client-->>Voter: Green Status: Cryptographically Verified in Ledger!
```

### 6.5 Phase 5: Election Tally Ceremony (§8.5)
The election tally is governed by strict cryptographic gates:

```mermaid
flowchart TD
    Start(["Admin triggers Close & Tally"]) --> CloseElection["1. Election status transitioned to CLOSED in DB"]
    CloseElection --> ForceCkpt["2. CheckpointService.forceNow() anchors closeHeadHash"]
    ForceCkpt --> RunAudit["3. Independent AuditService.audit() is executed"]
    RunAudit --> CheckClean{"Audit Clean?\n(blocksTally == false)"}

    CheckClean -- "Dirty (Tampered)" --> AbortTally["ABORT TALLY!\nThrow TallyPreconditionException\nElection remains CLOSED"]
    CheckClean -- "Clean" --> CollectBlocks["4. Read all non-orphan VOTE blocks from disk ledger"]

    CollectBlocks --> ShamirReconstruct["5. Reconstruct RSA Private Key using 2-of-3 Trustee Shares"]
    ShamirReconstruct --> ShuffleBallots["6. Fisher-Yates cryptographically random ballot shuffle"]
    ShuffleBallots --> DecryptBallots["7. Decrypt ciphertexts using reconstructed private key"]
    DecryptBallots --> TabulateVotes["8. Tabulate votes per candidate & record invalid ballots"]
    TabulateVotes --> PublishResults["9. Publish results to Bulletin Board & record TALLY event"]
    PublishResults --> Finish(["Election Tallied & Certified"])
```

### 6.6 Phase 6: Dual-Operator Ledger Recovery Ceremony (§8.4)
If a network partition or system crash produces an orphan block (e.g. block written to disk ledger but database commit aborted, or vice versa):
1. **Identification**: `AuditService` flags finding `BLOCK_WITHOUT_VOTE_ROW` or `VOTE_ROW_WITHOUT_BLOCK`.
2. **Dual-Signer Requirement**: Recovery operations (`ACCEPT_AS_ORPHAN` or `RECOVER_FROM_LEDGER`) **strictly require two distinct operators** holding the `AUDIT` role.
3. **Preimage Signing**: Both operators sign the recovery action preimage offline using their private Ed25519 keys:
   $$\text{"VERIVOTE-RECOVERY-v1"} \parallel \text{u64be}(\text{height}) \parallel \text{ascii}(\text{action}) \parallel 0\text{x}00 \parallel \text{blockHash} \parallel \text{tokenHash} \parallel \text{reason}$$
4. **Execution**: Submitted via `POST /api/admin/recover`. Both signatures are verified against enrolled public keys in `operator`. If valid, the recovery event is permanently anchored, the ledger marks the block as orphaned or inserts the recovered vote, and the audit status returns to clean.

---

## 7. Autonomous Forensic Audit Engine

The `AuditService` continuously evaluates the physical ledger on disk, all external checkpoint sinks, and the database cache to detect any unauthorized mutation.

### 7.1 Complete Audit Finding Catalog

| Finding Code | Severity | Description & Root Cause | Remediation / System Action |
| :--- | :---: | :--- | :--- |
| `LEDGER_UNREADABLE` | `RED` | Physical `blocks.ndjson` file is missing or unreadable on disk. | Restore ledger file from external replica or checkpoint sink. |
| `CHAIN_HASH_MISMATCH` | `RED` | Recomputed block hash does not match stored block hash. Direct tampering detected. | Isolate node; forensic examination of disk blocks. |
| `CHAIN_LINK_BROKEN` | `RED` | Block's `prevHash` does not match `blockHash` of preceding height. | Ledger chain has been spliced or truncated. |
| `CIPHERTEXT_MISMATCH` | `RED` | $\text{SHA-256}(\text{ciphertextB64}) \neq \text{voteHash}$. | Ciphertext was altered after block creation. |
| `MERKLE_ROOT_MISMATCH` | `RED` | Incremental Merkle root recalculation fails to match block's `merkleRoot`. | Merkle tree structure was modified. |
| `ORPHAN_WITHOUT_TARGET` | `RED` | Orphan block references a non-existent block height. | Investigate rogue orphan entry. |
| `DOUBLE_ORPHAN_MARKER` | `RED` | Two separate orphan blocks attempt to orphan the same block height. | Reject duplicate orphan action. |
| `CHAIN_TRUNCATED` | `RED` | Ledger block count is less than previously certified external checkpoint tree size. | Block rollback attack detected! Reject state. |
| `SINK_DIVERGENCE` | `RED` | Merkle root at size $S$ differs between ledger and external sink checkpoint. | External checkpoint divergence; possible split-brain. |
| `CHECKPOINT_BAD_SIGNATURE` | `RED` | Checkpoint signature fails Ed25519 verification against authority public key. | Forged checkpoint detected. |
| `DB_CIPHERTEXT_MISMATCH` | `ALERT` | Database vote row ciphertext does not match ledger block ciphertext. | Database was modified directly. Re-sync DB from ledger. |
| `BLOCK_WITHOUT_VOTE_ROW` | `ALERT` | Valid vote block exists in ledger file but is absent from database. | Run Dual-Operator Recovery (`RECOVER_FROM_LEDGER`). |
| `VOTE_ROW_WITHOUT_BLOCK` | `ALERT` | Database contains a vote row whose block height does not exist on disk. | Delete spurious DB row; investigate injection. |
| `DUPLICATE_TOKEN_HASH` | `ALERT` | The same `tokenHash` appears in multiple non-orphan ledger blocks. | Double-voting violation; investigate token issuance. |
| `VOTES_EXCEED_ISSUED` | `ALERT` | Total votes cast in ledger exceeds total tokens legitimately issued. | Ballot stuffing detected! Admin key compromise suspected. |
| `SINK_AHEAD_OF_DB` | `WARN` | External sink contains valid checkpoint not yet indexed in database. | Refresh database checkpoint index. |
| `ISSUANCE_MISMATCH` | `WARN` | Sum of registered voters marked `has_voted` deviates from issuance counter. | Reconcile issuance counter. |
| `TORN_TAIL_REPAIRED` | `INFO` | An uncompleted write was safely truncated and archived during boot. | Normal recovery after unexpected node restart. |
| `UNCHECKPOINTED_TAIL` | `INFO` | Ledger has new blocks appended since the last periodic checkpoint. | Trigger `POST /bb/checkpoint` to seal state. |

---

## 8. Relational Data Architecture & Anonymity Proof

### 8.1 Schema Overview & Relationship Isolation

```mermaid
erDiagram
    voter {
        BIGINT id PK
        VARCHAR national_id UK
        VARCHAR password_hash
        VARCHAR totp_secret
        BOOLEAN has_voted
        TIMESTAMP created_at
    }

    spent_token {
        VARCHAR token_hash PK
        BIGINT election_id
        TIMESTAMP spent_at
    }

    vote {
        BIGINT id PK
        BIGINT election_id
        VARCHAR token_hash
        VARCHAR receipt_hash UK
        TEXT encrypted_vote
        BIGINT block_height
        VARCHAR block_hash
    }

    checkpoint {
        BIGINT id PK
        INT tree_size
        VARCHAR merkle_root
        VARCHAR head_hash
        TEXT signature
        TIMESTAMP created_at
    }

    operator {
        BIGINT id PK
        VARCHAR name UK
        VARCHAR ed25519_pub
        VARCHAR role
        TIMESTAMP active_from
        TIMESTAMP active_to
    }

    recovery_event {
        BIGINT id PK
        BIGINT block_height UK
        VARCHAR action
        VARCHAR block_hash
        VARCHAR token_hash
        VARCHAR reason
        VARCHAR operator1
        TEXT signature1
        VARCHAR operator2
        TEXT signature2
    }
```

### 8.2 The Anonymity Invariant
**Theorem (Identity-Ballot Independence)**: Let $V$ be the set of registered voters, $T$ be the set of issued tokens, and $B$ be the set of cast ballots on the ledger. For any ballot $b \in B$ and voter $v \in V$:
$$\mathbb{P}(b \text{ was cast by } v) = \frac{1}{|V_{\text{voted}}|}$$
*Proof Sketch*:
1. During token issuance, voter identity $v$ is verified, and the server signs $m^* = (m \cdot r^e) \pmod N$. By the blinding property of RSA, $m^*$ is statistically independent of $m = \text{SHA-256}(\text{tokenPlain})$.
2. The random blinding factor $r$ is generated exclusively in client memory and discarded after unblinding.
3. During ballot submission, the voter connects over a new, unauthenticated session presenting only $(\text{tokenPlain}, s)$ and ciphertext $c$.
4. The database records `token_hash = SHA-256("VERIVOTE-SPEND-v1" || eid || tokenPlain)` in `spent_token`. Neither $\text{tokenPlain}$ nor `token_hash` exists in the `voter` table or application session store.
5. There are **zero foreign keys** connecting `voter` to `spent_token` or `vote`. Identity and ballot choice are mathematically and architecturally severed. $\blacksquare$

---

## 9. Comprehensive REST API Reference

### 9.1 Voter Authentication & Credentials
- **`POST /api/register`**: Registers a new national voter.
  - *Request*: `{"nationalId": "NID-12345", "displayName": "Alice", "password": "Password123"}`
  - *Response*: `{"voterId": 1, "totpSecret": "JBSWY3DPEHPK3PXP", "totpUri": "otpauth://..."}`
- **`POST /api/login`**: Authenticates voter credentials and issues a session token.
  - *Request*: `{"nationalId": "NID-12345", "password": "Password123"}`
  - *Response*: `{"sessionToken": "jwt.bearer.token"}`

### 9.2 Token Issuance & Anonymous Ballot Casting
- **`POST /api/web/request-token`**: Issues an anonymous blind-signed voting credential.
  - *Header*: `Authorization: Bearer <JWT>`
  - *Response*: `{"tokenPlain": "32-byte-hex", "signature": "base64-rsa-sig"}`
- **`POST /api/web/cast-vote`**: Submits an encrypted ballot to the append-only ledger.
  - *Request*: `{"candidateId": 1, "tokenPlain": "...", "signature": "..."}`
  - *Response*: `{"status": "CAST", "receiptHash": "80aa06de...", "blockHeight": 1}`
- **`GET /api/web/verify/{receiptHash}`**: Evaluates an inclusion proof against the signed checkpoint tree.
  - *Response*: `{"status": "VERIFIED", "blockHeight": 1, "treeSize": 1, "merkleRoot": "bb2f...", "checkpointSignature": "..."}`

### 9.3 Public Bulletin Board
- **`GET /bb/receipts`**: Returns all published cryptographic receipt hashes on the ledger.
- **`GET /bb/checkpoints`**: Returns all signed checkpoints and Merkle roots.
- **`POST /bb/checkpoint`**: Manually triggers an immediate checkpoint sealing pass.
- **`GET /bb/tally`**: Returns the official tallied election results, invalid count, and closing Merkle root.

### 9.4 Administrative Governance & Ceremonies
- **`GET /api/admin/audit`**: Executes a complete on-demand forensic ledger audit.
  - *Response*: `{"clean": true, "ledgerBlockCount": 5, "ledgerRealVoteCount": 5, "ledgerOrphanCount": 0, "dbVoteCount": 5, "findings": []}`
- **`GET /api/admin/tally/shares`**: Returns the Shamir key shares for authorized trustee custody.
- **`POST /api/admin/tally?electionId=1`**: Reconstructs the election tally key using 2-of-3 shares, closes the election, and publishes decrypted totals.
  - *Request*: `[{"index": 1, "value": "base64-share-1"}, {"index": 2, "value": "base64-share-2"}]`
- **`POST /api/admin/recover`**: Executes a dual-operator authorized ledger recovery.
  - *Request*: `{"blockHeight": 2, "action": "ACCEPT_AS_ORPHAN", "reason": "...", "operator1": "...", "signature1": "...", "operator2": "...", "signature2": "..."}`
- **`GET /api/admin/operators`**: Lists all enrolled cryptographic operators and their active roles.
- **`POST /api/admin/operators`**: Enrolls a new Ed25519 operator public key.

---

## 10. Threat Model & Security Evaluation (T1–T27)

| Threat ID | Asset at Risk | Adversary Model | Attack Vector / Strategy | Cryptographic Mitigation | Residual Status |
| :---: | :--- | :--- | :--- | :--- | :---: |
| **T1** | Ballot Secrecy | Curious Admin | Inspects database tables | No FK between voter and vote; blind tokens unlink identity | **Mitigated** |
| **T2** | Ballot Secrecy | Network Sniffer | Intercepts ballot in transit | Client-side RSA-OAEP encryption with tally authority public key | **Mitigated** |
| **T3** | Vote Integrity | Malicious DB Admin | `UPDATE vote SET candidate_id = 2` | Append-only ledger file on disk is truth; Audit flags `DB_CIPHERTEXT_MISMATCH` | **Mitigated** |
| **T4** | Vote Integrity | Malicious DB Admin | `DELETE FROM vote` / truncates DB | External signed checkpoints & disk ledger reconstruct missing rows | **Mitigated** |
| **T5** | Double Voting | Dishonest Voter | Attempts to cast two ballots | Token uniqueness enforced via `spent_token` primary key; `has_voted` check | **Mitigated** |
| **T6** | Ballot Stuffing | Malicious Admin | Injects fabricated votes | Valid blind signature required; issuance reconciliation counter caps total | **Mitigated (L15)** |
| **T7** | Coercion | Coercer / Buyer | Forces voter to pick candidate | Out of scope for iteration 1; receipt-freeness requires mix-nets | **Future (L1)** |
| **T8** | Verification | Apathetic Voter | Skips verification step | 1-Click automated verification flow on receipt screen ($\le 3$ clicks) | **Mitigated** |
| **T9** | Tally Integrity | Corrupted Tallier | Falsifies vote sum | All ciphertexts published on BB; 2-of-3 Shamir shares required to decrypt | **Mitigated** |
| **T10**| Identity Theft | Phisher / Sniffer | Steals voter password | Mandatory Time-Based One-Time Password (TOTP RFC 6238) 2FA | **Mitigated** |
| **T11**| Ballot Secrecy | Admin with Logs | Correlates timestamp & order | Random client delay (30–300s); timestamps omitted from vote/token records | **Mitigated** |
| **T12**| Ledger Integrity| Host Compromise | Rewrites disk ledger | Checkpoints signed via Ed25519 replicated to immutable Git & File sinks | **Mitigated** |
| **T13**| Client Integrity| Compromised API | Returns fake `VERIFIED` flag | Client independently recalculates Merkle path and verifies Ed25519 signature | **Mitigated** |
| **T14**| Network Anonymity| ISP / Passive Sniffer| Correlates client IP traffic | Client delay jitter; full mitigation requires Tor/Mixnet integration | **Partial (L4b)** |
| **T15**| Token Theft | Malicious Admin | Resets voter's `has_voted` flag| Published issuance counter detects discrepancy between issued and cast counts | **Mitigated** |
| **T16**| Cross-Election | Replay Attacker | Replays token in different poll| Domain-separated preimage includes `int64be(electionId)` | **Mitigated** |
| **T17**| Token Splicing | MITM Attacker | Slices token bytes | Fixed-length 32-byte token plain enforced across all decoding routines | **Mitigated** |
| **T18**| Collision Attack | Quantum/Cryptanalyst| Generates SHA-256 collision | SHA-256 Merkle leaves utilize standard RFC 6962 $0\text{x}00/0\text{x}01$ domain bytes | **Mitigated** |
| **T19**| Unchecked Orphan| Rogue Auditor | Accepts orphan without proof | Dual-operator Ed25519 signatures mandatory for `ACCEPT_AS_ORPHAN` | **Mitigated** |
| **T20**| Replay Attack | Replay Attacker | Replays recovery signature | Unique event ID and block height bound into recovery signature preimage | **Mitigated** |
| **T21**| Premature Tally | Early Decryption | Decrypts before poll closes | Election must transition to `CLOSED` before tally engine accepts Shamir shares | **Mitigated** |
| **T22**| Key Custody | Rogue Trustee | Attempts solo decryption | $k=2$ threshold ensures single trustee possesses zero key information | **Mitigated** |
| **T23**| Post-Close Append| Malicious Admin | Appends vote after tally | `forceNow()` seals head hash at election close; tally enforces height limit | **Mitigated** |
| **T24**| Dirty Tally | Admin with Tamper | Forces tally with broken audit | `DefaultTallyService` throws `TallyPreconditionException` if audit dirty | **Mitigated** |
| **T25**| Crash Rollback | Power Loss Crash | Uncommitted DB rows | `JpaRecoveryData` reconciles uncommitted ledger blocks back into database | **Mitigated** |
| **T26**| Spurious Checkpoint| Spoofed Signer | Emits invalid checkpoint | Client pins election authority Ed25519 public key in client binary | **Mitigated** |
| **T27**| Denial of Service | Botnet Flooding | Floods vote endpoint | IP-based rate limiting & strict token validation before database access | **Mitigated** |

---

## 11. Known System Limitations (L1–L15)

In accordance with transparent cryptographic engineering standards, VeriVote-J documents all architectural constraints:

- **L1 (No Coercion Resistance)**: The system does not prevent a coercer from observing a voter at the screen. Coercion resistance (e.g. JCJ / Civitas fake-credential protocols) is deferred to future iterations.
- **L2 (Trusted Tallier for Decryption Execution)**: Once the Shamir threshold key is reconstructed in memory, the tally service decrypts ciphertexts. Verifiable mix-nets or homomorphic encryption (Paillier / ElGamal) would remove this trust requirement.
- **L3 (Admin Holds Blind Signature Key)**: The election administrator holds the RSA blind signing key during polling. Multi-party threshold blind signing is planned for enterprise deployments.
- **L4a & L4b (Network Metadata Correlation)**: The server observes client IP addresses. A state-level network adversary observing both issuance and submission traffic could attempt traffic analysis. Tor / onion routing provides complete mitigation.
- **L5 (No Zero-Knowledge Proofs of Correct Decryption)**: The tally engine publishes plaintext ballots and total counts. ZK-SNARK proofs of decryption are not implemented in this version.
- **L6 (TOTP Authentication Boundary)**: Voter 2FA relies on RFC 6238 TOTP. Hardware FIDO2 / WebAuthn tokens are not currently integrated.
- **L7 (Tally Shuffle Verification)**: Ballots are shuffled using Fisher-Yates with `SecureRandom`. Observers cannot mathematically verify that the shuffle was honest without verifiable mix-net proofs.
- **L8 (Filesystem Ledger Root)**: The ledger is stored on the server's local disk. Replicated consensus ledgers (Raft or BFT) can be integrated via `LedgerService`.
- **L9 (Transferable Proof of Vote)**: Because the voter receives a cryptographic receipt containing their unique `receiptHash`, a voter can willingly show this receipt to a vote-buyer.
- **L10 (No Token Revocation)**: An issued blind token cannot be revoked without resetting the election.
- **L11 (Single Election Authority Identity)**: Checkpoints are signed by a single master election authority Ed25519 key rather than a multi-signature committee.
- **L12 (Unchained Application Logs)**: General application logs outside `blocks.ndjson` are standard logback files and are not hash-chained.
- **L13 (Client Binary Trust)**: JavaFX and Web clients rely on TLS channel security for binary distribution.
- **L14 (Timing Analysis Window)**: Client delay jitter (30–300s) mitigates, but does not completely eliminate, timing correlation attacks during periods of exceptionally low voter turnout.
- **L15 (Admin Ballot Stuffing Bound)**: A malicious admin possessing the blind signing key can create tokens, but cannot exceed the total registered voter population without immediately triggering `VOTES_EXCEED_ISSUED` in the audit service.

---

## 12. Verification, Build & Deployment Runbook

### 12.1 Prerequisites
- **Java Development Kit (JDK)**: Version 21 (Eclipse Temurin recommended)
- **Build Tool**: Apache Maven 3.9+
- **Container Engine**: Docker 24+ (optional for containerized deployment)

### 12.2 Local Compilation & Test Execution
```bash
# Clone the repository
git clone https://github.com/subratkumarpadhy4/javaproject-2nd-year.git
cd javaproject2026

# Compile all modules with Maven
mvn clean compile

# Execute the complete test suite across all 5 modules (25+ test cases)
mvn test
```

### 12.3 Running the Spring Boot Web Platform
```bash
# Run the web module directly
mvn spring-boot:run -pl verivote-web
```
The server will bind to `http://localhost:8080`.
Open `http://localhost:8080` in your browser to access the complete responsive Web UI (Vote Portal, Verify Vote, Bulletin Board, and Admin Console).

### 12.4 Running the JavaFX Desktop Client
```bash
# Launch Voter Desktop App
mvn javafx:run -pl verivote-client

# Launch Admin & Auditor Desktop App
mvn exec:java -pl verivote-client -Dexec.mainClass="com.verivote.client.admin.AdminApp"
```

### 12.5 Production Docker Security & Container Hardening Architecture

VeriVote-J’s containerization strategy follows enterprise zero-trust principles:

1. **Multi-Stage Build Isolation**:
   - The [`Dockerfile`](Dockerfile) uses a two-stage build pipeline:
     - **Stage 1 (Builder)**: Uses `maven:3.9.9-eclipse-temurin-21` to compile the multi-module Java 21 codebase and execute tests.
     - **Stage 2 (Runtime)**: Extracts only the final repackaged artifact (`verivote-web.jar`) into an official, lightweight `eclipse-temurin:21-jre` runtime base image.
   - **Zero Build Tool Leakage**: Compilers, Maven dependencies, intermediate `.class` files, git metadata, and local source repositories are completely excluded from the runtime container image, minimizing the potential attack surface.

2. **Persistent Volume Isolation (`/data`)**:
   - In accordance with the append-only ledger security model, cryptographic state is decoupled from the ephemeral container lifecycle:
     - Append-only block ledger: `/data/ledger/blocks.ndjson`
     - Signed digital checkpoints: `/data/ledger/checkpoints`
   - Mounting a persistent volume at `/data` ensures that container restarts, deployments, or rollbacks cannot delete or truncate historical election proofs or audit trails.

3. **Zero Hardcoded Secrets & Dynamic Environment Injection**:
   - The Docker image contains **zero embedded credentials, database passwords, or private cryptographic keys**.
   - All sensitive runtime variables are injected dynamically at container startup:
     ```bash
     docker run -d \
       -p 8080:8080 \
       -e PORT=8080 \
       -e SPRING_PROFILES_ACTIVE=prod \
       -e DATABASE_URL="postgresql://user:pass@host:5432/verivotedb" \
       -e VERIVOTE_HMAC_MASTER_KEY="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef" \
       -v verivote-ledger-data:/data \
       --name verivote-prod \
       verivote-backend:latest
     ```

4. **Resource Constraints & Denial-of-Service (DoS) Defense**:
   - The runtime container specifies strict JVM memory boundaries:
     ```bash
     JAVA_OPTS="-Xms256m -Xmx512m"
     ```
   - This prevents out-of-memory container thrashing, ensures deterministic heap allocation, and isolates the application from host-level memory starvation attacks.

5. **Headless Execution & Attack Surface Minimization**:
   - Uses headless JRE 21 with no graphical subsystem or unnecessary system utilities, drastically reducing the package surface area eligible for Common Vulnerabilities and Exposures (CVEs).

---

## 13. Step-by-Step Election Demonstration Script

For academic presentations, security audits, and laboratory evaluations:

1. **Voter Registration & Login**:
   - Navigate to the **Vote Portal** tab.
   - Click *"Register as new voter"*, enter National ID `NID-1001`, Name `Alice`, and Password `Password123`.
   - Sign in with credentials.
2. **Blind Token Issuance**:
   - Click *"Request Blind-Signed Token"*. The client blinds a random 256-bit token plain, acquires the server's RSA signature, and unblinds it locally.
3. **Encrypted Ballot Casting**:
   - Select candidate (Alice Johnson, Bob Smith, or Charlie Davis).
   - Click *"Cast Encrypted Vote"*. The client encrypts the vote under the election public key and records the block into the append-only ledger.
   - Note the assigned **Block Height** and **Receipt Hash**.
4. **Independent Cryptographic Verification**:
   - Click *"Verify Vote in Ledger (1 Click)"*.
   - The verifier displays the inclusion proof, Merkle path, tree size, computed Merkle root, and Ed25519 checkpoint signature.
5. **Inspect Public Bulletin Board**:
   - Switch to the **Bulletin Board** tab. Observe the real-time list of receipts and signed checkpoints.
6. **Execute Forensic Integrity Audit**:
   - Switch to the **Admin Console** $\rightarrow$ **Audit & Integrity** tab.
   - Click *"Run Independent Audit"*. Observe that the ledger status reports `OVERALL: CLEAN` with zero findings.
7. **Simulate Database Tampering & Forensic Detection**:
   - Directly modify or delete a vote row in the database using the H2 console (`/h2-console`).
   - Re-run the audit. The system immediately catches the mutation, reporting `OVERALL: TAMPERED` with finding code `DB_CIPHERTEXT_MISMATCH` or `BLOCK_WITHOUT_VOTE_ROW`.
8. **Execute Dual-Operator Recovery**:
   - In the **Recovery Ceremony** tab, input the block height, select `RECOVER_FROM_LEDGER`, provide two authorized operator signatures, and execute recovery. The database is restored from the ledger, and the status returns to `CLEAN`.
9. **Execute Election Tally Ceremony**:
   - In the **Election Tally** tab, click *"Load Trustee Shares"* (loads 2 of 3 Shamir shares satisfying the threshold).
   - Click *"Execute Tally Ceremony"*. The election status locks to `CLOSED`, votes are decrypted and tallied, and final results are posted to the public bulletin board.

---

## 14. Academic & Architectural Reference Metadata

- **System Version**: `0.1.0-SNAPSHOT`
- **Architectural Baseline**: RFC 9474 (Blind RSA), RFC 6962 (Certificate Transparency Merkle Trees), RFC 6238 (TOTP), RFC 8032 (Ed25519)
- **Target Deployment Platform**: Java 21 LTS / Docker Container / Railway Cloud PaaS
- **Live Public URL**: [https://verivote-backend-production.up.railway.app](https://verivote-backend-production.up.railway.app)
- **Primary Source Code Repository**: [https://github.com/subratkumarpadhy4/javaproject-2nd-year.git](https://github.com/subratkumarpadhy4/javaproject-2nd-year.git)
