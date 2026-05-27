package org.vestifeed.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.vestifeed.api.Api
import org.vestifeed.api.miniflux.MinifluxImpl
import org.vestifeed.api.miniflux.MinifluxSync
import org.vestifeed.api.miniflux.minifluxHttpClient
import org.vestifeed.db.Database
import org.vestifeed.db.table.ConfTable

class Sync(
    private val scope: CoroutineScope,
    private val legacyApi: Api,
    private val db: Database,
) {
    sealed class State {
        data class Idle(val error: Throwable? = null) : State()
        object Starting : State()
        data class InitialSync(val stage: InitialSyncStage) : State()
        data class FollowUpSync(val args: Args, val stage: FollowUpSyncStage) : State()
    }

    sealed class InitialSyncStage {
        object SyncingFeeds : InitialSyncStage()
        data class SyncingEntries(val entriesSynced: Long) : InitialSyncStage()
    }

    sealed class FollowUpSyncStage {
        object SyncingFeeds : FollowUpSyncStage()
        object SyncingFlags : FollowUpSyncStage()
        object SyncingEntries : FollowUpSyncStage()
    }

    private val _state = MutableStateFlow<State>(State.Idle())
    val state = _state.asStateFlow()

    data class Args(
        val syncFeeds: Boolean = true,
        val syncFlags: Boolean = true,
        val syncEntries: Boolean = true,
    )

    fun runInBackground(args: Args = Args()) {
        scope.launch { run(args) }
    }

    suspend fun runInForeground(args: Args = Args()) {
        run(args)
    }

    private suspend fun run(args: Args = Args()) {
        while (_state.value !is State.Idle) {
            delay(100)
        }

        _state.update { State.Starting }

        val conf = withContext(Dispatchers.IO) { db.conf.select() }

        when (conf.backend) {
            ConfTable.Backend.Miniflux -> {
                val minifluxUrl = conf.minifluxUrl
                val minifluxToken = conf.minifluxToken

                if (minifluxUrl == null || minifluxToken == null) {
                    _state.update { State.Idle(IllegalStateException("Miniflux credentials are not set")) }
                    return
                }

                val api = MinifluxImpl(
                    client = minifluxHttpClient(token = minifluxToken),
                    baseUrl = "$minifluxUrl/v1/".toHttpUrl(),
                )

                val sync = MinifluxSync(db, api)

                if (conf.minifluxInitialSyncCompleted) {
                    if (args.syncFeeds) {
                        _state.update {
                            State.FollowUpSync(args, FollowUpSyncStage.SyncingFeeds)
                        }

                        try {
                            sync.syncFeeds()
                        } catch (e: Throwable) {
                            _state.update { State.Idle(e) }
                            return
                        }
                    }

                    if (args.syncFlags) {
                        _state.update { State.FollowUpSync(args, FollowUpSyncStage.SyncingFlags) }

                        try {
                            val unsyncedEntries =
                                withContext(Dispatchers.IO) { db.entry.selectByReadSynced(false) }
                            val unsyncedReadEntries = unsyncedEntries.filter { it.extRead }
                            val unsyncedUnreadEntries = unsyncedEntries.filter { !it.extRead }

                            if (unsyncedReadEntries.isNotEmpty()) {
                                legacyApi.markEntriesAsRead(
                                    entriesIds = unsyncedReadEntries.map { it.id },
                                    read = true,
                                )

                                withContext(Dispatchers.IO) {
                                    db.transaction {
                                        unsyncedReadEntries.forEach {
                                            db.entry.updateReadSynced(true, it.id)
                                        }
                                    }
                                }
                            }

                            if (unsyncedUnreadEntries.isNotEmpty()) {
                                legacyApi.markEntriesAsRead(
                                    entriesIds = unsyncedUnreadEntries.map { it.id },
                                    read = false,
                                )

                                withContext(Dispatchers.IO) {
                                    db.transaction {
                                        unsyncedUnreadEntries.forEach {
                                            db.entry.updateReadSynced(true, it.id)
                                        }
                                    }
                                }
                            }
                        } catch (e: Throwable) {
                            _state.update { State.Idle(e) }
                            return
                        }

                        try {
                            val notSyncedEntries =
                                withContext(Dispatchers.IO) {
                                    db.entry.selectByBookmarkedSynced(
                                        false
                                    )
                                }
                            val notSyncedBookmarkedEntries =
                                notSyncedEntries.filter { it.extBookmarked }
                            val notSyncedNotBookmarkedEntries =
                                notSyncedEntries.filterNot { it.extBookmarked }

                            if (notSyncedBookmarkedEntries.isNotEmpty()) {
                                legacyApi.markEntriesAsBookmarked(notSyncedBookmarkedEntries, true)

                                withContext(Dispatchers.IO) {
                                    db.transaction {
                                        notSyncedBookmarkedEntries.forEach {
                                            db.entry.updateBookmarkedSynced(true, it.id)
                                        }
                                    }
                                }
                            }

                            if (notSyncedNotBookmarkedEntries.isNotEmpty()) {
                                legacyApi.markEntriesAsBookmarked(notSyncedNotBookmarkedEntries, false)

                                withContext(Dispatchers.IO) {
                                    db.transaction {
                                        notSyncedNotBookmarkedEntries.forEach {
                                            db.entry.updateBookmarkedSynced(true, it.id)
                                        }
                                    }
                                }
                            }
                        } catch (e: Throwable) {
                            _state.update { State.Idle(e) }
                            return
                        }
                    }

                    if (args.syncEntries) {
                        _state.update {
                            State.FollowUpSync(args, FollowUpSyncStage.SyncingEntries)
                        }

                        try {
                            sync.incrementalSync()
                        } catch (e: Throwable) {
                            _state.update { State.Idle(e) }
                            return
                        }
                    }
                } else {
                    try {
                        _state.update { State.InitialSync(InitialSyncStage.SyncingFeeds) }
                        sync.syncFeeds()
                        sync.syncUnreadEntries()
                        sync.syncStarredEntries()
                        withContext(Dispatchers.IO) {
                            db.conf.update {
                                it.copy(
                                    minifluxInitialSyncCompleted = true,
                                )
                            }
                        }
                    } catch (e: Throwable) {
                        _state.update { State.Idle(e) }
                        return
                    }
                }

                _state.update { State.Idle() }
            }

            ConfTable.Backend.Embedded -> {
                _state.update { State.Idle() }
            }

            null -> {
                _state.update { State.Idle(IllegalStateException("backend is not set")) }
            }
        }
    }
}