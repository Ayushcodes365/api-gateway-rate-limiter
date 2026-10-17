package com.example.ratelimiter.web;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A couple of trivial endpoints to put behind the rate limiter. */
@RestController
@RequestMapping("/api")
public class DemoController {

    @GetMapping("/hello")
    public Map<String, Object> hello() {
        return Map.of("message", "Hello from the rate-limited API", "timestamp", Instant.now().toString());
    }

    @GetMapping("/echo/{text}")
    public Map<String, Object> echo(@PathVariable String text) {
        return Map.of("echo", text, "timestamp", Instant.now().toString());
    }
}
