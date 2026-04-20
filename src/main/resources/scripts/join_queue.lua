-- Atomically joins a player to the matchmaking queue
-- KEYS[1] = queue sorted set: gamematch:queue:{region}:{mode}
-- KEYS[2] = player registry hash: gamematch:queue:player_info
-- KEYS[3] = active players set: gamematch:queue:active_players
-- ARGV[1] = playerId
-- ARGV[2] = skillRating
-- ARGV[3] = playerInfoJson
-- Returns 1 if joined, 0 if already in queue

if redis.call('SISMEMBER', KEYS[3], ARGV[1]) == 1 then
    return 0
end

redis.call('SADD', KEYS[3], ARGV[1])
redis.call('HSET', KEYS[2], ARGV[1], ARGV[3])
redis.call('ZADD', KEYS[1], tonumber(ARGV[2]), ARGV[1])
return 1
