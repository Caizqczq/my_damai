if redis.call('EXISTS', KEYS[1]) == 1 then
    return 0
end

redis.call('INCRBY', KEYS[2], ARGV[1])
redis.call('SET', KEYS[1], '1')
return 1
