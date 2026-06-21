package ai.koalla.core.entity

import jakarta.persistence.*
import java.time.OffsetDateTime
import java.util.UUID

enum class MovementType {
    CASH_IN,
    CASH_OUT
}

enum class EntityContext {
    PF,
    PJ
}

@Entity
@Table(name = "categories", schema = "koalla")
class Category(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,

    @Column(length = 100, nullable = false)
    var name: String,

    @Column(name = "user_id")
    var userId: UUID? = null
)

@Entity
@Table(name = "category_keywords", schema = "koalla")
class CategoryKeyword(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,

    @Column(length = 50, nullable = false)
    var keyword: String,

    @Column(name = "category_id", nullable = false)
    var categoryId: Int
)

@Entity
@Table(name = "transactions", schema = "koalla")
class Transaction(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(nullable = false)
    var amount: Long, // stored in cents

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var movement: MovementType,

    @Column(name = "category_id")
    var categoryId: Int? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    var entityType: EntityContext = EntityContext.PF,

    @Column(length = 20)
    var source: String = "whatsapp",

    @Column(name = "external_id", length = 100)
    var externalId: String? = null,

    @Column(name = "occurred_at")
    var occurredAt: OffsetDateTime? = null,

    @Column(name = "created_at", updatable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at")
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
) {
    @PreUpdate
    fun preUpdate() {
        updatedAt = OffsetDateTime.now()
    }
}

