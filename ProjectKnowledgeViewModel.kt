package com.sync.app.ui.screens.knowledge

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sync.app.domain.knowledge.ArchiveUnit
import com.sync.app.domain.knowledge.IndexReport
import com.sync.app.domain.knowledge.KnowledgeFile
import com.sync.app.domain.knowledge.KnowledgeRepository
import com.sync.app.domain.knowledge.KnowledgeStatus
import com.sync.app.domain.knowledge.RepoSource
import com.sync.app.domain.knowledge.SplitArchivePlanner
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Ringkasan yang ditampilkan di dialog agar alasan penolakan terlihat. */
data class ImportSummary(val headline: String, val details: List<String>)

@HiltViewModel
class ProjectKnowledgeViewModel @Inject constructor(
    private val repository: KnowledgeRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val projectId: Long = checkNotNull(savedStateHandle["projectId"])

    val files: StateFlow<List<KnowledgeFile>> = repository.observeFiles(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _summary = MutableStateFlow<ImportSummary?>(null)
    val summary: StateFlow<ImportSummary?> = _summary.asStateFlow()

    private val _stats = MutableStateFlow(0 to 0)
    val stats: StateFlow<Pair<Int, Int>> = _stats.asStateFlow()

    fun refreshStats() {
        viewModelScope.launch { _stats.value = repository.stats(projectId) }
    }

    /**
     * Satu pemilih untuk semuanya: zip utuh, zip terpecah (.z01 / .zip.001),
     * beberapa zip sekaligus, dan berkas biasa. Pengelompokan dilakukan dari nama.
     */
    fun importPicked(uris: List<Uri>, names: List<String>) {
        if (_busy.value || uris.isEmpty()) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val plan = SplitArchivePlanner.plan(names)
                val sources = mutableListOf<RepoSource>()
                if (plan.units.isNotEmpty()) {
                    sources += RepoSource.Archive(
                        plan.units.map { unit -> ArchiveUnit(unit.label, unit.indices.map { uris[it] }) }
                    )
                }
                if (plan.loose.isNotEmpty()) {
                    sources += RepoSource.Tree(plan.loose.map { uris[it] }, plan.loose.map { names[it] })
                }

                var accepted = 0
                var duplicate = 0
                var binary = 0
                var large = 0
                var ignored = 0
                val details = mutableListOf<String>()
                details += plan.problems

                for (source in sources) {
                    val report = repository.importRepo(projectId, source)
                    accepted += report.accepted
                    duplicate += report.skippedDuplicate
                    binary += report.skippedBinary
                    large += report.skippedLarge
                    ignored += report.skippedIgnored
                    details += report.rejected
                }

                val headline = buildString {
                    append("$accepted berkas diterima")
                    if (duplicate > 0) append(" · $duplicate sudah ada")
                    if (binary > 0) append(" · $binary biner dilewati")
                    if (ignored > 0) append(" · $ignored berkas di folder build/.git dst. diabaikan")
                    if (large > 0) append(" · $large terlalu besar")
                    if (details.isNotEmpty()) append(" · ${details.size} catatan")
                }
                _message.value = headline

                val indexReport = repository.indexPending(projectId)
                details += indexReport.errors
                if (details.isNotEmpty()) _summary.value = ImportSummary(headline, details)
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                _message.value = "Impor gagal: memori habis. Coba pecah arsip lebih kecil."
            } catch (e: Exception) {
                _message.value = "Impor gagal: ${e.message}"
            } finally {
                _busy.value = false
                refreshStats()
            }
        }
    }

    fun index() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val report: IndexReport = repository.indexPending(projectId)
                _message.value = "${report.filesIndexed} berkas diindeks, ${report.chunks} potongan" +
                    if (report.filesFailed > 0) ", ${report.filesFailed} gagal" else ""
                if (report.errors.isNotEmpty()) {
                    _summary.value = ImportSummary("Hasil pengindeksan", report.errors)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = "Indeks gagal: ${e.message}"
            } finally {
                _busy.value = false
                refreshStats()
            }
        }
    }

    fun toggle(file: KnowledgeFile) {
        viewModelScope.launch { repository.setEnabled(file.id, file.status == KnowledgeStatus.SKIPPED) }
    }

    fun reindex(file: KnowledgeFile) {
        viewModelScope.launch {
            repository.reindexFile(file.id)
            index()
        }
    }

    fun delete(file: KnowledgeFile) {
        viewModelScope.launch {
            repository.deleteFile(file.id)
            refreshStats()
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            repository.deleteAll(projectId)
            refreshStats()
        }
    }

    fun clearMessage() { _message.value = null }

    fun dismissSummary() { _summary.value = null }
}
