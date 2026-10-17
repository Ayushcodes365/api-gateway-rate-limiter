package com.example.ratelimiter.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Rate limiter settings, bound from the {@code ratelimit.*} properties.
 *
 * @param enabled       master switch for the interceptor
 * @param failOpen      allow traffic when Redis is unreachable (false = reject with 429)
 * @param keyPrefix     prefix for every Redis key the limiter creates
 * @param defaultPolicy policy for anonymous clients (limited by IP address)
 * @param policies      named policies, e.g. free / premium
 * @param apiKeys       API key -> policy name
 */
@ConfigurationProperties(prefix = "ratelimit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("true") boolean failOpen,
        @DefaultValue("rl:") String keyPrefix,
        @DefaultValue Policy defaultPolicy,
        Map<String, Policy> policies,
        Map<String, String> apiKeys) {

    public RateLimitProperties {
        policies = policies == null ? Map.of() : policies;
        apiKeys = apiKeys == null ? Map.of() : apiKeys;
    }

    /**
     * Token bucket parameters.
     *
     * @param capacity        maximum tokens in the bucket (also the maximum burst size)
     * @param refillPerSecond tokens added back per second (the sustained request rate)
     */
    public record Policy(
            @DefaultValue("10") long capacity,
            @DefaultValue("5") double refillPerSecond) {

        public Policy {
            if (capacity < 1) {
                throw new IllegalArgumentException("capacity must be >= 1");
            }
            if (refillPerSecond <= 0) {
                throw new IllegalArgumentException("refill-per-second must be > 0");
            }
        }
    }
}
