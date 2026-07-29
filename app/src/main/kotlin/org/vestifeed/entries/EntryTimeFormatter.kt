package org.vestifeed.entries

import android.content.res.Resources
import org.vestifeed.R
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Formats the published timestamp of an entry for the entries list subtitle.
 *
 * Anything within the last 24 hours is rendered as a friendly relative phrase
 * ("Just now", "5 mins ago", "3 hours ago"). Older entries fall back to the
 * locale-aware strict date/time format previously used by the list, so the
 * "older than 24h" branch always produces the same string the user used to
 * see before this change.
 *
 * The bucket decision lives in [RelativeTimeCalculator] so the rules can be
 * unit-tested without Android resources. This object only owns the Android
 * side (resource lookup + strict-format fallback).
 */
object EntryTimeFormatter {

    private val STRICT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(
        FormatStyle.MEDIUM,
        FormatStyle.SHORT,
    )

    fun format(
        now: OffsetDateTime,
        published: OffsetDateTime,
        resources: Resources,
    ): String {
        return when (val relative = RelativeTimeCalculator.compute(now, published)) {
            RelativeTime.JustNow -> resources.getString(R.string.time_just_now)
            is RelativeTime.MinutesAgo -> resources.getQuantityString(
                R.plurals.time_minutes_ago,
                relative.count,
                relative.count,
            )
            is RelativeTime.HoursAgo -> resources.getQuantityString(
                R.plurals.time_hours_ago,
                relative.count,
                relative.count,
            )
            RelativeTime.OlderThanDay -> STRICT_FORMAT.format(published)
        }
    }
}

/**
 * Discrete bucket that [EntryTimeFormatter] renders. The bucket itself is
 * locale-agnostic — translation happens at the formatter layer.
 */
sealed class RelativeTime {
    data object JustNow : RelativeTime()
    data class MinutesAgo(val count: Int) : RelativeTime()
    data class HoursAgo(val count: Int) : RelativeTime()
    data object OlderThanDay : RelativeTime()
}

/**
 * Pure bucketing rules. Kept separate from [EntryTimeFormatter] so the
 * time-arithmetic can be exercised by JUnit tests without touching
 * Android resources.
 */
object RelativeTimeCalculator {

    /**
     * Bucket boundaries:
     * - diff in `(-∞, 1m)`     -> [RelativeTime.JustNow] (also any future-dated entry)
     * - diff in `[1m, 1h)`     -> [RelativeTime.MinutesAgo]
     * - diff in `[1h, 24h)`    -> [RelativeTime.HoursAgo]
     * - diff in `[24h, +∞)`    -> [RelativeTime.OlderThanDay]
     */
    fun compute(now: OffsetDateTime, published: OffsetDateTime): RelativeTime {
        val duration = Duration.between(published, now)
        return when {
            duration < ONE_MINUTE -> RelativeTime.JustNow
            duration < ONE_HOUR -> RelativeTime.MinutesAgo(duration.toMinutes().toInt())
            duration < ONE_DAY -> RelativeTime.HoursAgo(duration.toHours().toInt())
            else -> RelativeTime.OlderThanDay
        }
    }

    private val ONE_MINUTE: Duration = Duration.ofMinutes(1)
    private val ONE_HOUR: Duration = Duration.ofHours(1)
    private val ONE_DAY: Duration = Duration.ofHours(24)
}