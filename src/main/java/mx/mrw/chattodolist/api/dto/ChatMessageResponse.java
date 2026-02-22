package mx.mrw.chattodolist.api.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessageResponse(
        String id,
        Instant createdAt,
        String sender,
        String text,
        String taskId,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<ChatMessageAttachmentResponse> attachments) {
}
