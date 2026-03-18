package mx.mrw.chattodolist.api.dto;

import java.time.Instant;

public record TaskAttachmentResponse(
        String id,
        String taskId,
        String mediaPath,
        String mimeType,
        long sizeBytes,
        Instant createdAt,
        String url) {
}
