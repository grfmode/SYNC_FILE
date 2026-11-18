package com.sync.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.sync.app.model.MessageStatus
import kotlinx.coroutines.flow.Flow

data class ConversationSummaryRow(
    val summaryText: String?,
    val summaryUpToMessageId: Long?
)

data class MessageSearchRow(
    val conversationId: Long,
    val conversationTitle: String,
    val messageId: Long,
    val messageContent: String,
    val createdAt: Long,
    val projectId: Long?
)

data class ProjectSnapshotRow(val id: Long, val name: String)

@Dao
interface ChatDao {

    @Insert
    suspend fun insertProject(project: ProjectEntity): Long

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProject(id: Long)

    @Query("UPDATE projects SET name = :name WHERE id = :id")
    suspend fun updateProjectName(id: Long, name: String)

    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT id, name FROM projects ORDER BY createdAt DESC")
    suspend fun observeProjectsSnapshot(): List<ProjectSnapshotRow>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: Long): ProjectEntity?

    @Query("SELECT * FROM projects WHERE memoryText IS NOT NULL AND memoryText != ''")
    suspend fun getProjectsWithMemory(): List<ProjectEntity>

    @Query("UPDATE projects SET description = :description, systemPrompt = :systemPrompt WHERE id = :id")
    suspend fun updateProjectDetails(id: Long, description: String?, systemPrompt: String?)

    @Query("UPDATE projects SET memoryText = :memoryText WHERE id = :id")
    suspend fun updateProjectMemory(id: Long, memoryText: String?)

    @Insert
    suspend fun insertProjectAttachment(attachment: ProjectAttachmentEntity): Long

    @Query("DELETE FROM project_attachments WHERE id = :id")
    suspend fun deleteProjectAttachment(id: Long)

    @Query("DELETE FROM project_attachments WHERE projectId = :projectId AND uri = :uri")
    suspend fun deleteProjectAttachmentByUri(projectId: Long, uri: String)

    @Query("SELECT * FROM project_attachments WHERE projectId = :projectId ORDER BY createdAt ASC")
    fun observeProjectAttachments(projectId: Long): Flow<List<ProjectAttachmentEntity>>

    @Query("SELECT * FROM project_attachments WHERE projectId = :projectId ORDER BY createdAt ASC")
    suspend fun getProjectAttachments(projectId: Long): List<ProjectAttachmentEntity>

    @Insert
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: Long)

    @Query("SELECT * FROM conversations WHERE projectId IS NULL ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE projectId = :projectId ORDER BY updatedAt DESC")
    fun observeConversationsByProject(projectId: Long): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestConversation(): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: Long): ConversationEntity?

    @Query("DELETE FROM conversations WHERE id IN (:ids)")
    suspend fun deleteConversationsByIds(ids: List<Long>)

    @Query("UPDATE conversations SET projectId = :projectId WHERE id IN (:ids)")
    suspend fun updateConversationsProject(ids: List<Long>, projectId: Long?)

    @Insert
    suspend fun insertMessage(message: MessageEntity): Long

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    suspend fun getMessagesForConversation(conversationId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessageById(id: Long): MessageEntity?

    @Query("UPDATE messages SET status = 'Failed' WHERE status = 'Sending'")
    suspend fun markStaleAsFailed(): Int

    @Query("UPDATE messages SET content = :content WHERE id = :id")
    suspend fun updateMessageContent(id: Long, content: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND id > :messageId")
    suspend fun deleteMessagesAfter(conversationId: Long, messageId: Long)

    @Query(
        """
        SELECT m.conversationId AS conversationId,
               c.title AS conversationTitle,
               m.id AS messageId,
               m.content AS messageContent,
               m.createdAt AS createdAt,
               c.projectId AS projectId
        FROM messages m
        INNER JOIN conversations c ON c.id = m.conversationId
        WHERE m.content LIKE '%' || :query || '%'
        ORDER BY m.createdAt DESC
        LIMIT 100
        """
    )
    suspend fun searchMessagesInContent(query: String): List<MessageSearchRow>

    @Query("SELECT summaryText, summaryUpToMessageId FROM conversations WHERE id = :id")
    suspend fun getConversationSummary(id: Long): ConversationSummaryRow?

    @Query("UPDATE conversations SET summaryText = :summaryText, summaryUpToMessageId = :summaryUpToMessageId WHERE id = :id")
    suspend fun updateConversationSummary(id: Long, summaryText: String?, summaryUpToMessageId: Long?)
}