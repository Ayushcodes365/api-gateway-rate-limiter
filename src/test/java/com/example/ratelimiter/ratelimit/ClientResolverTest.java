package com.example.ratelimiter.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.example.ratelimiter.config.RateLimitProperties;
import com.example.ratelimiter.config.RateLimitProperties.Policy;

class ClientResolverTest {

    private final Policy defaultPolicy = new Policy(10, 2);
    private final Policy premiumPolicy = new Policy(100, 50);
    private ClientResolver resolver;

    @BeforeEach
    void setUp() {
        RateLimitProperties props = new RateLimitProperties(
                true, true, "rl:", defaultPolicy,
                Map.of("premium", premiumPolicy),
                Map.of("premium-key", "premium"));
        resolver = new ClientResolver(props);
    }

    @Test
    void knownApiKeyGetsItsTierPolicy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "premium-key");

        ClientIdentity identity = resolver.resolve(request);

        assertThat(identity.key()).isEqualTo("key:premium-key");
        assertThat(identity.policy()).isEqualTo(premiumPolicy);
    }

    @Test
    void unknownApiKeyFallsBackToIpAndDefaultPolicy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("X-API-Key", "made-up-key");

        ClientIdentity identity = resolver.resolve(request);

        assertThat(identity.key()).isEqualTo("ip:203.0.113.7");
        assertThat(identity.policy()).isEqualTo(defaultPolicy);
    }

    @Test
    void anonymousClientIsLimitedByIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.4");

        ClientIdentity identity = resolver.resolve(request);

        assertThat(identity.key()).isEqualTo("ip:198.51.100.4");
        assertThat(identity.policy()).isEqualTo(defaultPolicy);
    }
}
