package mx.mrw.chattodolist.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import mx.mrw.chattodolist.config.AppAiProperties;

@Component
public class AiInputNormalizer {

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("(?s)```.*?```");
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://\\S+|www\\.\\S+)");
    private static final Pattern PATH_PATTERN = Pattern.compile("([A-Za-z]:\\\\[^\\s]+|/[^\\s]+|\\./[^\\s]+)");
    private static final Pattern TAG_PATTERN = Pattern.compile("#[\\w\\-/]+");
    private static final Pattern ID_PATTERN = Pattern.compile("\\b([A-Fa-f0-9]{8,}|[A-Za-z]{2,}-\\d{2,})\\b");

    private final AppAiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public AiInputNormalizer(AppAiProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    public NormalizedInput normalizeMessage(AiFlow flow, String rawInput) {
        String source = rawInput == null ? "" : rawInput;
        String mode = resolveMode();
        int originalChars = source.length();
        int maxInputChars = maxInputCharsForFlow(flow);

        if ("conservative".equalsIgnoreCase(mode)) {
            String normalized = source.trim();
            if (normalized.length() > maxInputChars) {
                normalized = truncateByFlow(flow, normalized, maxInputChars);
            }
            return new NormalizedInput(normalized, mode, normalized.length() < originalChars, originalChars, normalized.length());
        }

        String normalized = applyBalanced(source, mode);
        boolean truncated = false;
        if (normalized.length() > maxInputChars) {
            normalized = truncateByFlow(flow, normalized, maxInputChars);
            truncated = true;
        }
        return new NormalizedInput(normalized, mode, truncated || normalized.length() < originalChars, originalChars, normalized.length());
    }

    public String normalizeContextValue(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private String applyBalanced(String source, String mode) {
        String input = source.trim().replace("\r\n", "\n");
        if (input.isEmpty()) {
            return input;
        }

        boolean hasProtectedSignals = hasProtectedSignals(input);
        boolean looksLikeJson = isJsonPayload(input);

        String normalized = normalizeOutsideCodeBlocks(input, hasProtectedSignals, looksLikeJson, mode);
        normalized = compressLargeCodeBlocks(normalized);
        normalized = normalized.replaceAll("\\n{3,}", "\n\n").trim();

        return normalized;
    }

    private String normalizeOutsideCodeBlocks(String input, boolean hasProtectedSignals, boolean looksLikeJson, String mode) {
        List<String> segments = new ArrayList<>();
        List<Boolean> codeFlags = new ArrayList<>();
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(input);
        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                segments.add(input.substring(cursor, matcher.start()));
                codeFlags.add(false);
            }
            segments.add(matcher.group());
            codeFlags.add(true);
            cursor = matcher.end();
        }
        if (cursor < input.length()) {
            segments.add(input.substring(cursor));
            codeFlags.add(false);
        }

        StringBuilder output = new StringBuilder(input.length());
        for (int i = 0; i < segments.size(); i++) {
            String segment = segments.get(i);
            if (codeFlags.get(i)) {
                output.append(segment);
                continue;
            }
            output.append(cleanTextSegment(segment, hasProtectedSignals, looksLikeJson, mode));
        }
        return output.toString();
    }

    private String cleanTextSegment(String segment, boolean hasProtectedSignals, boolean looksLikeJson, String mode) {
        String value = segment.replace("\t", " ");
        value = value.replaceAll(" {2,}", " ");
        value = value.replaceAll("\\n[ \\t]+", "\n");

        if ("strict".equalsIgnoreCase(mode) && !hasProtectedSignals && !looksLikeJson) {
            if (aiProperties.getNormalization().isStripHtml()) {
                value = value.replaceAll("(?is)<[^>]+>", " ");
            }
            if (aiProperties.getNormalization().isStripMarkdown()) {
                value = value.replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "");
                value = value.replaceAll("(?m)^\\s*>\\s*", "");
                value = value.replaceAll("\\*\\*(.*?)\\*\\*", "$1");
                value = value.replaceAll("\\*(.*?)\\*", "$1");
            }
        }

