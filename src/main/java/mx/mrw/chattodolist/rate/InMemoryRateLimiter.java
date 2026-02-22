package mx.mrw.chattodolist.rate;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import mx.mrw.chattodolist.config.AppRateLimitProperties;
import mx.mrw.chattodolist.exception.ApiException;

@Component
public class InMemoryRateLimiter {

    private static final long WINDOW_MS = 60_000L;

    private final AppRateLimitProperties properties;
    private final Clock clock;
    private final Map<String, Deque<Long>> requestsByClient = new ConcurrentHashMap<>();

    public InMemoryRateLimiter(AppRateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void assertAllowed(String clientId) {
        if (!allow(clientId)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", "Too many requests");
        }
    }

    public boolean allow(String clientId) {
        if (!properties.isEnabled()) {
            return true;
        }
        String key = StringUtils.hasText(clientId) ? clientId : "anonymous";
        long now = clock.millis();
        int maxRequests = Math.max(1, properties.getRequestsPerMinute() + properties.getBurst());

        Deque<Long> queue = requestsByClient.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (queue) {
            long windowStart = now - WINDOW_MS;
            while (!queue.isEmpty() && queue.peekFirst() < windowStart) {
                queue.pollFirst();
            }

            if (queue.size() >= maxRequests) {
                return false;
            }

            queue.addLast(now);
            return true;
        }
    }
}
