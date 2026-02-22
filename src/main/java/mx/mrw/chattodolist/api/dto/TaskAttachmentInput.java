package mx.mrw.chattodolist.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record TaskAttachmentInput(
        @NotBlank @Size(max = 512) String mediaPath,
        @NotBlank @Size(max = 64) String mime,
        @Positive long size) {
}
