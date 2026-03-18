package mx.mrw.chattodolist.api.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskItemResponse(
        String id,
        Instant createdAt,
        Instant updatedAt,
        String title,
        String message,
        String roleTag,
        String sectionTag,
        String route,
        String type,
        String priority,
        String status,
        TaskAuthorResponse author,
        boolean includeScreenshotLater,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<TaskAttachmentResponse> attachments,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<TaskDevNoteResponse> devNotes) {
}
