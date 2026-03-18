package mx.mrw.chattodolist.api.dto;

import java.time.Instant;

public record TaskDevNoteResponse(
        String id,
        Instant createdAt,
        TaskAuthorResponse author,
        String text) {
}
