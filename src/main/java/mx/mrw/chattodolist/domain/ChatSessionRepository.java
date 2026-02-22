package mx.mrw.chattodolist.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, String> {

    List<ChatSessionEntity> findTop50BySubjectOrderByUpdatedAtDesc(String subject);

    Optional<ChatSessionEntity> findByIdAndSubject(String id, String subject);
}
