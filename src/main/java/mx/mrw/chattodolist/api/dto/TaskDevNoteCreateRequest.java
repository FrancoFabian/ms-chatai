package mx.mrw.chattodolist.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaskDevNoteCreateRequest(
        @Size(max = 120) String authorName,
        @NotBlank @Size(max = 4000) String text) {
}
