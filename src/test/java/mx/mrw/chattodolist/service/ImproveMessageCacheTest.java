package mx.mrw.chattodolist.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import mx.mrw.chattodolist.config.AppAiProperties;

class ImproveMessageCacheTest {

    private MutableClock clock;
    private ImproveMessageCache cache;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-02-21T00:00:00Z"));
        AppAiProperties properties = new AppAiProperties();
        properties.getCache().getImproveMessage().setEnabled(true);
        properties.getCache().getImproveMessage().setTtlSeconds(1);
        properties.getCache().getImproveMessage().setMaxEntries(100);
        cache = new ImproveMessageCache(properties, clock);
    }

    @Test
    void shouldReturnHitBeforeTtlExpires() {
        String key = cache.hashKey("mensaje", "DEV", "/dashboard/dev", "dev", "gpt-5-mini", "improve-v2", "balanced", 0.2);
        cache.put(key, "mensaje mejorado");

        ImproveMessageCache.CacheResult result = cache.get(key);
        assertTrue(result.hit());
        assertTrue("mensaje mejorado".equals(result.value()));
    }

    @Test
    void shouldExpireEntryAfterTtl() {
        String key = cache.hashKey("mensaje", "DEV", "/dashboard/dev", "dev", "gpt-5-mini", "improve-v2", "balanced", 0.2);
        cache.put(key, "mensaje mejorado");

        clock.advanceMillis(1_100L);
        ImproveMessageCache.CacheResult result = cache.get(key);
        assertFalse(result.hit());
    }

    @Test
    void hashKeyShouldBeStable() {
        String first = cache.hashKey("msg", "DEV", "/dashboard/dev", "dev", "gpt-5-mini", "improve-v2", "balanced", 0.2);
        String second = cache.hashKey("msg", "DEV", "/dashboard/dev", "dev", "gpt-5-mini", "improve-v2", "balanced", 0.2);
        assertNotNull(first);
        assertTrue(first.equals(second));
    }

    private static final class MutableClock extends Clock {
        private final AtomicLong millis;
        private final ZoneId zoneId = ZoneOffset.UTC;

        private MutableClock(Instant initial) {
            this.millis = new AtomicLong(initial.toEpochMilli());
        }

        void advanceMillis(long delta) {
            millis.addAndGet(delta);
        }

        @Override
        public ZoneId getZone() {
            return zoneId;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis.get());
        }
    }
}
