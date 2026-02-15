-- KEYS[1]: seat:locked:{programId}:{categoryId}
-- KEYS[2]: seat:sold:{programId}:{categoryId}
-- ARGV[1..N]: seatId 列表

local lockedKey = KEYS[1]
local soldKey   = KEYS[2]

for i = 1, #ARGV do
    local seatId = ARGV[i]
    redis.call('HDEL', lockedKey, seatId)
    redis.call('SADD', soldKey, seatId)
end

return 'OK'