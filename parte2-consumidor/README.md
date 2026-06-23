# Part 2 — Audit Consumer (Java + Jedis)

**Author:** Stiven Esneider Pardo Gutierrez  
**Course:** Software Architecture  
**Date:** June 23, 2026

---

## Objective

Build an independent Java application that acts as a consumer within the `auditoria-group` consumer group. It reads `TransferenciaCreada` events from the `banco.transferencias` Redis stream, parses them, persists them to an audit log file, and acknowledges them with `XACK` only after successful persistence.

---

## Architecture

```
┌──────────────┐     XADD      ┌──────────────────────────────────────┐
│              │──────────────▶│         Redis Stream                 │
│   Producer   │               │     banco.transferencias             │
│  (any app)   │               │                                      │
└──────────────┘               └──┬──────────────┬───────────────────┘
                                  │              │
                        XREADGROUP│              │XREADGROUP
                                  ▼              ▼
                          ┌──────────────┐ ┌──────────────┐
                          │fraude-group  │ │notif-group   │
                          └──────────────┘ └──────────────┘
                                  │
                        XREADGROUP│
                                  ▼
                          ┌──────────────┐
                          │auditoria-group│
                          │  auditoria-1  │
                          └──────┬───────┘
                                 │ parse + persist
                                 ▼
                          ┌──────────────┐
                          │ auditoria.log│
                          │ (JSON file)  │
                          └──────────────┘
                                 │
                                 │ XACK (only after successful persist)
                                 ▼
                          PEL entry removed
```

---

## Persistence Decision

Events are persisted to a **local log file (`auditoria.log`)** for the following reasons:

| Reason | Explanation |
|--------|-------------|
| **Simplicity** | No external database (H2, MySQL) needs to be installed or configured |
| **Append-only** | Naturally fits the audit use case — each event is a new entry, immutable |
| **Idempotent** | Duplicate events are visible in the log and easily identified |
| **Portable** | Works on any OS without additional services |
| **Auditable** | Can be inspected with standard CLI tools (`cat`, `grep`, `tail`) |

Each log entry contains:
- Timestamp
- `eventId`, `transferId`, `from`, `to`, `amount`, `currency`, `createdAt`
- The raw JSON payload for full traceability

---

## Requirements

- **Java 17+** (uses `java.time.LocalDateTime`, `java.nio.file`, and text blocks)
- **Docker** with Redis container running on `localhost:6379`
- **Maven 3.8+** (or use the Maven wrapper)

---

## Dependencies (pom.xml)

| Dependency | Version | Purpose |
|------------|---------|---------|
| `redis.clients:jedis` | 5.1.0 | Redis client for Java — provides `XREADGROUP`, `XACK`, etc. |
| `com.google.code.gson:gson` | 2.10.1 | JSON parsing for `TransferenciaCreada` events |
| `ch.qos.logback:logback-classic` | 1.4.14 | Logging framework |

The build uses `maven-shade-plugin` to create a fat JAR with all dependencies bundled.

---

## Code Structure

### `TransferenciaCreada.java`

A Plain Old Java Object (POJO) representing the transfer event with fields:
- `eventId` — Unique event identifier
- `transferId` — Transfer identifier
- `from` — Source account
- `to` — Destination account
- `amount` — Transfer amount
- `currency` — Currency code (e.g., USD, EUR)
- `createdAt` — ISO 8601 timestamp

Uses Gson's `@SerializedName` annotations for direct field mapping from JSON.

### `AuditoriaConsumer.java`

Main consumer class with the following logic:

1. **Connection & Registration:** Connects to Redis via `Jedis` and implicitly registers as consumer `auditoria-1` within `auditoria-group` on the first `XREADGROUP` call.

2. **Blocking Read Loop:** Calls `XREADGROUP` with `BLOCK 0` (infinite blocking) and `COUNT 1` (one message at a time) inside two nested infinite loops:
   - Outer loop: handles reconnection (if Redis goes down, waits 5 seconds and retries).
   - Inner loop: reads messages and processes them.

3. **Message Processing (`processMessage`):**
   - Extracts the message ID and fields from the `StreamEntry`.
   - Parses the JSON string from the `evento` field into a `TransferenciaCreada` object using Gson.
   - Calls `persistEvent` to write to the audit log.
   - If persistence succeeds, sends `XACK` to remove the message from the PEL.
   - If any exception occurs, the `XACK` is **not** sent, leaving the message in the PEL for later recovery.

4. **Persistence (`persistEvent`):**
   - Opens `auditoria.log` in append mode.
   - Writes a formatted line with key fields followed by the raw JSON.
   - Flushes and closes the file.

5. **Reconnection:** If a `JedisConnectionException` is caught, the inner loop breaks, the `Jedis` resource is closed (via try-with-resources), and the outer loop retries after 5 seconds.

---

## Compilation

```bash
cd parte2-consumidor
mvn clean package -DskipTests
```

This generates `target/auditoria-consumidor-1.0.0.jar` (fat JAR with all dependencies).

---

## Execution

```bash
java -jar target/auditoria-consumidor-1.0.0.jar
```

The consumer will:
1. Create `auditoria.log` if it does not exist.
2. Connect to Redis on `localhost:6379`.
3. Register itself in `auditoria-group` as `auditoria-1`.
4. Block indefinitely waiting for new messages.

### Stop

Press `Ctrl + C` to stop the consumer gracefully.

---

## Testing

You can publish events manually or use the automated producer.

**Option A — Automated producer (recommended):**

Run `parte4-productor` in a separate terminal to generate random events every 5 seconds:

```bash
cd parte4-productor
mvn clean package -DskipTests
java -jar target/simulador-transferencias-1.0.0.jar
```

**Option B — Manual test event:**

```bash
docker exec redis-auditoria redis-cli XADD banco.transferencias * \
  evento '{"eventId":"e1","transferId":"t1","from":"Alice","to":"Bob","amount":1500.00,"currency":"USD","createdAt":"2026-06-23T10:00:00Z"}'
```

The consumer terminal should log:
```
[2026-06-23 10:00:01] Message received: ID=1719122400001-0
[2026-06-23 10:00:01] Event persisted to auditoria.log
[2026-06-23 10:00:01] XACK sent for 1719122400001-0
```

And `auditoria.log` should contain:
```
[2026-06-23 10:00:01] eventId=e1 | transferId=t1 | from=Alice | to=Bob | amount=1500.00 USD | createdAt=2026-06-23T10:00:00Z
  JSON: {"eventId":"e1","transferId":"t1","from":"Alice","to":"Bob","amount":1500.00,"currency":"USD","createdAt":"2026-06-23T10:00:00Z"}
---
```

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| `Connection refused` | Ensure Redis container is running: `docker ps \| grep redis-auditoria` |
| `java.lang.NoClassDefFoundError` | Rebuild the fat JAR with `mvn clean package` |
| Consumer does not receive messages | Check that the consumer group exists: `XINFO GROUPS banco.transferencias` |
| Messages stay in PEL forever | Check for exceptions in consumer output; fix the parsing/persistence logic |
