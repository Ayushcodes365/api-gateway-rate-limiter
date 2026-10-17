# API Gateway Rate Limiter (Spring Boot + Redis)

A small Spring Boot service whose `/api/**` endpoints sit behind a **distributed token bucket rate limiter**. All limiter state lives in Redis and is updated by one atomic Lua script, so several app instances share the same counters.

## How it works

```
client ──► RateLimitInterceptor ──► ClientResolver  (API key -> tier, otherwise IP)
                 │                         │
                 │                         ▼
                 └──────────► RedisTokenBucketLimiter ──► token_bucket.lua (in Redis)
                 │
        allowed ─┴─► controller      blocked ─► 429 + Retry-After
```

- **Token bucket:** each client has a bucket of `capacity` tokens that refills at `refill-per-second`. A request costs one token, so clients can burst up to `capacity` and then settle at the refill rate.
- **Atomic:** the read, refill, consume and write steps run inside Redis in a single Lua script, so there are no race conditions between app instances.
- **Redis clock:** the script uses Redis `TIME`, so app servers with skewed clocks still agree.
- **Self-cleaning:** every bucket key gets a TTL, so idle clients do not accumulate in Redis.
- **Tiers:** a known `X-API-Key` maps to a named policy (`free`, `premium`); everyone else is limited per IP with the default policy.
- **Fail open (configurable):** if Redis is unreachable, requests are allowed by default (`ratelimit.fail-open: false` rejects them instead).

Response headers on every limited endpoint: `X-RateLimit-Limit`, `X-RateLimit-Remaining`. Blocked requests also get `Retry-After`.

## Run it

Requirements: Java 17+, Maven, Docker.

```bash
docker compose up -d          # starts Redis on localhost:6379
mvn spring-boot:run           # starts the API on localhost:8080
```

## Try it

```bash
curl -i http://localhost:8080/api/hello

# Burst of 15 anonymous requests: the first 10 pass, then 429s
scripts/hammer.sh 15

# Higher tiers
scripts/hammer.sh 30 free-key-123
scripts/hammer.sh 30 premium-key-456
```

Peek at a bucket in Redis:

```bash
docker exec -it ratelimiter-redis redis-cli --scan --pattern 'rl:*'
docker exec -it ratelimiter-redis redis-cli hgetall 'rl:ip:127.0.0.1'
```

## Configure

Everything is in `src/main/resources/application.yml` under `ratelimit.*` (policies, API keys, Redis key prefix, fail-open). Redis host and port come from `REDIS_HOST` / `REDIS_PORT`.

## Test

```bash
mvn test
```

The tests cover client resolution and the interceptor's 200 / 429 behavior with a mocked limiter, so they do not need Redis.

## Project layout

```
src/main/java/com/example/ratelimiter/
  RateLimiterApplication.java
  config/    RateLimitProperties, RedisScriptConfig
  ratelimit/ RedisTokenBucketLimiter, ClientResolver, ClientIdentity, RateLimitResult
  web/       RateLimitInterceptor, WebConfig, DemoController
src/main/resources/scripts/token_bucket.lua
```

## Notes for production

- Behind a reverse proxy or load balancer, set `server.forward-headers-strategy` so the per-IP limit sees the real client address. Only enable it when the proxy is trusted, since otherwise clients can spoof `X-Forwarded-For`.
- Move the demo API keys out of `application.yml` into a secret store or database.
- For a very large Redis footprint, use Redis Cluster; keys are independent, so they shard cleanly.
- Ideas to extend: per-endpoint policies, a sliding window algorithm, Micrometer metrics for blocked requests, a Testcontainers test that runs the Lua script against a real Redis.
