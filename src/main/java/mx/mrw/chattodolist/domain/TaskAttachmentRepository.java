package mx.mrw.chattodolist.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskAttachmentRepository extends JpaRepository<TaskAttachmentEntity, UUID> {

    List<TaskAttachmentEntity> findByTaskIdOrderByCreatedAtDesc(String taskId);

    void deleteByTaskId(String taskId);
}
