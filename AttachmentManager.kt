package com.sync.app.domain.chat

import android.net.Uri
import com.sync.app.domain.provider.AttachmentPayload
import com.sync.app.domain.provider.GeneratedImage
import com.sync.app.model.AttachmentUi

/**
 * Satu hasil persiapan lampiran.
 *
 * Kegagalan satu lampiran tidak menggagalkan request; pemanggil membuang yang
 * gagal dan mencatat alasannya.
 */
data class PreparedAttachment(
    val ui: AttachmentUi,
    val payload: AttachmentPayload?,
    val error: String?
) {
    val ok: Boolean get() = payload != null
}

interface AttachmentManager {

    suspend fun loadAttachment(uri: Uri): Result<AttachmentUi>

    /** Selalu mengembalikan satu entri per lampiran, urutan tetap. */
    suspend fun preparePayloads(attachments: List<AttachmentUi>): List<PreparedAttachment>

    suspend fun storeGeneratedImage(image: GeneratedImage): AttachmentUi?
}