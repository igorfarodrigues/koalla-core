package ai.koalla.core.repository

import ai.koalla.core.entity.ChatHistory
import ai.koalla.core.entity.ConversationStatus
import ai.koalla.core.entity.MessageQueue
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface MessageQueueRepository : JpaRepository<MessageQueue, Long> {
    fun findByWaIdOrderByTimestampAsc(waId: String): List<MessageQueue>

    @Modifying
    @Query("DELETE FROM MessageQueue m WHERE m.waId = :waId")
    fun deleteByWaId(waId: String)
}

@Repository
interface ChatHistoryRepository : JpaRepository<ChatHistory, Long> {
    fun findBySessionIdOrderByCreatedAtAsc(sessionId: String): List<ChatHistory>

    @Modifying
    @Query("DELETE FROM ChatHistory c WHERE c.sessionId = :sessionId")
    fun deleteBySessionId(sessionId: String)
}

@Repository
interface ConversationStatusRepository : JpaRepository<ConversationStatus, String> {
    fun findBySessionId(sessionId: String): ConversationStatus?
}
