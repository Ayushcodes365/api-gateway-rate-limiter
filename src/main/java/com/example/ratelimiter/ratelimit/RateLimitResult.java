package com.example.ratelimiter.ratelimit;

/**
 * Outcome of a rate limit check.
 *
 * @param allowed           whether the request may proceed
 * @param limit             bucket capacity for this client
 * @param remaining         tokens left after this request
 * @param retryAfterSeconds when blocked, how long until a token is available
 */
public record RateLimitResult(boolean allowed, long limit, long remaining, long retryAfterSeconds) {

    public static RateLimitResult allowedResult(long limit, long remaining) {
        return new RateLimitResult(true, limit, remaining, 0);
    }

    public static RateLimitResult blockedResult(long limit, long retryAfterSeconds) {
        return new RateLimitResult(false, limit, 0, Math.max(1, retryAfterSeconds));
    }
}
