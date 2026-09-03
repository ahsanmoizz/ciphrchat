package org.ciphrchat.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "groups",
    indices = [
        Index(value = ["groupId"], unique = true),
        Index(value = ["updatedAtEpochMs"])
    ]
)
data class GroupEntity(
    @PrimaryKey
    val groupId: String,
    val name: String,
    val creatorPublicId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val isActive: Boolean = true
)

@Entity(
    tableName = "group_members",
    primaryKeys = ["groupId", "memberPublicId"],
    indices = [
        Index(value = ["groupId"]),
        Index(value = ["memberPublicId"])
    ]
)
data class GroupMemberEntity(
    val groupId: String,
    val memberPublicId: String,
    val joinedAtEpochMs: Long,
    val membershipState: String = "ACTIVE" // "ACTIVE", "LEFT"
)
