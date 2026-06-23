#!/bin/bash
# ============================================================
# PART 1 — Infrastructure (Docker / Redis CLI)
# Author: Stiven Esneider Pardo Gutierrez
# ============================================================
# This script automates Part 1 of the Redis Streams exercise:
#   1. Check/start the Redis 7 Docker container
#   2. Create the 'auditoria-group' consumer group
#   3. Verify all three consumer groups exist
# ============================================================

echo "=========================================="
echo " STEP 1: Verify / Start Redis Container"
echo "=========================================="

RUNNING=$(docker ps -q -f name=redis-auditoria -f status=running)
EXISTS=$(docker ps -aq -f name=redis-auditoria)

if [ -n "$RUNNING" ]; then
    echo "[OK] Redis is already running in container 'redis-auditoria'"
elif [ -n "$EXISTS" ]; then
    echo "[INFO] Container 'redis-auditoria' exists but is stopped. Starting it..."
    docker start redis-auditoria
else
    echo "[INFO] Creating and starting Redis 7 container..."
    docker run -d --name redis-auditoria -p 6379:6379 redis:7
fi

sleep 2
echo ""

echo "=========================================="
echo " STEP 2: Create Consumer Group 'auditoria-group'"
echo "=========================================="

echo "Where should the group start reading from?"
echo "  [1] From \$ (new events only)"
echo "  [2] From 0 (full history)"
read -p "Choose 1 or 2: " OPTION

if [ "$OPTION" = "1" ]; then
    docker exec redis-auditoria redis-cli XGROUP CREATE banco.transferencias auditoria-group $ MKSTREAM
    echo "[OK] Group 'auditoria-group' created from \$ (new events only)"
elif [ "$OPTION" = "2" ]; then
    docker exec redis-auditoria redis-cli XGROUP CREATE banco.transferencias auditoria-group 0 MKSTREAM
    echo "[OK] Group 'auditoria-group' created from 0 (full history)"
else
    echo "[ERROR] Invalid option. Exiting."
    exit 1
fi

echo ""

echo "=========================================="
echo " STEP 3: Confirm All Consumer Groups"
echo "=========================================="
docker exec redis-auditoria redis-cli XINFO GROUPS banco.transferencias

echo ""
echo "=== PART 1 COMPLETE ==="
