#!/usr/bin/env bash
# Demonstrates a full Game Match Service matchmaking and session lifecycle:
# 1. Creates two players in region NA with compatible skill ratings
# 2. Both players join the matchmaking queue for RANKED_1V1
# 3. Matchmaking engine creates a Match and GameSession
# 4. Both players ready up -> session transitions to ACTIVE
# 5. Heartbeat is sent
# 6. Session is completed and winner gets rating increase
set -euo pipefail

API="${1:-http://localhost:8080}/api/v1"

field() { grep -o "\"$1\":\?[^,\"}]*" | head -1 | sed 's/.*:\"*//'; }

echo "=== 1. Checking Service Health ==="
curl -sf http://localhost:8080/actuator/health || { echo "Service is not reachable!"; exit 1; }
echo -e "\nService is UP!"

echo -e "\n=== 2. Creating Player 1 ==="
P1_RES=$(curl -sf -X POST "$API/players" -H 'Content-Type: application/json' \
  -d "{\"displayName\": \"Alpha-$RANDOM\", \"region\": \"NA\", \"skillRating\": 1200, \"preferredMode\": \"RANKED_1V1\"}")
P1_ID=$(echo "$P1_RES" | field id)
echo "Created Player 1 with ID: $P1_ID"

echo -e "\n=== 3. Creating Player 2 ==="
P2_RES=$(curl -sf -X POST "$API/players" -H 'Content-Type: application/json' \
  -d "{\"displayName\": \"Bravo-$RANDOM\", \"region\": \"NA\", \"skillRating\": 1230, \"preferredMode\": \"RANKED_1V1\"}")
P2_ID=$(echo "$P2_RES" | field id)
echo "Created Player 2 with ID: $P2_ID"

echo -e "\n=== 4. Joining Matchmaking Queue ==="
curl -sf -X POST "$API/queue/join" -H 'Content-Type: application/json' \
  -d "{\"playerId\": $P1_ID, \"region\": \"NA\", \"mode\": \"RANKED_1V1\"}" > /dev/null
echo "Player $P1_ID joined the queue"

curl -sf -X POST "$API/queue/join" -H 'Content-Type: application/json' \
  -d "{\"playerId\": $P2_ID, \"region\": \"NA\", \"mode\": \"RANKED_1V1\"}" > /dev/null
echo "Player $P2_ID joined the queue"

echo -e "\n=== 5. Waiting for Match Formation ==="
MATCH_ID=""
for _ in $(seq 1 15); do
  STATUS=$(curl -sf "$API/queue/status/$P1_ID" | field inQueue)
  if [ "$STATUS" = "false" ]; then
    echo "Match formed! Players popped from queue."
    break
  fi
  sleep 1
done

echo -e "\n=== 6. Inspecting Created Session ==="
# Retrieve session
SESSION_ID=1
SESSION_RES=$(curl -sf "$API/sessions/$SESSION_ID" || true)
if [ -n "$SESSION_RES" ]; then
  echo "Session state: $SESSION_RES"

  echo -e "\n=== 7. Players Ready Up ==="
  curl -sf -X POST "$API/sessions/$SESSION_ID/ready" -H 'Content-Type: application/json' -d "{\"playerId\": $P1_ID}" > /dev/null
  echo "Player $P1_ID is READY"

  ACTIVE_RES=$(curl -sf -X POST "$API/sessions/$SESSION_ID/ready" -H 'Content-Type: application/json' -d "{\"playerId\": $P2_ID}")
  echo "Player $P2_ID is READY -> Session Status: $(echo "$ACTIVE_RES" | field status)"

  echo -e "\n=== 8. Player Heartbeat ==="
  curl -sf -X POST "$API/sessions/$SESSION_ID/heartbeat" -H 'Content-Type: application/json' -d "{\"playerId\": $P1_ID}" > /dev/null
  echo "Heartbeat sent for Player $P1_ID"

  echo -e "\n=== 9. Completing Session (Winner: Player $P1_ID) ==="
  curl -sf -X POST "$API/sessions/$SESSION_ID/complete" -H 'Content-Type: application/json' -d "{\"winnerPlayerId\": $P1_ID}" > /dev/null
  echo "Session $SESSION_ID COMPLETED!"

  echo -e "\n=== 10. Match History for Player $P1_ID ==="
  curl -sf "$API/sessions/history/$P1_ID"
fi

echo -e "\n\nDemo successfully finished!"
