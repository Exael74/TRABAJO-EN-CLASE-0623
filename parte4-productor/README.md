# Part 4 — Event Producer / Simulator (Java + Jedis)

**Author:** Stiven Esneider Pardo Gutierrez  
**Course:** Software Architecture  
**Date:** June 23, 2026

---

## Objective

Build an autonomous event producer that generates random `TransferenciaCreada` events and publishes them to the `banco.transferencias` Redis stream using `XADD`. This allows the entire pipeline (producer → stream → consumer) to run without manual CLI intervention.

---

## Architecture

```
┌────────────────────────┐     XADD      ┌──────────────────────────────┐
│  SimuladorTransferencias│──────────────▶│     Redis Stream             │
│  (generates events      │               │     banco.transferencias     │
│   every 5 seconds)      │               │                              │
└────────────────────────┘               └──────────┬───────────────────┘
                                                     │
                                           XREADGROUP│
                                                     ▼
                                           ┌──────────────────┐
                                           │ AuditoriaConsumer│
                                           │ (auditoria-group)│
                                           └──────────────────┘
```

## Event Generation Logic

The simulator creates a `TransferenciaCreada` event every 5 seconds with:

| Field | Generation Strategy |
|-------|-------------------|
| `eventId` | `evt-` + 8-character UUID fragment |
| `transferId` | `tr-` + 6-character UUID fragment |
| `from` | Random account from `cta-101` through `cta-110` |
| `to` | Random account (guaranteed different from `from`) |
| `amount` | Random double between 100.00 and 10,000.00, rounded to 2 decimals |
| `currency` | Random selection from `COP`, `USD`, `EUR` |
| `createdAt` | Current UTC timestamp in ISO 8601 format |

Example generated event:
```json
{
  "eventId": "evt-a3f8c21e",
  "transferId": "tr-b7d4a9",
  "from": "cta-105",
  "to": "cta-102",
  "amount": 3750.50,
  "currency": "COP",
  "createdAt": "2026-06-23T15:30:00Z"
}
```

## Requirements

- Java 17+
- Docker with Redis container running on `localhost:6379`
- Maven 3.8+

## Compilation

```bash
cd parte4-productor
mvn clean package -DskipTests
```

Generates `target/simulador-transferencias-1.0.0.jar`.

## Execution

```bash
java -jar target/simulador-transferencias-1.0.0.jar
```

The simulator will:
1. Connect to Redis on `localhost:6379`
2. Generate and publish a random `TransferenciaCreada` event every 5 seconds
3. Log each published event to the console
4. Reconnect automatically if Redis is temporarily unavailable

### Stop

Press `Ctrl + C` to stop.

## Example Output

```
[SIMULADOR] Starting transfer event generator...
[SIMULADOR] Connected to Redis.
[SIMULADOR] Published event: ID=1719148500000-0 | cta-105 -> cta-102 | 3750.50 COP
[SIMULADOR] Published event: ID=1719148505000-0 | cta-101 -> cta-108 | 1200.00 USD
[SIMULADOR] Published event: ID=1719148510000-0 | cta-110 -> cta-103 | 8900.75 EUR
```

## Full Demo Sequence

1. `parte1-infra/setup-redis.sh` — Start Redis + create consumer groups
2. `parte4-productor` — Run simulator (publishes events every 5s)
3. `parte2-consumidor` — Run audit consumer (reads and persists events)
4. `parte3-simulacion/simular-caida.sh` — Simulate consumer crash and recovery
