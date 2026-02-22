package mx.mrw.chattodolist.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageAttachmentInput(
        @NotBlank @Size(max = 512) String mediaPath,
        @NotBlank @Size(max = 64) String mimeType,
        @Min(1) long sizeBytes) {
}
