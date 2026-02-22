package mx.mrw.chattodolist.api.dto;

import java.util.List;

public record ChatSessionsResponse(
        String requestId,
        List<ChatSessionResponse> sessions) {
}
