package com.ryan.pollenwitan.domain.model

import java.time.LocalDate
import java.time.MonthDay

object PollenSeasonCalendar {

    private const val PRE_SEASON_WEEKS = 4L

    // Typical season start dates for Central Europe (Poznań region)
    val seasonStart: Map<PollenType, MonthDay> = mapOf(
        PollenType.Alder to MonthDay.of(2, 15),     // Mid-February
        PollenType.Birch to MonthDay.of(4, 1),       // Early April
        PollenType.Grass to MonthDay.of(5, 15),      // Mid-May
        PollenType.Mugwort to MonthDay.of(7, 15),    // Mid-July
        PollenType.Ragweed to MonthDay.of(8, 1),     // Early August
        PollenType.Olive to MonthDay.of(5, 25)       // Late May
    )

    /**
     * Returns pollen types whose pre-season alert window includes [today].
     * The window is a 7-day range starting [PRE_SEASON_WEEKS] weeks before the season start.
     * Only considers allergens in [trackedAllergens].
     */
    fun preSeasonAlerts(
        trackedAllergens: Set<PollenType>,
        today: LocalDate
    ): List<PollenType> {
        return trackedAllergens.filter { type ->
            val start = seasonStart[type] ?: return@filter false
            val seasonStartDate = start.atYear(today.year)
            val alertStart = seasonStartDate.minusWeeks(PRE_SEASON_WEEKS)
            val alertEnd = alertStart.plusDays(6)
            today in alertStart..alertEnd
        }
    }

    /**
     * Returns a formatted season start string (e.g. "April 1" or "1 kwietnia")
     * for display in notifications.
     */
    fun seasonStartDisplay(type: PollenType, today: LocalDate): String {
        val start = seasonStart[type] ?: return ""
        val date = start.atYear(today.year)
        return java.time.format.DateTimeFormatter.ofPattern("MMMM d").format(date)
    }
}
