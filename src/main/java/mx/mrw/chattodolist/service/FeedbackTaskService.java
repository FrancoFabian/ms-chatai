package mx.mrw.chattodolist.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import mx.mrw.chattodolist.api.dto.TaskAttachmentResponse;
import mx.mrw.chattodolist.api.dto.TaskAuthorResponse;
import mx.mrw.chattodolist.api.dto.TaskDevNoteCreateRequest;
import mx.mrw.chattodolist.api.dto.TaskDevNoteResponse;
import mx.mrw.chattodolist.api.dto.TaskItemResponse;
import mx.mrw.chattodolist.api.dto.TaskUpdateRequest;
import mx.mrw.chattodolist.domain.TaskAttachmentEntity;
import mx.mrw.chattodolist.domain.TaskAttachmentRepository;
import mx.mrw.chattodolist.domain.TaskDevNoteEntity;
import mx.mrw.chattodolist.domain.TaskDevNoteRepository;
import mx.mrw.chattodolist.domain.TaskEntity;
import mx.mrw.chattodolist.domain.TaskRepository;
import mx.mrw.chattodolist.exception.ApiException;

@Service
public class FeedbackTaskService {

    private static final String STATUS_OPEN = "OPEN";
    private static final String PRIORITY_MEDIUM = "MEDIUM";
    private static final String TYPE_OTHER = "OTHER";

    private final TaskRepository taskRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;
    private final TaskDevNoteRepository taskDevNoteRepository;
    private final MediaStorageService mediaStorageService;

    public FeedbackTaskService(
            TaskRepository taskRepository,
            TaskAttachmentRepository taskAttachmentRepository,
            TaskDevNoteRepository taskDevNoteRepository,
            MediaStorageService mediaStorageService) {
        this.taskRepository = taskRepository;
        this.taskAttachmentRepository = taskAttachmentRepository;
        this.taskDevNoteRepository = taskDevNoteRepository;
        this.mediaStorageService = mediaStorageService;
    }

    @Transactional(readOnly = true)
    public List<TaskItemResponse> listTasks(String subject) {
        List<TaskEntity> tasks = taskRepository.findTop200BySubjectOrderByUpdatedAtDesc(subject);
        if (tasks.isEmpty()) {
            return List.of();
        }
        return mapTasks(tasks);
    }

    @Transactional(readOnly = true)
    public TaskItemResponse getTask(String subject, String taskId) {
        TaskEntity task = requireOwnedTask(subject, taskId);
        return mapTasks(List.of(task)).getFirst();
    }

    @Transactional
    public TaskItemResponse updateTask(String subject, String taskId, TaskUpdateRequest request) {
        TaskEntity task = requireOwnedTask(subject, taskId);

        if (StringUtils.hasText(request.status())) {
            task.setStatus(request.status().trim());
        }
        if (StringUtils.hasText(request.priority())) {
            task.setPriority(request.priority().trim());
        }
        if (StringUtils.hasText(request.type())) {
            task.setTaskType(request.type().trim());
        }
        if (StringUtils.hasText(request.title())) {
            task.setTitle(deriveTitle(request.title().trim()));
        }

        task.setUpdatedAt(Instant.now());
        taskRepository.save(task);
        return mapTasks(List.of(task)).getFirst();
    }

    @Transactional
    public TaskDevNoteResponse addDevNote(String subject, String taskId, TaskDevNoteCreateRequest request) {
        TaskEntity task = requireOwnedTask(subject, taskId);

        TaskDevNoteEntity note = new TaskDevNoteEntity();
        note.setTaskId(task.getTaskId());
        note.setAuthorName(StringUtils.hasText(request.authorName()) ? request.authorName().trim() : null);
        note.setText(request.text().trim());
        TaskDevNoteEntity saved = taskDevNoteRepository.save(note);

        task.setUpdatedAt(Instant.now());
        taskRepository.save(task);

        return mapDevNote(saved);
    }

    @Transactional
    public void deleteAttachment(String subject, String taskId, String attachmentId) {
        TaskEntity task = requireOwnedTask(subject, taskId);

        UUID attachmentUuid;
        try {
            attachmentUuid = UUID.fromString(attachmentId);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ATTACHMENT_ID", "Attachment id is invalid");
        }

        long deleted = taskAttachmentRepository.deleteByIdAndTaskId(attachmentUuid, task.getTaskId());
        if (deleted <= 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ATTACHMENT_NOT_FOUND", "Attachment not found");
        }

        task.setUpdatedAt(Instant.now());
        taskRepository.save(task);
    }

