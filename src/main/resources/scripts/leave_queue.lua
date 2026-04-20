-- Atomically removes a player from the matchmaking queue
-- KEYS[1] = queue sorted set: gamematch:queue:{region}:{mode}
-- KEYS[2] = player registry hash: gamematch:queue:player_info
-- KEYS[3] = active players set: gamematch:queue:active_players
-- ARGV[1] = playerId
-- Returns 1 if removed, 0 if player was not in queue

if redis.call('SISMEMBER', KEYS[3], ARGV[1]) == 0 then
    return 0
end

redis.call('SREM', KEYS[3], ARGV[1])
redis.call('HDEL', KEYS[2], ARGV[1])
if KEYS[1] and KEYS[1] ~= '' then
    redis.call('ZREM', KEYS[1], ARGV[1])
end
return 1
