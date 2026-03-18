package mx.mrw.chattodolist.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {

    Optional<TaskEntity> findByTaskId(String taskId);

    Optional<TaskEntity> findByTaskIdAndSubject(String taskId, String subject);

    List<TaskEntity> findTop200BySubjectOrderByUpdatedAtDesc(String subject);
}
