package com.sync.app.domain.chat

import com.sync.app.model.AttachmentUi
import com.sync.app.model.ChatMessage
import com.sync.app.model.Conversation
import com.sync.app.model.MessageRole
import com.sync.app.model.MessageStatus
import com.sync.app.model.Project
import kotlinx.coroutines.flow.Flow

data class ConversationSummary(
    val text: String?,
    val upToMessageId: Long?
)

data class MessageSearchResult(
    val conversationId: Long,
    val conversationTitle: String,
    val messageId: Long,
    val messageContent: String,
    val createdAt: Long,
    val projectId: Long? = null
)

interface ChatRepository {
    // Projects
    fun observeProjects(): Flow<List<Project>>
    suspend fun getProjectById(id: Long): Project?
    suspend fun createProject(name: String): Long
    suspend fun updateProjectName(id: Long, name: String)
    suspend fun updateProjectDetails(id: Long, description: String?, systemPrompt: String?)
    suspend fun updateProjectMemory(id: Long, memoryText: String?) // [P-030c]
    suspend fun deleteProject(id: Long)
    suspend fun updateConversationProject(conversationId: Long, projectId: Long?)

    // Project Attachments
    fun observeProjectAttachments(projectId: Long): Flow<List<AttachmentUi>>
    suspend fun getProjectAttachments(projectId: Long): List<AttachmentUi>
    suspend fun addProjectAttachment(projectId: Long, attachment: AttachmentUi): Long
    suspend fun deleteProjectAttachment(id: Long)
    suspend fun deleteProjectAttachmentByUri(projectId: Long, uri: String)

    // Conversations
    fun observeConversations(): Flow<List<Conversation>>
    fun observeConversationsByProject(projectId: Long): Flow<List<Conversation>>
    suspend fun getLatestConversationId(): Long?
    suspend fun getConversationById(id: Long): Conversation?
    suspend fun createConversation(title: String, projectId: Long? = null): Long
    suspend fun getMessages(conversationId: Long): List<ChatMessage>

    suspend fun addMessage(
        conversationId: Long,
        role: MessageRole,
        content: String,
        status: MessageStatus,
        attachments: List<AttachmentUi> = emptyList()
    ): Long

    suspend fun updateMessage(id: Long, content: String, status: MessageStatus, attachments: List<AttachmentUi>? = null)
    suspend fun updateConversationTitle(id: Long, title: String)
    suspend fun deleteConversation(id: Long)
    suspend fun markStaleAsFailed(): Int
    suspend fun editUserMessageAndTruncate(conversationId: Long, messageId: Long, newContent: String)

    // Summary
    suspend fun getConversationSummary(conversationId: Long): ConversationSummary?
    suspend fun updateConversationSummary(conversationId: Long, text: String?, upToMessageId: Long?)

    // Search & Bulk
    suspend fun searchMessages(query: String): List<MessageSearchResult>
    suspend fun deleteConversations(ids: List<Long>)
    suspend fun moveConversationsToProject(ids: List<Long>, projectId: Long?)
}