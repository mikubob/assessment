-- 仅在令牌匹配时延长租约；过期后的持有者不能恢复所有权。
if redis.call('GET', KEYS[1]) == ARGV[1] then
    return redis.call('EXPIRE', KEYS[1], ARGV[2])
end
return 0
