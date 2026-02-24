-- KEYS[1]: 单个桶 key
-- ARGV[1]: quantity
-- 返回: 剩余库存 (>=0 成功) 或 "-1" (不足)
local stock = tonumber(redis.call('GET', KEYS[1]) or '0')
if stock < tonumber(ARGV[1]) then return '-1' end
return tostring(redis.call('DECRBY', KEYS[1], ARGV[1]))
