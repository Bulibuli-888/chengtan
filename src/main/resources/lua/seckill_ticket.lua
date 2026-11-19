-- 抢票原子脚本
-- KEYS[1] 库存 string           ticket:stock:{sessionId}
-- KEYS[2] 用户已购 hash         ticket:bought:{sessionId}
-- KEYS[3] 证件集合 set          ticket:attendee:{sessionId}
-- KEYS[4] 场次元信息 hash       ticket:session:{sessionId}
-- ARGV[1] userId
-- ARGV[2] quantity
-- ARGV[3] now(epoch millis)
-- ARGV[4] limit（每人限购）
-- ARGV[5..] attendeeId 列表
-- 返回：0成功 1库存不足 2超限购 3未开售 4未开始 5已结束 6未初始化 7证件重复

local stockKey = KEYS[1]
local boughtKey = KEYS[2]
local attendeeKey = KEYS[3]
local metaKey = KEYS[4]

local userId = ARGV[1]
local quantity = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local limit = tonumber(ARGV[4])

local status = tonumber(redis.call('hget', metaKey, 'status'))
if status == nil then
    return 6
end
if status ~= 1 then
    return 3
end

local saleStart = tonumber(redis.call('hget', metaKey, 'saleStart'))
local saleEnd = tonumber(redis.call('hget', metaKey, 'saleEnd'))
if saleStart ~= nil and now < saleStart then
    return 4
end
if saleEnd ~= nil and now > saleEnd then
    return 5
end

local stock = tonumber(redis.call('get', stockKey))
if stock == nil then
    return 6
end
if stock < quantity then
    return 1
end

local bought = tonumber(redis.call('hget', boughtKey, userId)) or 0
if bought + quantity > limit then
    return 2
end

local attendeeIds = {}
for i = 5, #ARGV do
    local aid = ARGV[i]
    if redis.call('sismember', attendeeKey, aid) == 1 then
        return 7
    end
    attendeeIds[#attendeeIds + 1] = aid
end

redis.call('decrby', stockKey, quantity)
redis.call('hincrby', boughtKey, userId, quantity)
for i = 1, #attendeeIds do
    redis.call('sadd', attendeeKey, attendeeIds[i])
end
return 0
