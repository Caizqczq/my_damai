-- KEYS[1]: seat:avail:{programId}:{categoryId}
-- KEYS[2]: seat:locked:{programId}:{categoryId}
-- ARGV[1..N]: seatId 列表

local availKey  = KEYS[1]
local lockedKey = KEYS[2]
local released  = 0

for i = 1, #ARGV do
    local seatId   = ARGV[i]
    local lockInfo = redis.call('HGET', lockedKey, seatId)
    if lockInfo then
        -- 从 lockInfo 提取原始 score: "userId:timestamp:score"
        local score = string.match(lockInfo, ':([^:]+)$')
        -- 从锁定哈希移除
        redis.call('HDEL', lockedKey, seatId)
        -- 放回可用集合
        redis.call('ZADD', availKey, tonumber(score), seatId)
        released = released + 1
    end
end

return tostring(released)
