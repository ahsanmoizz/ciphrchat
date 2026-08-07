package org.ciphrchat.app.identity

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.ciphrchat.app.data.AppDatabase
import org.ciphrchat.app.data.ContactEntity
import javax.inject.Inject
import javax.inject.Singleton

interface ContactRepository {
    fun observe(): Flow<List<ContactEntity>>
    suspend fun find(contactId: String): ContactEntity?
    suspend fun findByPeerId(peerId: String): ContactEntity?
    suspend fun save(contact: ContactEntity)
}

@Singleton
class PersistentContactRepository @Inject constructor(
    private val database: AppDatabase
) : ContactRepository {
    private val dao = database.contactDao()

    override fun observe(): Flow<List<ContactEntity>> = dao.observeAll()
    override suspend fun find(contactId: String): ContactEntity? = withContext(Dispatchers.IO) { dao.find(contactId) }
    override suspend fun findByPeerId(peerId: String): ContactEntity? = withContext(Dispatchers.IO) { dao.findByPeerId(peerId) }
    override suspend fun save(contact: ContactEntity) = withContext(Dispatchers.IO) { dao.save(contact) }
}
