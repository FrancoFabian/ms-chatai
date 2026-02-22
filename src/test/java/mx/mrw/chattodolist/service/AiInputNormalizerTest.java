package mx.mrw.chattodolist.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import mx.mrw.chattodolist.config.AppAiProperties;

class AiInputNormalizerTest {

    private AiInputNormalizer normalizer;

    @BeforeEach
    void setUp() {
        AppAiProperties properties = new AppAiProperties();
        properties.getNormalization().setMode("balanced");
        properties.getNormalization().getMaxInputChars().setFeedbackChat(220);
        properties.getNormalization().getMaxInputChars().setImproveMessage(180);
        normalizer = new AiInputNormalizer(properties, new ObjectMapper());
    }

    @Test
    void normalizeShouldPreserveUrlsPathsTagsAndIds() {
        String raw = "  Revisar https://demo.local/app  en /dashboard/dev con #Clients/pagos id ABCD1234   ";
        NormalizedInput output = normalizer.normalizeMessage(AiFlow.FEEDBACK_CHAT, raw);

        assertTrue(output.text().contains("https://demo.local/app"));
        assertTrue(output.text().contains("/dashboard/dev"));
        assertTrue(output.text().contains("#Clients/pagos"));
        assertTrue(output.text().contains("ABCD1234"));
    }

    @Test
    void normalizeShouldApplySmartTruncationWithMarker() {
        String raw = "inicio ".repeat(80) + "medio " + "final ".repeat(80);
        NormalizedInput output = normalizer.normalizeMessage(AiFlow.FEEDBACK_CHAT, raw);

        assertTrue(output.text().contains("[...truncated...]"));
        assertTrue(output.truncated());
        assertTrue(output.text().startsWith("inicio"));
        assertTrue(output.text().contains("final"));
    }

    @Test
    void normalizeShouldCompressLargeCodeBlockWithoutBreakingFence() {
        StringBuilder code = new StringBuilder();
        code.append("```java\n");
        for (int i = 0; i < 40; i++) {
            code.append("line").append(i).append(";\n");
        }
        code.append("```\n");
        String raw = "encabezado\n" + code + "cola";

        NormalizedInput output = normalizer.normalizeMessage(AiFlow.IMPROVE_MESSAGE, raw);

        assertTrue(output.text().contains("[...truncated_code_block...]"));
        assertTrue(output.text().contains("```"));
        assertFalse(output.text().isBlank());
    }
}
