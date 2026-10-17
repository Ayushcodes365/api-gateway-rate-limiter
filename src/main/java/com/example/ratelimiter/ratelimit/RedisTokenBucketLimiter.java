package com.example.ratelimiter.ratelimit;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.example.ratelimiter.config.RateLimitProperties;
import com.example.ratelimiter.config.RateLimitProperties.Policy;

/**
 * Distributed token bucket limiter. All state lives in Redis and is updated by
 * a single Lua script, so every app instance shares the same counters.
 */
@Component
public class RedisTokenBucketLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenBucketLimiter.class);

    private final StringRedisTemplate redis;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> script;
    private final RateLimitProperties props;

    @SuppressWarnings("rawtypes")
    public RedisTokenBucketLimiter(StringRedisTemplate redis,
                                   RedisScript<List> script,
                                   RateLimitProperties props) {
        this.redis = redis;
        this.script = script;
        this.props = props;
    }

    public RateLimitResult tryAcquire(String clientKey, Policy policy) {
        String redisKey = props.keyPrefix() + clientKey;
        try {
            List<?> result = redis.execute(
                    script,
                    List.of(redisKey),
                    String.valueOf(policy.capacity()),
                    String.valueOf(policy.refillPerSecond()),
                    "1");

            if (result == null || result.size() < 3) {
                throw new IllegalStateException("Unexpected script result: " + result);
            }

            boolean allowed = ((Number) result.get(0)).longValue() == 1L;
            long remaining = ((Number) result.get(1)).longValue();
            long retryAfterMs = ((Number) result.get(2)).longValue();

            return allowed
                    ? RateLimitResult.allowedResult(policy.capacity(), remaining)
                    : RateLimitResult.blockedResult(policy.capacity(), (long) Math.ceil(retryAfterMs / 1000.0));

        } catch (DataAccessException | IllegalStateException e) {
            log.warn("Rate limit check failed for {} ({}); failOpen={}", redisKey, e.getMessage(), props.failOpen());
            return props.failOpen()
                    ? RateLimitResult.allowedResult(policy.capacity(), policy.capacity())
                    : RateLimitResult.blockedResult(policy.capacity(), 1);
        }
    }
}
