#!/usr/bin/env bash
# VeriVote-J §8.4 Tamper Detection Demo
# Run from repo root: bash demo/demo.sh

set -e

BASE_URL="http://localhost:8080"
LEDGER_PATH="verivote-web/ledger/blocks.ndjson"

echo "═══════════════════════════════════════════════"
echo "VeriVote-J Tamper Detection Demo (§8.4)"
echo "═══════════════════════════════════════════════"
echo ""

# Setup: register a voter, cast 3 votes, force checkpoint
# (Uses curl. Requires server running on $BASE_URL.)

echo "Step 0: Setup — register voter, cast 3 votes, checkpoint"
echo "This script assumes:"
echo "  - Server running at $BASE_URL"
echo "  - At least 3 votes already cast (via the JavaFX voter client)"
echo "  - At least one checkpoint exists (auto-created every 10 blocks or 60s)"
echo ""
read -p "Press ENTER when ready..."

# SCENARIO 1: DB ciphertext tampering
echo ""
echo "─── SCENARIO 1: UPDATE vote SET encrypted_vote = garbage ───"
echo "Effect: audit must report DB_CIPHERTEXT_MISMATCH (RED)"
echo ""
echo "Instruction:"
echo "  1. Open H2 console: http://localhost:8080/h2-console"
echo "     (JDBC URL: jdbc:h2:mem:verivotedb, user: sa, no password)"
echo "  2. Run: UPDATE vote SET encrypted_vote = 'AAAA' WHERE id = 1;"
echo "  3. Run the audit view in the admin client, or curl:"
echo "     curl $BASE_URL/api/admin/audit | jq"
echo "  4. Expect: findings contains DB_CIPHERTEXT_MISMATCH"
echo ""
read -p "Press ENTER to continue to Scenario 2..."

# SCENARIO 2: DB vote row deletion
echo ""
echo "─── SCENARIO 2: DELETE FROM vote WHERE id = 2 ───"
echo "Effect: audit reports BLOCK_WITHOUT_VOTE_ROW (ALERT)"
echo ""
echo "Instruction:"
echo "  1. In H2 console: DELETE FROM vote WHERE id = 2;"
echo "  2. curl $BASE_URL/api/admin/audit | jq"
echo "  3. Expect: BLOCK_WITHOUT_VOTE_ROW at the corresponding block height"
echo "  4. Then recover via admin UI (RECOVER_FROM_LEDGER) or curl:"
echo "     curl -X POST $BASE_URL/api/admin/recover -d '{...}'"
echo "  5. Re-run audit → CLEAN"
echo ""
read -p "Press ENTER to continue to Scenario 3..."

# SCENARIO 3: Ledger truncation
echo ""
echo "─── SCENARIO 3: Truncate $LEDGER_PATH ───"
echo "Effect: audit reports CHAIN_TRUNCATED (RED), OR server refuses restart"
echo ""
echo "Instruction:"
echo "  1. head -c <half> $LEDGER_PATH > /tmp/truncated.ndjson"
echo "     mv /tmp/truncated.ndjson $LEDGER_PATH"
echo "     (or: sed -i '' '3d' $LEDGER_PATH  — deletes line 3)"
echo "  2. curl $BASE_URL/api/admin/audit | jq"
echo "  3. Expect: CHAIN_TRUNCATED against last checkpoint"
echo "  4. STOP server (Ctrl+C in the run window). Restart:"
echo "     cd verivote-web && mvn spring-boot:run"
echo "  5. Expect: startup fails with 'refuse to start' (mid-file corruption)"
echo ""
read -p "Press ENTER to continue to Scenario 4..."

# SCENARIO 4: Append garbage
echo ""
echo "─── SCENARIO 4: Append garbage line to ledger ───"
echo "Effect: restart refuses; audit reports MALFORMED_LINE (RED)"
echo ""
echo "Instruction:"
echo "  1. echo '{\"garbage\": true}' >> $LEDGER_PATH"
echo "  2. STOP server. Restart: cd verivote-web && mvn spring-boot:run"
echo "  3. Expect: startup fails immediately with parse error"
echo "  4. Remove the garbage: sed -i '' '\$d' $LEDGER_PATH"
echo "  5. Restart — succeeds again"
echo ""

echo "═══════════════════════════════════════════════"
echo "Demo complete."
echo "═══════════════════════════════════════════════"
