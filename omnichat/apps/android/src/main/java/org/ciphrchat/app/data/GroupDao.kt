package org.ciphrchat.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups WHERE groupId = :groupId LIMIT 1")
    suspend fun findGroupById(groupId: String): GroupEntity?

    @Query("SELECT * FROM groups WHERE groupId = :groupId LIMIT 1")
    fun observeGroupById(groupId: String): Flow<GroupEntity?>

    @Query("SELECT * FROM groups ORDER BY updatedAtEpochMs DESC")
    fun observeAllGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE isActive = 1 ORDER BY updatedAtEpochMs DESC")
    fun observeActiveGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE isActive = 1 ORDER BY updatedAtEpochMs DESC")
    suspend fun getActiveGroups(): List<GroupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)

    @Update
    suspend fun updateGroup(group: GroupEntity)

    @Query("DELETE FROM groups WHERE groupId = :groupId")
    suspend fun deleteGroup(groupId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: GroupMemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<GroupMemberEntity>)

    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    suspend fun getMembers(groupId: String): List<GroupMemberEntity>

    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    fun observeMembers(groupId: String): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM group_members WHERE groupId = :groupId AND membershipState = 'ACTIVE'")
    suspend fun getActiveMembers(groupId: String): List<GroupMemberEntity>

    @Query("UPDATE group_members SET membershipState = :state WHERE groupId = :groupId AND memberPublicId = :memberPublicId")
    suspend fun updateMemberState(groupId: String, memberPublicId: String, state: String): Int

    @Query("DELETE FROM group_members WHERE groupId = :groupId")
    suspend fun deleteMembersForGroup(groupId: String): Int
}
