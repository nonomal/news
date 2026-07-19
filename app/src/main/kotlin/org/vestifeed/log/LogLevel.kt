package org.vestifeed.log

enum class LogLevel(
    val value: String,
    val priority: Int,
) {
    TRACE("trace", 0),
    DEBUG("debug", 1),
    INFO("info", 2),
    WARN("warn", 3),
    ERROR("error", 4);

    companion object {
        fun from(value: String): LogLevel? = entries.firstOrNull { it.value == value }
    }
}