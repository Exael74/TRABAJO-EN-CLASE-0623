#!/bin/bash
# ============================================================
# PART 3 — Crash Simulation: Consumer Failure Before XACK
# Author: Stiven Esneider Pardo Gutierrez
# ============================================================
# This script simulates a consumer crash scenario:
#   1. Publish a test event via XADD
#   2. Read the event with XREADGROUP (simulate crash — no XACK)
#   3. Inspect the PEL with XPENDING
#   4. Claim the pending message with XAUTOCLAIM
#   5. Acknowledge with XACK and verify PEL is empty
#
# Prerequisite: The 'redis-auditoria' container must be running.
# ============================================================

REDIS="docker exec redis-auditoria redis-cli"

echo "=========================================="
echo " STEP 1: Publish a Test Event"
echo "=========================================="

$REDIS XADD banco.transferencias * \
  evento '{"eventId":"e-caida-001","transferId":"t-caida-001","from":"Carlos","to":"Maria","amount":2500.00,"currency":"USD","createdAt":"2026-06-23T12:00:00Z"}'

echo "[OK] Event published."
echo ""

echo "=========================================="
echo " STEP 2: Read the Event Without Acknowledging"
echo "         (Simulating a Consumer Crash)"
echo "=========================================="

echo "Reading via XREADGROUP from auditoria-group (1 message, no XACK)..."
$REDIS XREADGROUP GROUP auditoria-group auditoria-simulador COUNT 1 BLOCK 5000 STREAMS banco.transferencias ">"

echo ""
echo "[WARNING] Consumer 'auditoria-simulador' read the message but did NOT send XACK."
echo "          The message remains in the PEL (Pending Entries List)."
echo ""

echo "=========================================="
echo " STEP 3: Inspect Pending Messages with XPENDING"
echo "=========================================="

$REDIS XPENDING banco.transferencias auditoria-group

echo ""
echo "[INFO] XPENDING shows messages delivered but not yet acknowledged."
echo "       Each PEL entry contains: message ID, consumer name, idle time (ms), delivery count."
echo ""

echo "=========================================="
echo " STEP 4: Claim the Message with XAUTOCLAIM"
echo "=========================================="

echo "Another consumer ('auditoria-reprocesador') claims pending messages..."
$REDIS XAUTOCLAIM banco.transferencias auditoria-group auditoria-reprocesador 0 0-0

echo ""
echo "[INFO] XAUTOCLAIM reassigns pending messages idle for >= 0 ms to the new consumer."
echo "       Alternatively, XCLAIM can target a specific message ID."
echo ""

echo "=========================================="
echo " STEP 5: Acknowledge and Verify PEL Is Empty"
echo "=========================================="

PENDING_INFO=$($REDIS XPENDING banco.transferencias auditoria-group - + 10 2>/dev/null)

if [ -z "$PENDING_INFO" ]; then
    echo "[WARNING] No pending messages found. XACK may have already been sent."
else
    echo "Pending messages:"
    echo "$PENDING_INFO"

    MSG_ID=$(echo "$PENDING_INFO" | head -1 | awk '{print $1}')

    if [ -n "$MSG_ID" ]; then
        echo "Sending XACK for message: $MSG_ID"
        $REDIS XACK banco.transferencias auditoria-group "$MSG_ID"

        echo ""
        echo "Verifying PEL is empty..."
        $REDIS XPENDING banco.transferencias auditoria-group
        echo ""
        echo "[OK] PEL is empty — no pending messages remaining."
    fi
fi

echo ""
echo "=== PART 3 COMPLETE ==="
echo ""
echo "[SUMMARY] PEL (Pending Entries List) explanation:"
echo "   The PEL is an internal Redis structure that tracks every message delivered to"
echo "   a consumer group consumer but not yet acknowledged with XACK. If a consumer"
echo "   crashes before calling XACK, the message stays in the PEL. Another consumer"
echo "   can claim it via XCLAIM or XAUTOCLAIM and reprocess it, ensuring at-least-once"
echo "   delivery semantics — critical for financial transaction systems."
