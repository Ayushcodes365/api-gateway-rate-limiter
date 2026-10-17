package com.example.ratelimiter.ratelimit;

import org.springframework.stereotype.Component;

import com.example.ratelimiter.config.RateLimitProperties;
import com.example.ratelimiter.config.RateLimitProperties.Policy;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Decides who the caller is: a known API key gets its tier's policy,
 * everyone else is limited per IP address under the default policy.
 */
@Component
public class ClientResolver {

    public static final String API_KEY_HEADER = "X-API-Key";

    private final RateLimitProperties props;

    public ClientResolver(RateLimitProperties props) {
        this.props = props;
    }

    public ClientIdentity resolve(HttpServletRequest request) {
        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey != null && !apiKey.isBlank()) {
            String policyName = props.apiKeys().get(apiKey);
            if (policyName != null) {
                Policy policy = props.policies().getOrDefault(policyName, props.defaultPolicy());
                return new ClientIdentity("key:" + apiKey, policy);
            }
            // Unknown keys get no special treatment: fall through to per-IP limiting.
        }

        // Behind a reverse proxy, enable server.forward-headers-strategy so that
        // getRemoteAddr() reflects the real client rather than the proxy.
        return new ClientIdentity("ip:" + request.getRemoteAddr(), props.defaultPolicy());
    }
}
