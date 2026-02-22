package mx.mrw.chattodolist.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AppendChatMessageRequest(
        @Size(max = 128) String messageId,
        @NotBlank @Size(max = 8) String sender,
        @NotBlank @Size(max = 4000) String text,
        @Size(max = 128) String taskId,
        List<@Valid ChatMessageAttachmentInput> attachments) {
}
