--
-- Created by IntelliJ IDEA.
-- User: crossoverJie
-- Date: 22/04/2018
-- Time: 15:36
-- To change this template use File | Settings | File Templates.
--
--lua 下标从 1 开始
-- 限流 key
local key = KEYS[1]
-- 限流大小
local limit = tonumber(ARGV[1])

-- 获取当前流量大小
local currentLimit = tonumber(redis.call('get', key) or "0")

if currentLimit + 1 > limit then
    -- 达到限流大小 返回
    return 0;
else
    -- 没有达到阈值 value + 1
    redis.call("INCRBY", key, 1)
    redis.call("EXPIRE", key, 2)
    return currentLimit + 1
end

