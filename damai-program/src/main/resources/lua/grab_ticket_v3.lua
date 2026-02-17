-- KEYS[1]: seat:avail:{programId}:{categoryId}     (可用座位 ZSet)
-- KEYS[2]: seat:locked:{programId}:{categoryId}    (已锁定座位 Hash)
-- ARGV[1]: quantity  购买数量
-- ARGV[2]: userId    用户ID
-- ARGV[3]: timestamp 当前时间戳(毫秒)
--
-- 返回值:
--   JSON {code:0, seatIds:[...]}   成功
--   JSON {code:-1}                 库存不足

local availKey  = KEYS[1]
local lockedKey = KEYS[2]

local quantity  = tonumber(ARGV[1])
local userId    = ARGV[2]
local timestamp = ARGV[3]

-- Step 1: 用 ZCARD 检查可用座位数（替代独立的 stockKey）
local stock = redis.call('ZCARD', availKey)
if stock < quantity then
    return '{"code":-1}'
end

-- Step 2: 取出 N 个可用座位（按 Score 升序，即按 area/row/col 排序）
local seats = redis.call('ZRANGE', availKey, 0, quantity - 1, 'WITHSCORES')
if #seats < quantity * 2 then
    return '{"code":-1}'
end

-- Step 3: 原子执行——锁座位（不再需要扣 stockKey）
local lockInfo = userId .. ':' .. timestamp
local locked = {}
for i = 1, quantity * 2, 2 do
    local seatId = seats[i]
    local score  = seats[i + 1]
    -- 从可用集合移除
    redis.call('ZREM', availKey, seatId)
    -- 写入锁定哈希（保存 score 用于释放时恢复排序）
    redis.call('HSET', lockedKey, seatId, lockInfo .. ':' .. score)
    locked[#locked + 1] = seatId
end

local result = '{"code":0,"seatIds":['
for i, id in ipairs(locked) do
    if i > 1 then result = result .. ',' end
    result = result .. '"' .. id .. '"'
end
result = result .. ']}'

return result
