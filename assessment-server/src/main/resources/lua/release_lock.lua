-- 只删除当前持有者的锁，避免误释放下一位持有者。
if redis.call('GET', KEYS[1]) == ARGV[1] then
    return redis.call('DEL', KEYS[1])
end
return 0
