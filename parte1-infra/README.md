# Part 1 — Infrastructure: Redis with Docker & Consumer Group Setup

**Author:** Stiven Esneider Pardo Gutierrez  
**Course:** Software Architecture  
**Date:** June 23, 2026

---

## Objective

Set up a Redis 7 instance using Docker, verify it is running, create a third consumer group (`auditoria-group`) on the existing `banco.transferencias` stream, and confirm that all three consumer groups exist.

---

## Prerequisites

- Docker installed and running
- Redis CLI (comes with the Docker image, accessed via `docker exec`)

---

## Step-by-Step Manual Instructions

### 1. Verify / Start Redis Container

```bash
# Check if the container is already running
docker ps -f name=redis-auditoria

# If the container does not exist, create and start it
docker run -d --name redis-auditoria -p 6379:6379 redis:7

# If the container exists but is stopped, start it
docker start redis-auditoria
```

**Explanation:** The `redis:7` image provides a lightweight Redis server. Port `6379` is the default Redis port, mapped to the host so that the Java consumer can connect via `localhost:6379`.

### 2. Create the Third Consumer Group

```bash
# Option A: From $ (new events only — recommended for production)
docker exec redis-auditoria redis-cli XGROUP CREATE banco.transferencias auditoria-group $ MKSTREAM

# Option B: From 0 (all historical events — useful for testing/recovery)
docker exec redis-auditoria redis-cli XGROUP CREATE banco.transferencias auditoria-group 0 MKSTREAM
```

**Explanation:**
- `XGROUP CREATE` creates a consumer group on the stream.
- `$` means the group starts reading only new messages arriving after creation.
- `0` means the group starts reading from the very first message (entire history).
- `MKSTREAM` automatically creates the stream if it does not exist (avoids errors if the stream hasn't been initialized yet).

### 3. Confirm All Three Consumer Groups

```bash
docker exec redis-auditoria redis-cli XINFO GROUPS banco.transferencias
```

**Expected output:** Three groups should appear:
- `fraude-group`
- `notif-group`
- `auditoria-group`

Each group entry includes:
- `name`: group name
- `consumers`: number of registered consumers
- `pending`: number of unacknowledged messages
- `last-delivered-id`: last delivered message ID

---

## Automated Script

An automated Bash script is provided for convenience.

```bash
chmod +x setup-redis.sh
./setup-redis.sh
```

The script will:
1. Check if the `redis-auditoria` container exists and is running; if not, it creates or starts it.
2. Prompt you to choose between reading from `$` (new events) or `0` (history).
3. Create the `auditoria-group` consumer group accordingly.
4. Display the `XINFO GROUPS` output to confirm all three groups.

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| `port is already allocated` | Stop the conflicting container or change the host port mapping |
| `NOGROUP No such consumer group` | The stream might not exist yet. Use `MKSTREAM` flag as shown above |
| Connection refused from Java app | Ensure the container is running and port 6379 is accessible |
