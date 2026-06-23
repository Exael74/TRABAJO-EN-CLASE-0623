# Part 3 — Crash Simulation: Consumer Failure Before XACK

**Author:** Stiven Esneider Pardo Gutierrez  
**Course:** Software Architecture  
**Date:** June 23, 2026

---

## Objective

Simulate a real-world failure scenario where a consumer reads a message from the `banco.transferencias` stream but crashes **before** calling `XACK`. Demonstrate how Redis Streams handle this via the Pending Entries List (PEL), and how another consumer can claim and reprocess the unacknowledged message using `XAUTOCLAIM` or `XCLAIM`.

---

## Background: The Pending Entries List (PEL)

When a consumer in a consumer group reads a message via `XREADGROUP`, Redis immediately adds that message to the group's **PEL** (Pending Entries List). The PEL entry contains:

- **Message ID** — Unique identifier in the stream
- **Consumer name** — Which consumer has the message
- **Idle time** — Milliseconds since the message was last delivered
- **Delivery count** — How many times the message has been delivered

The message remains in the PEL **until the consumer sends `XACK`**. If the consumer crashes before acknowledging, the message stays in the PEL indefinitely. Another consumer can then **claim** the message using `XAUTOCLAIM` or `XCLAIM`, transferring ownership and reprocessing it.

This mechanism provides **at-least-once delivery** semantics: every message is guaranteed to be processed at least once, even if consumers fail.

---

## Step-by-Step Manual Simulation

### 1. Publish a Test Event

```bash
docker exec redis-auditoria redis-cli XADD banco.transferencias * \
  evento '{"eventId":"e-caida-001","transferId":"t-caida-001","from":"Carlos","to":"Maria","amount":2500.00,"currency":"USD","createdAt":"2026-06-23T12:00:00Z"}'
```

**What happens:** Redis appends a new entry to the `banco.transferencias` stream with an auto-generated ID (e.g., `1719122400001-0`). The message is now available to all consumer groups.

### 2. Simulate a Consumer Crash (Read Without XACK)

```bash
docker exec redis-auditoria redis-cli XREADGROUP GROUP auditoria-group auditoria-simulador COUNT 1 BLOCK 5000 STREAMS banco.transferencias ">"
```

**What happens:**
- Consumer `auditoria-simulador` requests one new message from `auditoria-group`.
- Redis delivers the message and adds it to the PEL for `auditoria-group`.
- The consumer **does NOT call `XACK`**, simulating a crash.
- The message remains in the PEL, marked as owned by `auditoria-simulador`.

### 3. Inspect Pending Messages with XPENDING

```bash
docker exec redis-auditoria redis-cli XPENDING banco.transferencias auditoria-group
```

**Expected output (example):**
```
1) 1) "1719122400001-0"
   2) "auditoria-simulador"
   3) (integer) 12345   ← idle time in milliseconds
   4) (integer) 1       ← delivery count
```

**What happens:** `XPENDING` shows all messages in the PEL that have not been acknowledged. Each entry reveals:
- The message ID that is stuck
- Which consumer holds it
- How long it has been idle
- How many times it has been redelivered

### 4. Claim the Message with XAUTOCLAIM

```bash
docker exec redis-auditoria redis-cli XAUTOCLAIM banco.transferencias auditoria-group auditoria-reprocesador 0 0-0
```

**Alternative — claim a specific message with XCLAIM:**

```bash
docker exec redis-auditoria redis-cli XCLAIM banco.transferencias auditoria-group auditoria-reprocesador 0 <MESSAGE-ID>
```

**What happens:**
- `XAUTOCLAIM` scans the PEL for messages idle for at least `0` milliseconds (all pending messages).
- It reassigns the found message(s) to `auditoria-reprocesador`.
- The delivery count increments (now 2), reflecting that the message is being reprocessed.
- `XAUTOCLAIM` is the modern, recommended approach (Redis 6.2+) as it handles multiple messages efficiently.
- `XCLAIM` targets a specific message by ID.

### 5. Acknowledge and Verify

```bash
# Get the message ID from the XPENDING output first
docker exec redis-auditoria redis-cli XACK banco.transferencias auditoria-group <MESSAGE-ID>

# Verify PEL is now empty
docker exec redis-auditoria redis-cli XPENDING banco.transferencias auditoria-group
```

**What happens:**
- `XACK` tells Redis the message has been successfully processed.
- Redis removes the message from the PEL.
- `XPENDING` now returns an empty list (or `(empty array)`), confirming no pending messages remain.

---

## Automated Script

```bash
chmod +x simular-caida.sh
./simular-caida.sh
```

The script automates all five steps:
1. Publishes a test event.
2. Reads it with `auditoria-simulador` without acknowledging.
3. Shows `XPENDING` output.
4. Uses `XAUTOCLAIM` to reassign the pending message.
5. Sends `XACK` and verifies the PEL is empty.

---

## Key Concepts Reference

| Concept | Description |
|---------|-------------|
| **PEL (Pending Entries List)** | Internal Redis list tracking every delivered but unacknowledged message per consumer group |
| **XACK** | Acknowledges a message, removing it from the PEL |
| **XCLAIM** | Reassigns a specific pending message from one consumer to another |
| **XAUTOCLAIM** | Automatically claims all pending messages exceeding a specified idle time (Redis 6.2+) |
| **Delivery count** | Number of times a message has been delivered (increments on each XCLAIM/XAUTOCLAIM) |
| **Idle time** | Milliseconds since the message was last delivered to a consumer |
| **At-least-once delivery** | Guarantee that every message is processed at least once; duplicates are possible but no message is lost |

---

## Educational Summary

This simulation demonstrates the **fault-tolerance** mechanism built into Redis Streams consumer groups:

1. **Normal flow:** Consumer reads → processes → acknowledges (XACK) → message removed from PEL.
2. **Crash scenario:** Consumer reads → crashes before XACK → message stays in PEL.
3. **Recovery:** Another consumer (or the same one restarted) claims the pending message via XAUTOCLAIM.
4. **Completion:** The new consumer processes and acknowledges the message.

This pattern ensures **no messages are lost** due to consumer failures, which is critical for financial applications like the banking transfer system in this exercise.