        return value;
    }

    private String compressLargeCodeBlocks(String input) {
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(input);
        StringBuffer buffer = new StringBuffer(input.length());
        while (matcher.find()) {
            String codeBlock = matcher.group();
            String compressed = compressSingleCodeBlock(codeBlock);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(compressed));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String compressSingleCodeBlock(String codeBlock) {
        if (!codeBlock.startsWith("```") || !codeBlock.endsWith("```")) {
            return codeBlock;
        }
        String inner = codeBlock.substring(3, codeBlock.length() - 3);
        String[] lines = inner.split("\\R", -1);
        if (lines.length <= 20) {
            return codeBlock;
        }

        StringBuilder compressed = new StringBuilder();
        compressed.append("```");
        for (int i = 0; i < 12; i++) {
            compressed.append(lines[i]).append("\n");
        }
        compressed.append("[...truncated_code_block...]\n");
        for (int i = Math.max(12, lines.length - 8); i < lines.length; i++) {
            compressed.append(lines[i]);
            if (i < lines.length - 1) {
                compressed.append("\n");
            }
        }
        compressed.append("```");
        return compressed.toString();
    }

    private String truncateByFlow(AiFlow flow, String input, int maxChars) {
        if (input.length() <= maxChars) {
            return input;
        }
        String marker = "\n[...truncated...]\n";
        int available = maxChars - marker.length();
        if (available < 40) {
            return input.substring(0, Math.max(0, maxChars));
        }

        double startRatio = flow == AiFlow.FEEDBACK_CHAT ? 0.65d : 0.75d;
        int startLength = Math.max(10, (int) Math.floor(available * startRatio));
        int endLength = Math.max(10, available - startLength);
        if (startLength + endLength > available) {
            endLength = available - startLength;
        }

        int suffixStart = Math.max(0, input.length() - endLength);
        startLength = adjustPrefixBoundary(input, startLength);
        suffixStart = adjustSuffixBoundary(input, suffixStart);

        String prefix = input.substring(0, Math.min(startLength, input.length())).trim();
        String suffix = input.substring(Math.min(suffixStart, input.length())).trim();
        return prefix + marker + suffix;
    }

    private int adjustPrefixBoundary(String input, int boundary) {
        int safeBoundary = Math.max(0, Math.min(boundary, input.length()));
        if (isInsideCodeFence(input, safeBoundary)) {
            int previousFence = input.lastIndexOf("```", safeBoundary - 1);
            if (previousFence >= 0) {
                return previousFence;
            }
        }
        return safeBoundary;
    }

    private int adjustSuffixBoundary(String input, int startIndex) {
        int safeBoundary = Math.max(0, Math.min(startIndex, input.length()));
        if (isInsideCodeFence(input, safeBoundary)) {
            int nextFence = input.indexOf("```", safeBoundary);
            if (nextFence >= 0) {
                return Math.min(input.length(), nextFence + 3);
            }
        }
        return safeBoundary;
    }

    private boolean isInsideCodeFence(String input, int position) {
        if (position <= 0 || position >= input.length()) {
            return false;
        }
        String before = input.substring(0, position);
        int fenceCount = countOccurrences(before, "```");
        return fenceCount % 2 != 0;
    }

    private int countOccurrences(String value, String token) {
        if (value.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private boolean hasProtectedSignals(String input) {
        return URL_PATTERN.matcher(input).find()
            || PATH_PATTERN.matcher(input).find()
            || TAG_PATTERN.matcher(input).find()
            || ID_PATTERN.matcher(input).find()
            || isJsonPayload(input)
            || CODE_BLOCK_PATTERN.matcher(input).find();
    }

    private boolean isJsonPayload(String input) {
        String trimmed = input.trim();
        if (!(trimmed.startsWith("{") && trimmed.endsWith("}"))
                && !(trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            return false;
        }
        try {
            objectMapper.readTree(trimmed);
            return true;
        }
        catch (Exception ignored) {
            return false;
        }
    }

    private String resolveMode() {
        String configured = aiProperties.getNormalization().getMode();
        if (!StringUtils.hasText(configured)) {
            return "balanced";
        }
        String normalized = configured.trim().toLowerCase(Locale.ROOT);
        if ("strict".equals(normalized) || "conservative".equals(normalized) || "balanced".equals(normalized)) {
            return normalized;
        }
        return "balanced";
    }

    private int maxInputCharsForFlow(AiFlow flow) {
        AppAiProperties.MaxInputChars maxInputChars = aiProperties.getNormalization().getMaxInputChars();
        return flow == AiFlow.FEEDBACK_CHAT
                ? Math.max(200, maxInputChars.getFeedbackChat())
                : Math.max(200, maxInputChars.getImproveMessage());
    }
}
