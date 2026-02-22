package mx.mrw.chattodolist.service;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class AiPromptGuard {

    private static final Pattern DATA_IMAGE_URL = Pattern.compile(
            "data:image\\/(png|jpeg|jpg|webp);base64,[a-zA-Z0-9+/=\\r\\n]+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKDOWN_IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\([^)]*\\)");

    public String sanitizeForAi(String input) {
        if (input == null) {
            return "";
        }
        String withoutDataUrls = DATA_IMAGE_URL.matcher(input).replaceAll("[image_removed]");
        return MARKDOWN_IMAGE.matcher(withoutDataUrls).replaceAll("[image_removed]");
    }
}
