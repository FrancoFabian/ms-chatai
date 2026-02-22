package mx.mrw.chattodolist.api.dto;

public record ChatMessageAttachmentResponse(
        String mediaPath,
        String mimeType,
        long sizeBytes) {
}
