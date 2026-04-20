-- Re-enqueues players if match creation transaction fails
-- KEYS[1] = queue sorted set: gamematch:queue:{region}:{mode}
-- KEYS[2] = player registry hash: gamematch:queue:player_info
-- KEYS[3] = active players set: gamematch:queue:active_players
-- ARGV pairs: ARGV[1]=playerId, ARGV[2]=skillRating, ARGV[3]=playerInfoJson, ARGV[4]=playerId2, ...

local i = 1
while i <= #ARGV do
    local playerId = ARGV[i]
    local rating = tonumber(ARGV[i+1])
    local infoJson = ARGV[i+2]

    if redis.call('SADD', KEYS[3], playerId) == 1 then
        redis.call('HSET', KEYS[2], playerId, infoJson)
        redis.call('ZADD', KEYS[1], rating, playerId)
    end
    i = i + 3
end
return 1
