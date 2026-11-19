-- 候补转订单失败时回滚限购计数和证件集合
-- KEYS[1] bought hash
-- KEYS[2] attendee set
-- ARGV[1] userId
-- ARGV[2] quantity
-- ARGV[3..] attendeeId
local left = redis.call('hincrby', KEYS[1], ARGV[1], -tonumber(ARGV[2]))
if left <= 0 then
    redis.call('hdel', KEYS[1], ARGV[1])
end
for i = 3, #ARGV do
    redis.call('srem', KEYS[2], ARGV[i])
end
return 1
