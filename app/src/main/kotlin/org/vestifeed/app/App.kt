package org.vestifeed.app

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.vestifeed.backend.Backend
import org.vestifeed.backend.backend
import org.vestifeed.db.Database
import org.vestifeed.og.OpenGraphImageFetcher
import org.vestifeed.sync.Sync
import java.io.File

class App : Application() {
    val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    val db by lazy {
        Database(
            driver = AndroidSQLiteDriver(),
            path = databaseFile().absolutePath,
        )
    }

    val sync by lazy { Sync(scope, db) }

    val ogFetcher by lazy { OpenGraphImageFetcher(db, this) }

    val api by lazy { backend(db) }

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            try {
                ogFetcher.fetchAndWatch()
            } catch (e: Throwable) {
                Log.e("App", "ogFetcher failed", e)
            }
        }
    }

    fun databaseFile(): File {
        return getDatabasePath("vesti.db")
    }
}

fun Fragment.sync() = requireContext().sync()

fun Context.sync(): Sync = (applicationContext as App).sync

fun Fragment.api() = requireContext().api()

fun Context.api(): Backend = (applicationContext as App).api

fun Fragment.db() = requireContext().db()

fun Context.db(): Database = (applicationContext as App).db