-- KEYS[1]: program:stock:{programId}:{categoryId}  (库存)
-- KEYS[2]: seat:avail:{programId}:{categoryId}     (可用座位)
-- KEYS[3]: seat:locked:{programId}:{categoryId}    (已锁定座位)
-- ARGV[1]: quantity  购买数量
-- ARGV[2]: userId    用户ID
-- ARGV[3]: timestamp 当前时间戳(毫秒)
--
-- 返回值:
--   JSON {code:0, seatIds:[...]}   成功
--   JSON {code:-1}                 库存不足
--   JSON {code:-2}                 可用座位不足

local stockKey = KEYS[1]
local availKey  = KEYS[2]
local lockedKey = KEYS[3]

local quantity  = tonumber(ARGV[1])
local userId    = ARGV[2]
local timestamp = ARGV[3]

-- Step 1: 检查库存
local stock = tonumber(redis.call('GET', stockKey) or '0')
if stock < quantity then
    return '{"code":-1}'
end

-- Step 2: 取出 N 个可用座位（按 Score 升序，即按 area/row/col 排序）
local seats = redis.call('ZRANGE', availKey, 0, quantity - 1, 'WITHSCORES')
if #seats < quantity * 2 then
    return '{"code":-2}'
end

-- Step 3: 原子执行——扣库存 + 锁座位
redis.call('DECRBY', stockKey, quantity)

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


