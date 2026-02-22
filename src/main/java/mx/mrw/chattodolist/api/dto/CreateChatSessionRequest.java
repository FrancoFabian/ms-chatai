package mx.mrw.chattodolist.api.dto;

import jakarta.validation.constraints.Size;

public record CreateChatSessionRequest(
        @Size(max = 128) String sessionId,
        @Size(max = 180) String title) {
}
