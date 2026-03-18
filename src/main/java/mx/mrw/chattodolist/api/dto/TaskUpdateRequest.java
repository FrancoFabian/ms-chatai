package mx.mrw.chattodolist.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TaskUpdateRequest(
        @Pattern(regexp = "OPEN|IN_REVIEW|DONE", message = "status must be OPEN, IN_REVIEW or DONE") String status,
        @Pattern(regexp = "LOW|MEDIUM|HIGH", message = "priority must be LOW, MEDIUM or HIGH") String priority,
        @Pattern(regexp = "BUG|IMPROVEMENT|NEW_SECTION|OTHER", message = "type must be BUG, IMPROVEMENT, NEW_SECTION or OTHER") String type,
        @Size(max = 240) String title) {
}
