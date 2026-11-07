# VeriVote-J Domain Specification

**This file is the SOURCE OF TRUTH for every byte that goes into a hash preimage,
a signature preimage, or a Merkle leaf. Any deviation from these definitions is a bug.**

> Blind signature scheme: blind RSA-PSS (RFC 9474 family).
> Never claim RFC 9474 compliance.

---

## Fixed-width encoding

- `u32be(x)`: 4 bytes, unsigned big-endian
- `u64be(x)`: 8 bytes, unsigned big-endian
- `int64be(x)`: 8 bytes, signed big-endian (two's complement)
- All hash outputs stored in DB as lowercase hex (no "0x" prefix)
- Inside hash preimages and Merkle leaves: raw bytes only

---

## Domain-separation preimages

Each preimage is a byte concatenation. No separators are inserted between fields
except `0x00` where explicitly shown.

```
Token signature:   "VERIVOTE-TOKEN-v1"    || int64be(eid) || tokenPlain

Spend hash:        SHA-256("VERIVOTE-SPEND-v1"    || int64be(eid) || tokenPlain)

Receipt hash:      SHA-256("VERIVOTE-RECEIPT-v1"  || int64be(eid) || nonce || tokenHash)

Block hash:        SHA-256("VERIVOTE-BLOCK-v1"
                           || typeByte(1=VOTE, 2=ORPHAN)
                           || u64be(height)
                           || u64be(refHeight)
                           || prevHash[32]
                           || voteHash[32]
                           || tokenHash[32]
                           || receiptHash[32]
                           || u64be(timestampSec)
                           || merkleRoot[32])

Merkle leaf:       SHA-256(0x00 || data)

Merkle node:       SHA-256(0x01 || left || right)   ← RFC 6962 standard

Empty tree:        SHA-256(0x02)

ORPHAN leaf:       leafHash("VERIVOTE-ORPHAN-v1" || u64be(refHeight))

Checkpoint sig:    "VERIVOTE-CKPT-v1"     || u32be(treeSize) || root || head

Recovery sig:      "VERIVOTE-RECOVERY-v1"
                   || u64be(height)
                   || ascii(action)
                   || 0x00
                   || blockHash
                   || tokenHash
                   || reason

Issuance-pub sig:  "VERIVOTE-ISSUANCE-v1"
                   || u64be(eid)
                   || u64be(issued)
                   || u64be(resets)
                   || u64be(votesCast)
                   || u64be(publishedAtSec)
```

---

## Field size reference

| Field | Size | Notes |
|-------|------|-------|
| `prevHash`, `voteHash`, `tokenHash`, `receiptHash`, `merkleRoot`, `blockHash` | 32 bytes | Raw SHA-256 output |
| `typeByte` | 1 byte | 1 = VOTE, 2 = ORPHAN |
| `height`, `refHeight`, `timestampSec` | 8 bytes | `u64be` |
| `treeSize` | 4 bytes | `u32be` |
| `eid`, `height` (signed) | 8 bytes | `int64be` |
| Merkle domain bytes | 1 byte | 0x00 leaf, 0x01 node, 0x02 empty |

---

## Constant identifiers (see `DomainStrings.java`)

| Java constant | Value |
|---------------|-------|
| `TOKEN_PREFIX` | `"VERIVOTE-TOKEN-v1"` |
| `SPEND_PREFIX` | `"VERIVOTE-SPEND-v1"` |
| `RECEIPT_PREFIX` | `"VERIVOTE-RECEIPT-v1"` |
| `BLOCK_PREFIX` | `"VERIVOTE-BLOCK-v1"` |
| `ORPHAN_PREFIX` | `"VERIVOTE-ORPHAN-v1"` |
| `CKPT_PREFIX` | `"VERIVOTE-CKPT-v1"` |
| `RECOVERY_PREFIX` | `"VERIVOTE-RECOVERY-v1"` |
| `ISSUANCE_PREFIX` | `"VERIVOTE-ISSUANCE-v1"` |
| `LEAF_DOMAIN` | `0x00` |
| `NODE_DOMAIN` | `0x01` |
| `EMPTY_ROOT_DOMAIN` | `0x02` |
| `TYPE_BYTE_VOTE` | `1` |
| `TYPE_BYTE_ORPHAN` | `2` |
| `CLAIM_STRING` | `"blind RSA-PSS (RFC 9474 family)"` |
