package org.ciphrchat.app.groups

import kotlinx.coroutines.flow.Flow
import org.ciphrchat.app.data.AppDatabase
import org.ciphrchat.app.data.GroupDao
import org.ciphrchat.app.data.GroupEntity
import org.ciphrchat.app.data.GroupMemberEntity
import org.ciphrchat.app.identity.ContactRepository
import org.ciphrchat.app.identity.IdentityRepository
import org.ciphrchat.app.messaging.MessageContentCodec
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupManager @Inject constructor(
    private val database: AppDatabase,
    private val contacts: ContactRepository,
    private val identity: IdentityRepository
) {
    val groupDao: GroupDao = database.groupDao()

    fun observeAllGroups(): Flow<List<GroupEntity>> = groupDao.observeAllGroups()

    fun observeActiveGroups(): Flow<List<GroupEntity>> = groupDao.observeActiveGroups()

    fun observeGroup(groupId: String): Flow<GroupEntity?> = groupDao.observeGroupById(groupId)

    fun observeMembers(groupId: String): Flow<List<GroupMemberEntity>> = groupDao.observeMembers(groupId)

    suspend fun getGroup(groupId: String): GroupEntity? = groupDao.findGroupById(groupId)

    suspend fun getMembers(groupId: String): List<GroupMemberEntity> = groupDao.getMembers(groupId)

    suspend fun getActiveMembers(groupId: String): List<GroupMemberEntity> = groupDao.getActiveMembers(groupId)

    suspend fun isGroup(conversationId: String): Boolean {
        if (conversationId.startsWith("group_")) return true
        return groupDao.findGroupById(conversationId) != null
    }

    suspend fun createGroup(
        name: String,
        selectedContactIds: List<String>
    ): Result<Pair<GroupEntity, MessageContentCodec.GroupInvitePayload>> {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("Group name cannot be blank"))
        }
        if (trimmedName.length > 100) {
            return Result.failure(IllegalArgumentException("Group name cannot exceed 100 characters"))
        }
        val distinctContactIds = selectedContactIds.distinct()
        if (distinctContactIds.isEmpty()) {
            return Result.failure(IllegalArgumentException("At least one contact must be selected"))
        }
        // Validate all contacts exist in paired contacts
        for (contactId in distinctContactIds) {
            val contact = contacts.find(contactId)
            if (contact == null) {
                return Result.failure(IllegalArgumentException("Selected contact is not paired: $contactId"))
            }
        }

        val localId = identity.current()?.publicId ?: return Result.failure(IllegalStateException("Local identity is unavailable"))
        val groupId = "group_${UUID.randomUUID()}"
        val now = System.currentTimeMillis()

        val group = GroupEntity(
            groupId = groupId,
            name = trimmedName,
            creatorPublicId = localId,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            isActive = true
        )
        groupDao.insertGroup(group)

        // Insert local user as active member
        val localMember = GroupMemberEntity(
            groupId = groupId,
            memberPublicId = localId,
            joinedAtEpochMs = now,
            membershipState = "ACTIVE"
        )
        groupDao.insertMember(localMember)

        // Insert selected contacts
        val memberEntities = distinctContactIds.map { contactId ->
            GroupMemberEntity(
                groupId = groupId,
                memberPublicId = contactId,
                joinedAtEpochMs = now,
                membershipState = "ACTIVE"
            )
        }
        groupDao.insertMembers(memberEntities)

        val allMemberIds = listOf(localId) + distinctContactIds
        val invitePayload = MessageContentCodec.GroupInvitePayload(
            groupId = groupId,
            groupName = trimmedName,
            creatorId = localId,
            memberIds = allMemberIds,
            createdAtEpochMs = now
        )

        return Result.success(Pair(group, invitePayload))
    }

    suspend fun leaveGroup(groupId: String): Result<MessageContentCodec.GroupLeavePayload> {
        val group = groupDao.findGroupById(groupId)
            ?: return Result.failure(IllegalStateException("Group does not exist: $groupId"))

        val localId = identity.current()?.publicId ?: return Result.failure(IllegalStateException("Local identity is unavailable"))
        val now = System.currentTimeMillis()

        groupDao.updateGroup(group.copy(isActive = false, updatedAtEpochMs = now))
        groupDao.updateMemberState(groupId, localId, "LEFT")

        val leavePayload = MessageContentCodec.GroupLeavePayload(
            groupId = groupId,
            memberId = localId,
            createdAtEpochMs = now
        )
        return Result.success(leavePayload)
    }

    suspend fun handleIncomingInvite(invite: MessageContentCodec.GroupInvitePayload, senderId: String): Boolean {
        // Authenticate: sender must match creator
        if (senderId != invite.creatorId) return false
        val localId = identity.current()?.publicId ?: return false
        // Authenticate: local user must be included in memberIds
        if (!invite.memberIds.contains(localId)) return false
        if (invite.groupId.isBlank() || invite.groupName.isBlank()) return false

        val existing = groupDao.findGroupById(invite.groupId)
        if (existing != null) {
            // Already know about this group, reactivate if previously left
            if (!existing.isActive) {
                groupDao.updateGroup(existing.copy(isActive = true, updatedAtEpochMs = System.currentTimeMillis()))
                groupDao.updateMemberState(invite.groupId, localId, "ACTIVE")
            }
            return true
        }

        val now = System.currentTimeMillis()
        val group = GroupEntity(
            groupId = invite.groupId,
            name = invite.groupName,
            creatorPublicId = invite.creatorId,
            createdAtEpochMs = invite.createdAtEpochMs,
            updatedAtEpochMs = now,
            isActive = true
        )
        groupDao.insertGroup(group)

        val memberEntities = invite.memberIds.distinct().map { memberId ->
            GroupMemberEntity(
                groupId = invite.groupId,
                memberPublicId = memberId,
                joinedAtEpochMs = now,
                membershipState = "ACTIVE"
            )
        }
        groupDao.insertMembers(memberEntities)
        return true
    }

    suspend fun handleIncomingLeave(leave: MessageContentCodec.GroupLeavePayload, senderId: String): Boolean {
        // Authenticate: sender must match the member leaving
        if (senderId != leave.memberId) return false
        val group = groupDao.findGroupById(leave.groupId) ?: return false
        groupDao.updateMemberState(leave.groupId, leave.memberId, "LEFT")
        groupDao.updateGroup(group.copy(updatedAtEpochMs = System.currentTimeMillis()))
        return true
    }
}
