# Event-Driven Architecture with Redis Streams — Banking Audit System

**Author:** Stiven Esneider Pardo Gutierrez  
**Course:** Software Architecture  
**Date:** June 23, 2026

---

## Project Overview

This project demonstrates an **Event-Driven Architecture** using **Redis Streams** as the message backbone. The system simulates a banking transfer platform where a **producer** publishes `TransferenciaCreada` events to a Redis stream called `banco.transferencias`. Three consumer groups consume these events for different purposes:

- `fraude-group` — Fraud detection
- `notif-group` — Notifications
- `auditoria-group` — **Audit trail** (the one implemented in this project)

The project is divided into four parts:

| Part | Description |
|------|-------------|
| **Part 1 — Infrastructure** | Docker setup, Redis container management, and consumer group creation |
| **Part 2 — Audit Consumer** | Java application using Jedis to consume, parse, persist, and acknowledge events |
| **Part 3 — Failure Simulation** | Redis CLI commands to simulate a consumer crash, pending message recovery via PEL, and XCLAIM/XAUTOCLAIM |
| **Part 4 — Event Producer** | Java application that generates random transfer events and publishes them to the stream via `XADD` |

---

## Project Structure

```
TRABAJO EN CLASE 0623/
├── .gitignore
├── README.md                         ← This file (global documentation & checklist)
├── parte1-infra/
│   ├── README.md                     ← Infrastructure documentation (Docker + Redis CLI)
│   └── setup-redis.sh                ← Automated setup script
├── parte2-consumidor/
│   ├── README.md                     ← Audit consumer documentation (Java + Jedis)
│   ├── pom.xml                       ← Maven project file
│   └── src/main/java/com/banco/auditoria/
│       ├── AuditoriaConsumer.java    ← Main consumer class (XREADGROUP loop)
│       └── TransferenciaCreada.java  ← Event POJO
├── parte3-simulacion/
│   ├── README.md                     ← Crash simulation documentation
│   └── simular-caida.sh              ← Automated simulation script
└── parte4-productor/
    ├── README.md                     ← Event producer documentation (Java + Jedis)
    ├── pom.xml                       ← Maven project file
    └── src/main/java/com/banco/simulador/
        └── SimuladorTransferencias.java ← Event generator (XADD loop)
```

---

## Development Process

### Design Decisions

1. **Redis Streams over traditional message queues:** Redis Streams provide persistent, ordered message delivery with consumer groups, pending entry lists (PEL), and message claiming — ideal for teaching event-driven architectures and at-least-once delivery semantics.

2. **Jedis over Lettuce:** Jedis was chosen for its simpler, synchronous API that is easier to understand in an educational context. Its blocking `XREADGROUP` with `BLOCK 0` provides a clean infinite polling loop.

3. **File-based persistence over H2 database:** The audit log is written to a local `auditoria.log` file. This avoids external database dependencies, keeps the setup self-contained, and the append-only format naturally fits audit use cases. Each log entry includes both a human-readable summary and the raw JSON payload.

4. **Docker for Redis isolation:** Running Redis in a Docker container ensures environment consistency and matches the assignment's requirement for the `redis:7` image.

### Key Redis Streams Concepts Applied

- **Consumer Groups:** Enable multiple consumers to cooperatively process the same stream.
- **XREADGROUP:** Reads messages assigned to a specific consumer within a group.
- **XACK:** Acknowledges successful processing, removing the message from the PEL.
- **PEL (Pending Entries List):** Tracks messages delivered but not yet acknowledged.
- **XAUTOCLAIM / XCLAIM:** Allow a different consumer to reclaim messages left unacknowledged by a failed consumer.

---

## Quick Start Checklist

### 1. Infrastructure
- [ ] `docker run -d --name redis-auditoria -p 6379:6379 redis:7`
- [ ] `docker exec redis-auditoria redis-cli XGROUP CREATE banco.transferencias auditoria-group $ MKSTREAM`
- [ ] `docker exec redis-auditoria redis-cli XINFO GROUPS banco.transferencias`

### 2. Event Producer (run in terminal 1)
- [ ] `cd parte4-productor`
- [ ] `mvn clean package -DskipTests`
- [ ] `java -jar target/simulador-transferencias-1.0.0.jar`

### 3. Audit Consumer (run in terminal 2)
- [ ] `cd parte2-consumidor`
- [ ] `mvn clean package -DskipTests`
- [ ] `java -jar target/auditoria-consumidor-1.0.0.jar`

### 4. Failure Simulation (run in terminal 3 after some events)
- [ ] `cd parte3-simulacion`
- [ ] `./simular-caida.sh`
