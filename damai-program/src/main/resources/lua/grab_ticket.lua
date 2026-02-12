-- KEYS[1]: 库存key  program:stock:{programId}:{categoryId}
-- KEYS[2]: 已购集合 user:bought:{programId}
-- ARGV[1]: userId
-- ARGV[2]: 购买数量

-- 1. 检查是否重复购买
if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return '-1'
end

-- 2. 检查库存
local stock = tonumber(redis.call('GET', KEYS[1]) or '0')
local quantity = tonumber(ARGV[2])

if stock < quantity then
    return '0'
end

-- 3. 扣减库存
redis.call('DECRBY', KEYS[1], quantity)

-- 4. 记录已购买
redis.call('SADD', KEYS[2], ARGV[1])

-- 5. 返回剩余库存
return tostring(stock - quantity)
