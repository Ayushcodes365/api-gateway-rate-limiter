package com.example.ratelimiter.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.ratelimiter.config.RateLimitProperties;
import com.example.ratelimiter.config.RateLimitProperties.Policy;
import com.example.ratelimiter.ratelimit.ClientResolver;
import com.example.ratelimiter.ratelimit.RateLimitResult;
import com.example.ratelimiter.ratelimit.RedisTokenBucketLimiter;

class RateLimitInterceptorTest {

    private final Policy policy = new Policy(5, 1);
    private RedisTokenBucketLimiter limiter;

    private MockMvc mvcWith(boolean enabled) {
        RateLimitProperties props = new RateLimitProperties(
                enabled, true, "rl:", policy, Map.of(), Map.of());
        limiter = mock(RedisTokenBucketLimiter.class);
        RateLimitInterceptor interceptor =
                new RateLimitInterceptor(props, new ClientResolver(props), limiter);
        return MockMvcBuilders.standaloneSetup(new DemoController())
                .addInterceptors(interceptor)
                .build();
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = mvcWith(true);
    }

    @Test
    void allowedRequestPassesThroughWithRateLimitHeaders() throws Exception {
        when(limiter.tryAcquire(any(), eq(policy))).thenReturn(RateLimitResult.allowedResult(5, 4));

        mvc.perform(get("/api/hello"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "5"))
                .andExpect(header().string("X-RateLimit-Remaining", "4"));
    }

    @Test
    void blockedRequestGets429WithRetryAfter() throws Exception {
        when(limiter.tryAcquire(any(), eq(policy))).thenReturn(RateLimitResult.blockedResult(5, 3));

        mvc.perform(get("/api/hello"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(jsonPath("$.error").value("rate_limit_exceeded"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(3));
    }

    @Test
    void disabledLimiterIsNeverConsulted() throws Exception {
        mvc = mvcWith(false);

        mvc.perform(get("/api/hello")).andExpect(status().isOk());

        verifyNoInteractions(limiter);
    }
}
