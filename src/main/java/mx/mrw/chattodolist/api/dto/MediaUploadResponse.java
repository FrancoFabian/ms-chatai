package mx.mrw.chattodolist.api.dto;

public record MediaUploadResponse(
        String mediaPath,
        String url,
        String mime,
        long size) {
}
