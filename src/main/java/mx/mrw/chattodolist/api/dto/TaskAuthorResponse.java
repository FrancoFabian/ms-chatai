package mx.mrw.chattodolist.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskAuthorResponse(
        String kind,
        String name) {
}
