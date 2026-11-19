-- 回补脚本：取消 / 超时 / 退款时调用，必须幂等由调用方状态机保证
-- KEYS[1] 库存 string
-- KEYS[2] 用户已购 hash
-- KEYS[3] 证件集合 set
-- KEYS[4] 场次元信息 hash（含 capacity）
-- ARGV[1] userId
-- ARGV[2] quantity
-- ARGV[3..] attendeeId 列表
-- 返回：1 已处理 0 库存 key 不存在

local stockKey = KEYS[1]
local boughtKey = KEYS[2]
local attendeeKey = KEYS[3]
local metaKey = KEYS[4]

local userId = ARGV[1]
local quantity = tonumber(ARGV[2])

local exists = redis.call('exists', stockKey)
if exists == 1 then
    local capacity = tonumber(redis.call('hget', metaKey, 'capacity'))
    local current = tonumber(redis.call('get', stockKey))
    if capacity ~= nil and current ~= nil and current + quantity > capacity then
        redis.call('set', stockKey, capacity)
    else
        redis.call('incrby', stockKey, quantity)
    end
end

local left = redis.call('hincrby', boughtKey, userId, -quantity)
if left <= 0 then
    redis.call('hdel', boughtKey, userId)
end

for i = 3, #ARGV do
    redis.call('srem', attendeeKey, ARGV[i])
end

if exists == 1 then
    return 1
end
return 0
