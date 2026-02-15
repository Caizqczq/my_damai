-- KEYS[1]: program:stock:{programId}:{categoryId}
-- KEYS[2]: seat:avail:{programId}:{categoryId}
-- KEYS[3]: seat:locked:{programId}:{categoryId}
-- ARGV[1..N]: seatId 列表

local stockKey  = KEYS[1]
local availKey  = KEYS[2]
local lockedKey = KEYS[3]
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

-- 回补库存
if released > 0 then
    redis.call('INCRBY', stockKey, released)
end

return tostring(released)