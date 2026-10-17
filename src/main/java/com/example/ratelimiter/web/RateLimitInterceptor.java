package com.example.ratelimiter.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.example.ratelimiter.config.RateLimitProperties;
import com.example.ratelimiter.ratelimit.ClientIdentity;
import com.example.ratelimiter.ratelimit.ClientResolver;
import com.example.ratelimiter.ratelimit.RateLimitResult;
import com.example.ratelimiter.ratelimit.RedisTokenBucketLimiter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Gateway-style check that runs before every controller: identify the client,
 * consume a token, and either let the request through or answer 429.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitProperties props;
    private final ClientResolver resolver;
    private final RedisTokenBucketLimiter limiter;

    public RateLimitInterceptor(RateLimitProperties props,
                                ClientResolver resolver,
                                RedisTokenBucketLimiter limiter) {
        this.props = props;
        this.resolver = resolver;
        this.limiter = limiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!props.enabled()) {
            return true;
        }

        ClientIdentity client = resolver.resolve(request);
        RateLimitResult result = limiter.tryAcquire(client.key(), client.policy());

        response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));

        if (result.allowed()) {
            return true;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"error\":\"rate_limit_exceeded\","
                        + "\"message\":\"Too many requests\","
                        + "\"retryAfterSeconds\":" + result.retryAfterSeconds() + "}");
        return false;
    }
}
