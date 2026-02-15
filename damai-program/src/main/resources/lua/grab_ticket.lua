-- KEYS[1]: 库存key  program:stock:{programId}:{categoryId}
-- ARGV[1]: 购买数量
--
-- 返回值:
--   >= 0  扣减成功，返回剩余库存
--   -1    库存不足，未做任何扣减

local stock = tonumber(redis.call('GET', KEYS[1]) or '0')
local quantity = tonumber(ARGV[1])

if stock < quantity then
    return '-1'
end

redis.call('DECRBY', KEYS[1], quantity)

return tostring(stock - quantity)
