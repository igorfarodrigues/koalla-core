package ai.koalla.core.entity

import jakarta.persistence.*
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "users", schema = "koalla")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "wa_id", length = 20, unique = true, nullable = false)
    var waId: String,

    @Column(name = "full_name", length = 100)
    var fullName: String? = null,

    @Column(length = 100, unique = true)
    var email: String? = null,

    @Column(name = "plan_type", length = 30)
    var planType: String = "FREE",

    var lifetime: Boolean = false,

    @Column(name = "is_active")
    var isActive: Boolean = true,

    @Column(name = "created_at", updatable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "update_at")
    var updateAt: OffsetDateTime = OffsetDateTime.now()
) {
    @PreUpdate
    fun preUpdate() {
        updateAt = OffsetDateTime.now()
    }
}

@Entity
@Table(name = "auth", schema = "koalla")
class Auth(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "user_id", unique = true, nullable = false)
    var userId: UUID,

    @Column(name = "magic_link_token", unique = true)
    var magicLinkToken: String? = null,

    @Column(name = "token_expires_at")
    var tokenExpiresAt: OffsetDateTime? = null,

    @Column(name = "last_login")
    var lastLogin: OffsetDateTime? = null,

    @Column(name = "created_at", updatable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now()
)

@Entity
@Table(name = "user_states", schema = "koalla")
class UserState(
    @Id
    @Column(name = "user_id")
    var userId: UUID,

    @Column(name = "current_state", length = 50)
    var currentState: String = "IDLE",

    @Column(columnDefinition = "TEXT")
    var content: String? = null,

    @Column(name = "updated_at")
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
) {
    @PreUpdate
    fun preUpdate() {
        updatedAt = OffsetDateTime.now()
    }
}

