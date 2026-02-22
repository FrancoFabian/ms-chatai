package mx.mrw.chattodolist.api.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String requestId,
        String error,
        String message,
        Map<String, String> validationErrors) {
}
