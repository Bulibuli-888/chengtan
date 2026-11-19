-- 回补库存并封顶到 capacity
-- KEYS[1] stock
-- KEYS[2] meta（含 capacity）
-- ARGV[1] quantity
-- 返回：回补后的库存，key 不存在返回 0
local current = tonumber(redis.call('get', KEYS[1]))
if current == nil then
    return 0
end
local capacity = tonumber(redis.call('hget', KEYS[2], 'capacity'))
local target = current + tonumber(ARGV[1])
if capacity ~= nil and target > capacity then
    target = capacity
end
redis.call('set', KEYS[1], target)
return target
