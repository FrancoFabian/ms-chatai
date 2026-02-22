package mx.mrw.chattodolist.rate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import mx.mrw.chattodolist.config.AppRateLimitProperties;

class InMemoryRateLimiterTest {

    @Test
    void allowShouldApplyRequestsPerMinutePlusBurst() {
        MutableClock clock = new MutableClock(Instant.parse("2026-02-21T00:00:00Z"));
        AppRateLimitProperties properties = new AppRateLimitProperties();
        properties.setEnabled(true);
        properties.setRequestsPerMinute(2);
        properties.setBurst(1);

        InMemoryRateLimiter rateLimiter = new InMemoryRateLimiter(properties, clock);

        assertTrue(rateLimiter.allow("user-1"));
        assertTrue(rateLimiter.allow("user-1"));
        assertTrue(rateLimiter.allow("user-1"));
        assertFalse(rateLimiter.allow("user-1"));
    }

    @Test
    void allowShouldResetAfterWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-02-21T00:00:00Z"));
        AppRateLimitProperties properties = new AppRateLimitProperties();
        properties.setEnabled(true);
        properties.setRequestsPerMinute(1);
        properties.setBurst(0);

        InMemoryRateLimiter rateLimiter = new InMemoryRateLimiter(properties, clock);

        assertTrue(rateLimiter.allow("user-1"));
        assertFalse(rateLimiter.allow("user-1"));

        clock.advanceSeconds(61);
        assertTrue(rateLimiter.allow("user-1"));
    }

    private static final class MutableClock extends Clock {

        private final AtomicLong currentMillis;
        private final ZoneId zoneId;

        private MutableClock(Instant initial) {
            this.currentMillis = new AtomicLong(initial.toEpochMilli());
            this.zoneId = ZoneOffset.UTC;
        }

        void advanceSeconds(long seconds) {
            currentMillis.addAndGet(seconds * 1000);
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
            return Instant.ofEpochMilli(currentMillis.get());
        }
    }
}
