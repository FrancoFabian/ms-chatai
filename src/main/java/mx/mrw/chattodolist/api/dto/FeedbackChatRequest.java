package mx.mrw.chattodolist.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FeedbackChatRequest(
        @NotBlank @Size(max = 4000) String message,
        @NotBlank @Size(max = 1000) String route,
        @NotBlank @Size(max = 255) String sectionTag,
        @NotBlank @Size(max = 64) String role,
        @NotBlank @Size(max = 64) String roleTag,
        @NotBlank @Size(max = 128) String taskId,
        @NotBlank @Size(max = 32) String taskType,
        @NotBlank @Size(max = 16) String priority,
        @Size(max = 120) String userName,
        boolean isGeneralMode,
        @Size(max = 64) String modelPreference,
        List<@Valid TaskAttachmentInput> attachments) {
}
