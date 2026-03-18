package mx.mrw.chattodolist.api.dto;

import java.util.List;

public record TaskListResponse(
        String requestId,
        List<TaskItemResponse> tasks) {
}
