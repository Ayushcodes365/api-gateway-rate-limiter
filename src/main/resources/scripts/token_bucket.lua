-- Token bucket rate limiter.
--
-- KEYS[1]  bucket key
-- ARGV[1]  capacity          (max tokens, i.e. max burst)
-- ARGV[2]  refill rate       (tokens per second)
-- ARGV[3]  requested tokens  (normally 1)
--
-- Returns: { allowed (1/0), tokens remaining (floored), retry-after in ms }
--
-- The whole read-refill-consume-write sequence runs atomically inside Redis,
-- so concurrent requests (even from many app instances) cannot race.
-- Time comes from Redis itself, so app servers with skewed clocks agree.

local key       = KEYS[1]
local capacity  = tonumber(ARGV[1])
local rate      = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])

local t = redis.call('TIME')
local now_ms = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)

local state  = redis.call('HMGET', key, 'tokens', 'ts')
local tokens = tonumber(state[1])
local ts     = tonumber(state[2])

if tokens == nil or ts == nil then
  -- first request from this client: start with a full bucket
  tokens = capacity
  ts = now_ms
end

-- refill based on elapsed time, never above capacity
local elapsed_ms = math.max(0, now_ms - ts)
tokens = math.min(capacity, tokens + (elapsed_ms / 1000.0) * rate)

local allowed = 0
local retry_after_ms = 0

if tokens >= requested then
  tokens = tokens - requested
  allowed = 1
else
  retry_after_ms = math.ceil(((requested - tokens) / rate) * 1000)
end

redis.call('HSET', key, 'tokens', tostring(tokens), 'ts', tostring(now_ms))

-- once the bucket would be full again, the stored state is equivalent to no
-- state, so the key can expire (keeps Redis memory bounded)
local ttl = math.max(1, math.ceil(capacity / rate) * 2)
redis.call('EXPIRE', key, ttl)

return { allowed, math.floor(tokens), retry_after_ms }
