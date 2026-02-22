package mx.mrw.chattodolist.service;

public record NormalizedInput(
        String text,
        String normalizationMode,
        boolean truncated,
        int originalChars,
        int normalizedChars) {
}
