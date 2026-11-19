-- 预留库存（候补补位 / 改签目标场次）
-- KEYS[1] stock
-- ARGV[1] quantity
-- 返回：1成功 0库存不足 -1 key不存在
local stock = tonumber(redis.call('get', KEYS[1]))
if stock == nil then
    return -1
end
local quantity = tonumber(ARGV[1])
if stock < quantity then
    return 0
end
redis.call('decrby', KEYS[1], quantity)
return 1
