package org.vestifeed.podcasts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vestifeed.db.Database
import org.vestifeed.enclosures.EnclosuresRepo
import org.vestifeed.sync.Sync
import java.time.OffsetDateTime

/**
 * Owns all data loading, mutation and intent routing for the Podcasts screen.
 * The fragment is a thin renderer that collects [state] and forwards user
 * intents.
 *
 * IO is always dispatched to [Dispatchers.IO]; the only thing the VM emits
 * to the main thread are immutable state objects and one-shot actions.
 */
class PodcastsViewModel(
    private val db: Database,
    private val sync: Sync,
    private val enclosuresRepo: EnclosuresRepo,
) : ViewModel() {

    private val _state = MutableStateFlow(PodcastsScreenState())
    val state: StateFlow<PodcastsScreenState> = _state.asStateFlow()

    private val _actions = MutableSharedFlow<PodcastsAction>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val actions: SharedFlow<PodcastsAction> = _actions.asSharedFlow()

    init {
        // Reload whenever a sync finishes so newly-downloaded entries show
        // up in the list.
        viewModelScope.launch {
            sync.running.collect { running ->
                if (!running) {
                    reloadInternal()
                }
            }
        }

        // Initial load.
        viewModelScope.launch { reloadInternal() }
    }

    /** Re-read every audio enclosure and re-emit state. */
    fun reload() {
        viewModelScope.launch { reloadInternal() }
    }

    private suspend fun reloadInternal() {
        val rows = withContext(Dispatchers.IO) { db.link.selectAudioEnclosureRows() }
        val now = OffsetDateTime.now()
        val items = rows.map { row ->
            PodcastsAdapter.Item(
                id = "${row.entryId}|${row.linkId}",
                entryId = row.entryId,
                linkId = row.linkId,
                href = row.href,
                type = row.type,
                primaryText = row.entryTitle,
                secondaryText = joinSubtitle(
                    feedTitle = row.feedTitle,
                    published = row.entryPublished,
                    now = now,
                ),
                downloadProgress = row.extEnclosureDownloadProgress,
                cacheUri = row.extCacheUri,
                entryRead = row.extRead,
                played = row.extPlayed,
                bookmarked = row.extBookmarked,
            )
        }

        _state.update { it.copy(items = items) }
    }

    /**
     * Persist the read flag on the entry backing the row, then reload so
     * the row's read state and swipe-target state both reflect the change.
     */
    fun setRead(entryId: String, read: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.entry.updateReadAndReadSynced(
                    id = entryId,
                    extRead = read,
                    extReadSynced = false,
                )
            }
            reloadInternal()
            sync.runInBackground()
        }
    }

    /** Persist the bookmark flag on the entry backing the row. */
    fun setBookmarked(entryId: String, bookmarked: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.entry.updateBookmarkedAndBookmarkedSynced(
                    id = entryId,
                    extBookmarked = bookmarked,
                    extBookmarkedSynced = false,
                )
            }
            reloadInternal()
            sync.runInBackground()
        }
    }

    /**
     * Mark the link backing [item] as played (or clear it) and stamp the
     * timestamp. Triggered when the user actually starts playback; the
     * entry's read state is left untouched so it still surfaces on the
     * Unread tab until the user opens it or swipes it.
     */
    fun setPlayed(item: PodcastsAdapter.Item, played: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.link.updatePlayedAndPlayedAt(
                    linkId = item.linkId,
                    played = played,
                    playedAt = if (played) OffsetDateTime.now() else null,
                )
            }
            reloadInternal()
        }
    }

    /**
     * Trigger a download of [item]'s audio. Progress propagates back through
     * the enclosure's `ext_enclosure_download_progress`, so reloading the
     * list after the download settles is enough to surface the new state.
     */
    fun downloadAudio(item: PodcastsAdapter.Item) {
        viewModelScope.launch {
            val link = lookupLink(item)
            if (link == null) {
                _actions.tryEmit(PodcastsAction.ShowError("Could not find link"))
                return@launch
            }
            runCatching { enclosuresRepo.downloadAudioEnclosure(link) }
                .onFailure { _actions.tryEmit(PodcastsAction.ShowError(it.message ?: "")) }
            reloadInternal()
        }
    }

    /**
     * Hand [item]'s downloaded audio off to an external player via
     * ACTION_VIEW. Uses the cached file URI when available so the player
     * can resume / seek into the file. Also flips the per-enclosure
     * played flag, since this is the only signal we get that the user
     * engaged with the audio (external players don't report completion).
     */
    fun playAudio(item: PodcastsAdapter.Item) {
        val uri = item.cacheUri
        if (uri.isNullOrBlank()) {
            _actions.tryEmit(PodcastsAction.ShowError("Not downloaded yet"))
            return
        }
        if (!item.played) {
            setPlayed(item, played = true)
        }
        _actions.tryEmit(PodcastsAction.PlayAudio(uri, item.type))
    }

    /** Drop the cache file backing [item] and clear the link's progress. */
    fun deleteAudio(item: PodcastsAdapter.Item) {
        viewModelScope.launch {
            val link = lookupLink(item)
            if (link == null) {
                reloadInternal()
                return@launch
            }
            runCatching { enclosuresRepo.deleteFromCache(link) }
                .onFailure { _actions.tryEmit(PodcastsAction.ShowError(it.message ?: "")) }
            reloadInternal()
        }
    }

    private suspend fun lookupLink(item: PodcastsAdapter.Item): org.vestifeed.db.table.LinkTable.Link? {
        return withContext(Dispatchers.IO) {
            db.link.selectById(item.linkId)
        }
    }

    private fun joinSubtitle(
        feedTitle: String,
        published: OffsetDateTime,
        now: OffsetDateTime,
    ): String {
        return "$feedTitle · ${formatRelativeTime(now, published)}"
    }

    private fun formatRelativeTime(now: OffsetDateTime, then: OffsetDateTime): String {
        val seconds = java.time.Duration.between(then, now).seconds
        return when {
            seconds < 60 -> "just now"
            seconds < 3600 -> "${seconds / 60} min ago"
            seconds < 86_400 -> "${seconds / 3600} hours ago"
            seconds < 604_800 -> "${seconds / 86_400} days ago"
            else -> then.toLocalDate().toString()
        }
    }
}

sealed class PodcastsAction {
    data class ShowError(val message: String) : PodcastsAction()
    data class PlayAudio(val uri: String, val mimeType: String) : PodcastsAction()
}

/**
 * Constructs a [PodcastsViewModel] with its dependencies pulled from the
 * application context.
 */
class PodcastsViewModelFactory(
    private val db: Database,
    private val sync: Sync,
    private val enclosuresRepo: EnclosuresRepo,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == PodcastsViewModel::class.java) {
            "Unknown ViewModel class ${modelClass.name}"
        }
        return PodcastsViewModel(db, sync, enclosuresRepo) as T
    }
}
