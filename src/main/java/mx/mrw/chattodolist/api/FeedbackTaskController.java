package mx.mrw.chattodolist.api;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import mx.mrw.chattodolist.api.dto.TaskDevNoteCreateRequest;
import mx.mrw.chattodolist.api.dto.TaskDevNoteResponse;
import mx.mrw.chattodolist.api.dto.TaskItemResponse;
import mx.mrw.chattodolist.api.dto.TaskListResponse;
import mx.mrw.chattodolist.api.dto.TaskUpdateRequest;
import mx.mrw.chattodolist.security.AuthContext;
import mx.mrw.chattodolist.service.FeedbackTaskService;
import mx.mrw.chattodolist.support.RequestContext;

@Validated
@RestController
@RequestMapping("/api/feedback-chat/tasks")
public class FeedbackTaskController {

    private final FeedbackTaskService feedbackTaskService;

    public FeedbackTaskController(FeedbackTaskService feedbackTaskService) {
        this.feedbackTaskService = feedbackTaskService;
    }

    @GetMapping
    public TaskListResponse listTasks(HttpServletRequest httpServletRequest) {
        String subject = subject(httpServletRequest);
        List<TaskItemResponse> tasks = feedbackTaskService.listTasks(subject);
        return new TaskListResponse(requestId(httpServletRequest), tasks);
    }

    @GetMapping("/{taskId}")
    public TaskItemResponse getTask(
            @PathVariable String taskId,
            HttpServletRequest httpServletRequest) {
        String subject = subject(httpServletRequest);
        return feedbackTaskService.getTask(subject, taskId);
    }

    @PatchMapping("/{taskId}")
    public TaskItemResponse updateTask(
            @PathVariable String taskId,
            @Valid @RequestBody TaskUpdateRequest request,
            HttpServletRequest httpServletRequest) {
        String subject = subject(httpServletRequest);
        return feedbackTaskService.updateTask(subject, taskId, request);
    }

    @PostMapping("/{taskId}/dev-notes")
    public TaskDevNoteResponse addDevNote(
            @PathVariable String taskId,
            @Valid @RequestBody TaskDevNoteCreateRequest request,
            HttpServletRequest httpServletRequest) {
        String subject = subject(httpServletRequest);
        return feedbackTaskService.addDevNote(subject, taskId, request);
    }

    @DeleteMapping("/{taskId}/attachments/{attachmentId}")
    public void deleteAttachment(
            @PathVariable String taskId,
            @PathVariable String attachmentId,
            HttpServletRequest httpServletRequest) {
        String subject = subject(httpServletRequest);
        feedbackTaskService.deleteAttachment(subject, taskId, attachmentId);
    }

    private String requestId(HttpServletRequest httpServletRequest) {
        Object value = httpServletRequest.getAttribute(RequestContext.REQUEST_ID);
        return value == null ? null : value.toString();
    }

    private String subject(HttpServletRequest httpServletRequest) {
        AuthContext authContext = (AuthContext) httpServletRequest.getAttribute(RequestContext.AUTH_CONTEXT);
        return authContext == null ? "unknown" : authContext.subject();
    }
}
