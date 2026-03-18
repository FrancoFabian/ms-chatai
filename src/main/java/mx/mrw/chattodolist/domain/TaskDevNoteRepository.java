package mx.mrw.chattodolist.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskDevNoteRepository extends JpaRepository<TaskDevNoteEntity, UUID> {

    List<TaskDevNoteEntity> findByTaskIdOrderByCreatedAtAsc(String taskId);

    List<TaskDevNoteEntity> findByTaskIdInOrderByCreatedAtAsc(List<String> taskIds);
}
