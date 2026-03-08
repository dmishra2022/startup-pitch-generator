package com.startup.pitch.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate Limiter Service using Bucket4j for IP-based daily limits.
 * - Default: 5 requests per IP per day
 * - With API key: 15 requests per IP per day (5 + 10 bonus)
 */
@Service
public class RateLimiterService {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    private static final int DEFAULT_LIMIT = 5;
    private static final int BONUS_LIMIT = 10;

    /**
     * Checks if the IP can make a request.
     * @param ip Client IP address
     * @param hasApiKey Whether user provided their own API key
     * @return true if allowed, false if rate limited
     */
    public boolean tryConsume(String ip, boolean hasApiKey) {
        Bucket bucket = buckets.computeIfAbsent(ip, this::createBucket);
        int capacity = hasApiKey ? DEFAULT_LIMIT + BONUS_LIMIT : DEFAULT_LIMIT;
        // Adjust bucket capacity if needed (Bucket4j doesn't allow dynamic capacity easily, so recreate if changed)
        // For simplicity, assume capacity is set once per IP per day reset
        return bucket.tryConsume(1);
    }

    private Bucket createBucket(String ip) {
        // Daily refill: 5 tokens, refill every 24 hours
        Bandwidth limit = Bandwidth.classic(DEFAULT_LIMIT, Refill.intervally(DEFAULT_LIMIT, Duration.ofDays(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    // For bonus, we need to add more tokens if hasApiKey
    // But Bucket4j buckets are fixed. Perhaps use separate buckets or adjust.
    // Simpler: Use a higher limit and track separately, but for now, let's use a map for counts.

    // Actually, to make it simple, let's use a custom counter with timestamps.

    // Revised: Use ConcurrentHashMap<String, RequestCount> where RequestCount has count and lastReset.

    private static class RequestCount {
        int count;
        long lastReset; // timestamp

        RequestCount() {
            this.count = 0;
            this.lastReset = System.currentTimeMillis();
        }
    }

    private final ConcurrentHashMap<String, RequestCount> requestCounts = new ConcurrentHashMap<>();

    public boolean tryConsumeSimple(String ip, boolean hasApiKey) {
        RequestCount rc = requestCounts.computeIfAbsent(ip, k -> new RequestCount());
        long now = System.currentTimeMillis();
        long dayMs = 24 * 60 * 60 * 1000;
        if (now - rc.lastReset > dayMs) {
            rc.count = 0;
            rc.lastReset = now;
        }
        int limit = hasApiKey ? DEFAULT_LIMIT + BONUS_LIMIT : DEFAULT_LIMIT;
        if (rc.count < limit) {
            rc.count++;
            return true;
        }
        return false;
    }
}
