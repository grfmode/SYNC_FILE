package com.sync.app.data.repository

import com.sync.app.data.local.ChatDao
import com.sync.app.data.local.ConversationEntity
import com.sync.app.data.local.MessageEntity
import com.sync.app.data.local.ProjectAttachmentEntity
import com.sync.app.data.local.ProjectEntity
import com.sync.app.data.local.toDomain
import com.sync.app.domain.chat.ChatRepository
import com.sync.app.domain.chat.ConversationSummary
import com.sync.app.domain.chat.MessageSearchResult
import com.sync.app.model.AttachmentUi
import com.sync.app.model.ChatMessage
import com.sync.app.model.Conversation
import com.sync.app.model.MessageRole
import com.sync.app.model.MessageStatus
import com.sync.app.model.Project
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val dao: ChatDao
) : ChatRepository {

    override fun observeProjects(): Flow<List<Project>> =
        dao.observeProjects().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getProjectById(id: Long): Project? =
        dao.getProjectById(id)?.toDomain()

    override suspend fun createProject(name: String): Long {
        return dao.insertProject(ProjectEntity(name = name, createdAt = System.currentTimeMillis()))
    }

    override suspend fun updateProjectName(id: Long, name: String) {
        dao.updateProjectName(id, name)
    }

    override suspend fun updateProjectDetails(id: Long, description: String?, systemPrompt: String?) {
        dao.updateProjectDetails(id, description, systemPrompt)
    }

    override suspend fun updateProjectMemory(id: Long, memoryText: String?) {
        dao.updateProjectMemory(id, memoryText)
    }

    override suspend fun deleteProject(id: Long) {
        dao.deleteProject(id)
    }

    override suspend fun updateConversationProject(conversationId: Long, projectId: Long?) {
        val existing = dao.getConversationById(conversationId) ?: return
        dao.updateConversation(existing.copy(projectId = projectId))
    }

    // === [P-030b] Project Attachments ===

    override fun observeProjectAttachments(projectId: Long): Flow<List<AttachmentUi>> =
        dao.observeProjectAttachments(projectId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getProjectAttachments(projectId: Long): List<AttachmentUi> =
        dao.getProjectAttachments(projectId).map { it.toDomain() }

    override suspend fun addProjectAttachment(projectId: Long, attachment: AttachmentUi): Long {
        val entity = ProjectAttachmentEntity(
            projectId = projectId,
            uri = attachment.uri,
            name = attachment.name,
            mimeType = attachment.mimeType,
            type = attachment.type
        )
        return dao.insertProjectAttachment(entity)
    }

    override suspend fun deleteProjectAttachment(id: Long) {
        dao.deleteProjectAttachment(id)
    }

    override suspend fun deleteProjectAttachmentByUri(projectId: Long, uri: String) {
        dao.deleteProjectAttachmentByUri(projectId, uri)
    }

    // === Conversations ===

    override fun observeConversations(): Flow<List<Conversation>> =
        dao.observeConversations().map { entities -> entities.map { it.toDomain() } }

    override fun observeConversationsByProject(projectId: Long): Flow<List<Conversation>> =
        dao.observeConversationsByProject(projectId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getLatestConversationId(): Long? =
        dao.getLatestConversation()?.id

    override suspend fun getConversationById(id: Long): Conversation? =
        dao.getConversationById(id)?.toDomain()

    override suspend fun createConversation(title: String, projectId: Long?): Long {
        val now = System.currentTimeMillis()
        val entity = ConversationEntity(
            title = title,
            createdAt = now,
            updatedAt = now,
            projectId = projectId
        )
        return dao.insertConversation(entity)
    }

    override suspend fun getMessages(conversationId: Long): List<ChatMessage> =
        dao.getMessagesForConversation(conversationId).map { it.toDomain() }

    override suspend fun addMessage(
        conversationId: Long, role: MessageRole, content: String, status: MessageStatus, attachments: List<AttachmentUi>
    ): Long {
        val attachmentsJson = if (attachments.isNotEmpty()) Json.encodeToString(attachments) else null
        val entity = MessageEntity(
            conversationId = conversationId, role = role, content = content, status = status, attachmentsJson = attachmentsJson, createdAt = System.currentTimeMillis()
        )
        val id = dao.insertMessage(entity)
        dao.getConversationById(conversationId)?.let { conv ->
            dao.updateConversation(conv.copy(updatedAt = System.currentTimeMillis()))
        }
        return id
    }

    override suspend fun updateMessage(id: Long, content: String, status: MessageStatus, attachments: List<AttachmentUi>?) {
        val existing = dao.getMessageById(id) ?: return
        val attachmentsJson = attachments?.let { if (it.isNotEmpty()) Json.encodeToString(it) else null } ?: existing.attachmentsJson
        dao.updateMessage(existing.copy(content = content, status = status, attachmentsJson = attachmentsJson))
    }

    override suspend fun updateConversationTitle(id: Long, title: String) {
        val existing = dao.getConversationById(id) ?: return
        dao.updateConversation(existing.copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun deleteConversation(id: Long) { dao.deleteConversation(id) }

    override suspend fun markStaleAsFailed(): Int = dao.markStaleAsFailed()

    override suspend fun editUserMessageAndTruncate(conversationId: Long, messageId: Long, newContent: String) {
        dao.updateMessageContent(messageId, newContent)
        dao.deleteMessagesAfter(conversationId, messageId)
        dao.getConversationById(conversationId)?.let { conv ->
            dao.updateConversation(conv.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    override suspend fun getConversationSummary(conversationId: Long): ConversationSummary? {
        val row = dao.getConversationSummary(conversationId) ?: return null
        return ConversationSummary(text = row.summaryText, upToMessageId = row.summaryUpToMessageId)
    }

    override suspend fun updateConversationSummary(conversationId: Long, text: String?, upToMessageId: Long?) {
        dao.updateConversationSummary(conversationId, text, upToMessageId)
    }

    override suspend fun searchMessages(query: String): List<MessageSearchResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return dao.searchMessagesInContent(trimmed).map { row ->
            MessageSearchResult(
                conversationId = row.conversationId,
                conversationTitle = row.conversationTitle,
                messageId = row.messageId,
                messageContent = row.messageContent,
                createdAt = row.createdAt,
                projectId = row.projectId
            )
        }
    }

    override suspend fun deleteConversations(ids: List<Long>) {
        if (ids.isNotEmpty()) dao.deleteConversationsByIds(ids)
    }

    override suspend fun moveConversationsToProject(ids: List<Long>, projectId: Long?) {
        if (ids.isNotEmpty()) dao.updateConversationsProject(ids, projectId)
    }
}