package mx.mrw.chattodolist.api.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatSessionResponse(
        String id,
        String title,
        String status,
        Instant createdAt,
        Instant updatedAt,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<ChatMessageResponse> messages) {
}
