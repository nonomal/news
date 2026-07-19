package org.vestifeed.sync

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestWorkerBuilder
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.vestifeed.app.App

class SyncWorkerTest {

    private lateinit var app: App

    @Before
    fun setup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        app = instrumentation.targetContext.applicationContext as App
        app.db.conf.update { it.copy(backend = null) }
    }

    @Test
    fun failureWithoutBackend() {
        val workerBuilder = TestWorkerBuilder.from(app, SyncWorker::class.java)
        val worker = workerBuilder.build()
        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Failure)
    }
}
