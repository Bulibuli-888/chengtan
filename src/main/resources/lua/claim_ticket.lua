-- 候补转订单：不再扣库存（补位时已预留），只校验限购和证件
-- KEYS[1] bought hash
-- KEYS[2] attendee set
-- ARGV[1] userId
-- ARGV[2] quantity
-- ARGV[3] limit
-- ARGV[4..] attendeeId
-- 返回：0成功 2超限购 7证件重复
local boughtKey = KEYS[1]
local attendeeKey = KEYS[2]
local userId = ARGV[1]
local quantity = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])

local bought = tonumber(redis.call('hget', boughtKey, userId)) or 0
if bought + quantity > limit then
    return 2
end

for i = 4, #ARGV do
    if redis.call('sismember', attendeeKey, ARGV[i]) == 1 then
        return 7
    end
end

redis.call('hincrby', boughtKey, userId, quantity)
for i = 4, #ARGV do
    redis.call('sadd', attendeeKey, ARGV[i])
end
return 0
