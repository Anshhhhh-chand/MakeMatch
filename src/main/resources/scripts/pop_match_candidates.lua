-- Atomically verifies and pops a matched group of candidates from the queue
-- KEYS[1] = queue sorted set: gamematch:queue:{region}:{mode}
-- KEYS[2] = player registry hash: gamematch:queue:player_info
-- KEYS[3] = active players set: gamematch:queue:active_players
-- ARGV = candidate player IDs (ARGV[1], ARGV[2], ...)
-- Returns 1 if all candidates were active and popped, 0 otherwise

local count = #ARGV
if count == 0 then
    return 0
end

-- Step 1: Verify all candidates are actively in the queue
for i = 1, count do
    if redis.call('SISMEMBER', KEYS[3], ARGV[i]) == 0 then
        return 0
    end
end

-- Step 2: Pop all candidates atomically
for i = 1, count do
    redis.call('SREM', KEYS[3], ARGV[i])
    redis.call('HDEL', KEYS[2], ARGV[i])
    redis.call('ZREM', KEYS[1], ARGV[i])
end

return 1
