package mx.mrw.chattodolist.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AiPromptGuardTest {

    private final AiPromptGuard guard = new AiPromptGuard();

    @Test
    void sanitizeShouldRemoveDataImagePayload() {
        String input = "detalle data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAA";
        String sanitized = guard.sanitizeForAi(input);

        assertFalse(sanitized.contains("data:image/"));
        assertTrue(sanitized.contains("[image_removed]"));
    }

    @Test
    void sanitizeShouldRemoveMarkdownImage() {
        String input = "mira esto ![captura](https://cdn.local/image.png) y ajusta boton";
        String sanitized = guard.sanitizeForAi(input);

        assertFalse(sanitized.contains("![captura]"));
        assertTrue(sanitized.contains("[image_removed]"));
    }

    @Test
    void sanitizeShouldKeepRegularText() {
        String input = "El boton guardar no responde en la vista de clientes";
        String sanitized = guard.sanitizeForAi(input);

        assertTrue(sanitized.contains("boton guardar"));
    }
}
