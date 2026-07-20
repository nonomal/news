package org.vestifeed.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object AuthEvents {
    private val _invalidationCount = MutableStateFlow(0L)
    val invalidationCount: StateFlow<Long> = _invalidationCount.asStateFlow()

    fun reportInvalidated() {
        _invalidationCount.update { it + 1 }
    }

    fun reset() {
        _invalidationCount.value = 0L
    }
}