    private TaskEntity requireOwnedTask(String subject, String taskId) {
        return taskRepository.findByTaskIdAndSubject(taskId, subject)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Task not found"));
    }

    private List<TaskItemResponse> mapTasks(List<TaskEntity> tasks) {
        List<String> taskIds = tasks.stream().map(TaskEntity::getTaskId).toList();
        List<TaskAttachmentEntity> attachments = taskAttachmentRepository.findByTaskIdInOrderByCreatedAtDesc(taskIds);
        List<TaskDevNoteEntity> devNotes = taskDevNoteRepository.findByTaskIdInOrderByCreatedAtAsc(taskIds);

        Map<String, List<TaskAttachmentEntity>> attachmentsByTask = new LinkedHashMap<>();
        for (TaskAttachmentEntity attachment : attachments) {
            attachmentsByTask.computeIfAbsent(attachment.getTaskId(), ignored -> new ArrayList<>()).add(attachment);
        }

        Map<String, List<TaskDevNoteEntity>> notesByTask = new LinkedHashMap<>();
        for (TaskDevNoteEntity note : devNotes) {
            notesByTask.computeIfAbsent(note.getTaskId(), ignored -> new ArrayList<>()).add(note);
        }

        List<TaskItemResponse> response = new ArrayList<>(tasks.size());
        for (TaskEntity task : tasks) {
            response.add(new TaskItemResponse(
                    task.getTaskId(),
                    task.getCreatedAt(),
                    task.getUpdatedAt(),
                    deriveTitle(task.getTitle()),
                    task.getUserMessage(),
                    task.getRoleTag(),
                    task.getSectionTag(),
                    task.getRoute(),
                    sanitizeType(task.getTaskType()),
                    sanitizePriority(task.getPriority()),
                    sanitizeStatus(task.getStatus()),
                    new TaskAuthorResponse("CLIENT_USER", task.getUserName()),
                    false,
                    attachmentsByTask.getOrDefault(task.getTaskId(), List.of()).stream()
                            .map(this::mapAttachment)
                            .toList(),
                    notesByTask.getOrDefault(task.getTaskId(), List.of()).stream()
                            .map(this::mapDevNote)
                            .toList()));
        }

        return response;
    }

    private TaskAttachmentResponse mapAttachment(TaskAttachmentEntity attachment) {
        return new TaskAttachmentResponse(
                attachment.getId().toString(),
                attachment.getTaskId(),
                attachment.getMediaPath(),
                attachment.getMimeType(),
                attachment.getSizeBytes(),
                attachment.getCreatedAt(),
                mediaStorageService.publicUrlFor(attachment.getMediaPath()));
    }

    private TaskDevNoteResponse mapDevNote(TaskDevNoteEntity note) {
        return new TaskDevNoteResponse(
                note.getId().toString(),
                note.getCreatedAt(),
                new TaskAuthorResponse("DEV_USER", note.getAuthorName()),
                note.getText());
    }

    private String sanitizeStatus(String raw) {
        if ("IN_REVIEW".equals(raw) || "DONE".equals(raw)) {
            return raw;
        }
        return STATUS_OPEN;
    }

    private String sanitizePriority(String raw) {
        if ("LOW".equals(raw) || "HIGH".equals(raw)) {
            return raw;
        }
        return PRIORITY_MEDIUM;
    }

    private String sanitizeType(String raw) {
        if ("BUG".equals(raw) || "IMPROVEMENT".equals(raw) || "NEW_SECTION".equals(raw)) {
            return raw;
        }
        return TYPE_OTHER;
    }

    private String deriveTitle(String rawTitle) {
        String source = StringUtils.hasText(rawTitle) ? rawTitle.trim() : "Untitled task";
        if (source.length() <= 60) {
            return source;
        }
        int split = source.substring(0, 60).lastIndexOf(' ');
        if (split > 30) {
            return source.substring(0, split) + "...";
        }
        return source.substring(0, 60) + "...";
    }
}
