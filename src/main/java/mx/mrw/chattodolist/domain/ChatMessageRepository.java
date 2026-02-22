package mx.mrw.chattodolist.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, String> {

    List<ChatMessageEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    List<ChatMessageEntity> findBySessionIdInOrderByCreatedAtAsc(List<String> sessionIds);

    void deleteBySessionId(String sessionId);
}
