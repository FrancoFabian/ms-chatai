package mx.mrw.chattodolist.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiUsageEventRepository extends JpaRepository<AiUsageEventEntity, UUID> {

    List<AiUsageEventEntity> findBySubjectOrderByCreatedAtDesc(String subject);
}
