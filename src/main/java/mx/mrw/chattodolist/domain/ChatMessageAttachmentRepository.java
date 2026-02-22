package mx.mrw.chattodolist.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageAttachmentRepository extends JpaRepository<ChatMessageAttachmentEntity, UUID> {

    List<ChatMessageAttachmentEntity> findByMessageIdInOrderByCreatedAtAsc(List<String> messageIds);
}
