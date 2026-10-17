package com.example.ratelimiter.ratelimit;

import com.example.ratelimiter.config.RateLimitProperties.Policy;

/**
 * Who is calling, and which policy applies to them.
 *
 * @param key    stable identifier used in the Redis key (e.g. "ip:1.2.3.4")
 * @param policy token bucket parameters for this client
 */
public record ClientIdentity(String key, Policy policy) {
}
