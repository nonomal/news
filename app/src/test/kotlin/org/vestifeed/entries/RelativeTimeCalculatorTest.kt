package org.vestifeed.entries

import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeCalculatorTest {

    private val now: OffsetDateTime = OffsetDateTime.of(2026, 7, 29, 12, 0, 0, 0, ZoneOffset.UTC)

    @Test
    fun futurePublishedIsJustNow() {
        val published = now.plusSeconds(30)
        assertEquals(RelativeTime.JustNow, RelativeTimeCalculator.compute(now, published))
    }

    @Test
    fun sameInstantIsJustNow() {
        assertEquals(RelativeTime.JustNow, RelativeTimeCalculator.compute(now, now))
    }

    @Test
    fun underOneMinuteIsJustNow() {
        val published = now.minusSeconds(45)
        assertEquals(RelativeTime.JustNow, RelativeTimeCalculator.compute(now, published))
    }

    @Test
    fun exactlyOneMinuteIsMinutesAgo() {
        val published = now.minus(Duration.ofMinutes(1))
        assertEquals(RelativeTime.MinutesAgo(1), RelativeTimeCalculator.compute(now, published))
    }

    @Test
    fun fifteenMinutesIsMinutesAgo() {
        val published = now.minus(Duration.ofMinutes(15))
        assertEquals(RelativeTime.MinutesAgo(15), RelativeTimeCalculator.compute(now, published))
    }

    @Test
    fun fiftyNineMinutesIsMinutesAgo() {
        val published = now.minus(Duration.ofMinutes(59))
        assertEquals(RelativeTime.MinutesAgo(59), RelativeTimeCalculator.compute(now, published))
    }

    @Test
    fun exactlyOneHourIsHoursAgo() {
        val published = now.minus(Duration.ofHours(1))
        assertEquals(RelativeTime.HoursAgo(1), RelativeTimeCalculator.compute(now, published))
    }

    @Test
    fun fiveHoursIsHoursAgo() {
        val published = now.minus(Duration.ofHours(5))
        assertEquals(RelativeTime.HoursAgo(5), RelativeTimeCalculator.compute(now, published))
    }

    @Test
    fun twentyThreeHoursIsHoursAgo() {
        val published = now.minus(Duration.ofHours(23))
        assertEquals(RelativeTime.HoursAgo(23), RelativeTimeCalculator.compute(now, published))
    }

    @Test
    fun exactlyTwentyFourHoursIsOlderThanDay() {
        val published = now.minus(Duration.ofHours(24))
        assertEquals(RelativeTime.OlderThanDay, RelativeTimeCalculator.compute(now, published))
    }

    @Test
    fun threeDaysIsOlderThanDay() {
        val published = now.minus(Duration.ofDays(3))
        assertEquals(RelativeTime.OlderThanDay, RelativeTimeCalculator.compute(now, published))
    }

    @Test
    fun computesAcrossOffsets() {
        // `now` and `published` carry different offsets but represent the
        // same UTC instants — the calculator must produce the same answer as
        // when both sides share an offset.
        val local = now.withOffsetSameInstant(ZoneOffset.ofHours(3))
        val published = local.minus(Duration.ofMinutes(15))
        assertEquals(
            RelativeTime.MinutesAgo(15),
            RelativeTimeCalculator.compute(local, published),
        )
    }
}