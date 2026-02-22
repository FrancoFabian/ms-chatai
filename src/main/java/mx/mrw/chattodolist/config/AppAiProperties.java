package mx.mrw.chattodolist.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public class AppAiProperties {

    private FlowProperties flow = new FlowProperties();
    private NormalizationProperties normalization = new NormalizationProperties();
    private CacheProperties cache = new CacheProperties();

    public FlowProperties getFlow() {
        return flow;
    }

    public void setFlow(FlowProperties flow) {
        this.flow = flow;
    }

    public NormalizationProperties getNormalization() {
        return normalization;
    }

    public void setNormalization(NormalizationProperties normalization) {
        this.normalization = normalization;
    }

    public CacheProperties getCache() {
        return cache;
    }

    public void setCache(CacheProperties cache) {
        this.cache = cache;
    }

    public static class FlowProperties {
        private FlowLimit feedbackChat = new FlowLimit();
        private FlowLimit improveMessage = new FlowLimit();

        public FlowProperties() {
            this.feedbackChat.setMaxTokens(220);
            this.improveMessage.setMaxTokens(140);
        }

        public FlowLimit getFeedbackChat() {
            return feedbackChat;
        }

        public void setFeedbackChat(FlowLimit feedbackChat) {
            this.feedbackChat = feedbackChat;
        }

        public FlowLimit getImproveMessage() {
            return improveMessage;
        }

        public void setImproveMessage(FlowLimit improveMessage) {
            this.improveMessage = improveMessage;
        }
    }

    public static class FlowLimit {
        private int maxTokens = 220;

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }
    }

    public static class NormalizationProperties {
        private String mode = "balanced";
        private boolean stripMarkdown = false;
        private boolean stripHtml = false;
        private MaxInputChars maxInputChars = new MaxInputChars();

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public boolean isStripMarkdown() {
            return stripMarkdown;
        }

        public void setStripMarkdown(boolean stripMarkdown) {
            this.stripMarkdown = stripMarkdown;
        }

        public boolean isStripHtml() {
            return stripHtml;
        }

        public void setStripHtml(boolean stripHtml) {
            this.stripHtml = stripHtml;
        }

        public MaxInputChars getMaxInputChars() {
            return maxInputChars;
        }

        public void setMaxInputChars(MaxInputChars maxInputChars) {
            this.maxInputChars = maxInputChars;
        }
    }

    public static class MaxInputChars {
        private int feedbackChat = 1800;
        private int improveMessage = 1400;

        public int getFeedbackChat() {
            return feedbackChat;
        }

        public void setFeedbackChat(int feedbackChat) {
            this.feedbackChat = feedbackChat;
        }

        public int getImproveMessage() {
            return improveMessage;
        }

        public void setImproveMessage(int improveMessage) {
            this.improveMessage = improveMessage;
        }
    }

    public static class CacheProperties {
        private ImproveMessageCacheProperties improveMessage = new ImproveMessageCacheProperties();

        public ImproveMessageCacheProperties getImproveMessage() {
            return improveMessage;
        }

        public void setImproveMessage(ImproveMessageCacheProperties improveMessage) {
            this.improveMessage = improveMessage;
        }
    }

    public static class ImproveMessageCacheProperties {
        private boolean enabled = true;
        private int ttlSeconds = 60;
        private int maxEntries = 1000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(int ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }

        public int getMaxEntries() {
            return maxEntries;
        }

        public void setMaxEntries(int maxEntries) {
            this.maxEntries = maxEntries;
        }
    }
}
