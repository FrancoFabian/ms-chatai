package mx.mrw.chattodolist.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import mx.mrw.chattodolist.config.AppAiProperties;

@Component
public class ImproveMessageCache {

    private final AppAiProperties aiProperties;
    private final Clock clock;
    private final Map<String, CacheEntry> entries = new LinkedHashMap<>(128, 0.75f, true);

    public ImproveMessageCache(AppAiProperties aiProperties, Clock clock) {
        this.aiProperties = aiProperties;
        this.clock = clock;
    }

    public CacheResult get(String cacheKeyHash) {
        if (!isEnabled()) {
            return CacheResult.miss();
        }
        synchronized (entries) {
            CacheEntry entry = entries.get(cacheKeyHash);
            if (entry == null) {
                return CacheResult.miss();
            }
            if (entry.expiresAtMillis() < clock.millis()) {
                entries.remove(cacheKeyHash);
                return CacheResult.miss();
            }
            return CacheResult.hit(entry.value());
        }
    }

    public void put(String cacheKeyHash, String value) {
        if (!isEnabled() || !StringUtils.hasText(value)) {
            return;
        }
        long expiresAt = clock.millis() + ttlMillis();
        synchronized (entries) {
            entries.put(cacheKeyHash, new CacheEntry(value, expiresAt));
            evictOverflow();
        }
    }

    public String hashKey(
            String messageNormalized,
            String roleTag,
            String route,
            String sectionTag,
            String model,
            String promptVersion,
            String normalizationMode,
            double temperatureApplied) {
        String rawKey = String.join("|",
                sanitize(messageNormalized),
                sanitize(roleTag),
                sanitize(route),
                sanitize(sectionTag),
                sanitize(model),
                sanitize(promptVersion),
                sanitize(normalizationMode),
                String.format(java.util.Locale.ROOT, "%.3f", temperatureApplied));
        return sha256(rawKey);
    }

    private void evictOverflow() {
        int maxEntries = Math.max(100, aiProperties.getCache().getImproveMessage().getMaxEntries());
        while (entries.size() > maxEntries) {
            String eldestKey = entries.keySet().iterator().next();
            entries.remove(eldestKey);
        }
    }

    private boolean isEnabled() {
        return aiProperties.getCache().getImproveMessage().isEnabled();
    }

    private long ttlMillis() {
        return Math.max(1, aiProperties.getCache().getImproveMessage().getTtlSeconds()) * 1000L;
    }

    private String sanitize(String value) {
        return value == null ? "" : value;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        }
        catch (Exception exception) {
            throw new IllegalStateException("Unable to hash improve-message cache key", exception);
        }
    }

    private record CacheEntry(String value, long expiresAtMillis) {
    }

    public record CacheResult(boolean hit, String value) {
        static CacheResult hit(String value) {
            return new CacheResult(true, value);
        }

        static CacheResult miss() {
            return new CacheResult(false, null);
        }
    }
}
