package ai.koalla.core.entity

import jakarta.persistence.*
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "wp_message_history", schema = "koalla")
class WpMessageHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "user_id")
    var userId: UUID? = null,

    @Column(length = 10, nullable = false)
    var direction: String, // INBOUND | OUTBOUND

    @Column(columnDefinition = "jsonb")
    var metadata: String = "{}",

    @Column(name = "created_at", updatable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now()
)

@Entity
@Table(name = "message_queue", schema = "koalla")
class MessageQueue(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "wa_id", length = 20, nullable = false)
    var waId: String,

    @Column(name = "message_id", length = 100, nullable = false)
    var messageId: String,

    @Column(columnDefinition = "TEXT", nullable = false)
    var message: String,

    var timestamp: OffsetDateTime = OffsetDateTime.now()
)

@Entity
@Table(name = "chat_history", schema = "koalla")
class ChatHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "session_id", length = 50, nullable = false)
    var sessionId: String,

    @Column(columnDefinition = "jsonb", nullable = false)
    var message: String, // JSON string representing the message

    @Column(name = "created_at", updatable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now()
)

@Entity
@Table(name = "conversation_status", schema = "koalla")
class ConversationStatus(
    @Id
    @Column(name = "session_id", length = 50)
    var sessionId: String,

    @Column(name = "lock_conversa")
    var lockConversa: Boolean = false,

    @Column(name = "aguardando_followup")
    var aguardandoFollowup: Boolean = false,

    @Column(name = "numero_followup")
    var numeroFollowup: Int = 0,

    @Column(name = "updated_at")
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
) {
    @PreUpdate
    fun preUpdate() {
        updatedAt = OffsetDateTime.now()
    }
}

