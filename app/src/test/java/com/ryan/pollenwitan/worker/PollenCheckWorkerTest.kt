package com.ryan.pollenwitan.worker

import com.ryan.pollenwitan.domain.model.AllergenThreshold
import com.ryan.pollenwitan.domain.model.AppLocation
import com.ryan.pollenwitan.domain.model.CurrentConditions
import com.ryan.pollenwitan.domain.model.DoseConfirmation
import com.ryan.pollenwitan.domain.model.MedicineAssignment
import com.ryan.pollenwitan.domain.model.PollenReading
import com.ryan.pollenwitan.domain.model.PollenSeasonCalendar
import com.ryan.pollenwitan.domain.model.PollenType
import com.ryan.pollenwitan.domain.model.ProfileLocation
import com.ryan.pollenwitan.domain.model.SeverityClassifier
import com.ryan.pollenwitan.domain.model.SeverityLevel
import com.ryan.pollenwitan.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class PollenCheckWorkerTest {

    // ── Location grouping ──────────────────────────────────────────────

    @Test
    fun `profiles without location override share global location group`() {
        val global = AppLocation(52.41, 16.93, "Poznan")
        val profiles = listOf(
            makeProfile("ryan", trackedTypes = listOf(PollenType.Birch)),
            makeProfile("olga", trackedTypes = listOf(PollenType.Alder))
        )

        val groups = PollenCheckLogic.groupProfilesByLocation(profiles, global)

        assertEquals("Both profiles should map to the same location", 1, groups.size)
        assertEquals(2, groups.values.first().size)
    }

    @Test
    fun `profile with location override forms separate group`() {
        val global = AppLocation(52.41, 16.93, "Poznan")
        val profiles = listOf(
            makeProfile("ryan", trackedTypes = listOf(PollenType.Birch)),
            makeProfile("olga", trackedTypes = listOf(PollenType.Alder),
                location = ProfileLocation(50.06, 19.94, "Krakow"))
        )

        val groups = PollenCheckLogic.groupProfilesByLocation(profiles, global)

        assertEquals("Two distinct locations expected", 2, groups.size)
        groups.values.forEach { assertEquals(1, it.size) }
    }

    @Test
    fun `two profiles with same override share one group`() {
        val global = AppLocation(52.41, 16.93, "Poznan")
        val profiles = listOf(
            makeProfile("a", location = ProfileLocation(50.06, 19.94, "Krakow")),
            makeProfile("b", location = ProfileLocation(50.06, 19.94, "Krakow"))
        )

        val groups = PollenCheckLogic.groupProfilesByLocation(profiles, global)

        assertEquals(1, groups.size)
        assertEquals(2, groups.values.first().size)
    }

    @Test
    fun `empty profile list produces empty groups`() {
        val global = AppLocation(52.41, 16.93, "Poznan")

        val groups = PollenCheckLogic.groupProfilesByLocation(emptyList(), global)

        assertTrue(groups.isEmpty())
    }

    @Test
    fun `group key uses correct coordinates from override`() {
        val global = AppLocation(52.41, 16.93, "Poznan")
        val profile = makeProfile("a", location = ProfileLocation(50.06, 19.94, "Krakow"))

        val groups = PollenCheckLogic.groupProfilesByLocation(listOf(profile), global)

        val key = groups.keys.single()
        assertEquals(50.06, key.first, 0.001)
        assertEquals(19.94, key.second, 0.001)
    }

    // ── Threshold breach detection ─────────────────────────────────────

    @Test
    fun `no breach when all readings below high`() {
        val profile = makeProfile("ryan", trackedTypes = listOf(PollenType.Birch))
        val conditions = makeConditions(birch = 30.0) // Moderate for birch (< 51)

        val breaches = PollenCheckLogic.findThresholdBreaches(profile, conditions)

        assertTrue("No breaches expected for moderate level", breaches.isEmpty())
    }

    @Test
    fun `breach detected at high severity`() {
        val profile = makeProfile("ryan", trackedTypes = listOf(PollenType.Birch))
        val conditions = makeConditions(birch = 55.0) // High for birch (51-200)

        val breaches = PollenCheckLogic.findThresholdBreaches(profile, conditions)

        assertEquals(1, breaches.size)
        assertEquals(PollenType.Birch, breaches[0].type)
        assertEquals(55.0, breaches[0].value, 0.001)
        assertEquals(SeverityLevel.High, breaches[0].severity)
    }

    @Test
    fun `breach detected at very high severity`() {
        val profile = makeProfile("ryan", trackedTypes = listOf(PollenType.Birch))
        val conditions = makeConditions(birch = 250.0) // VeryHigh for birch (>200)

        val breaches = PollenCheckLogic.findThresholdBreaches(profile, conditions)

        assertEquals(1, breaches.size)
        assertEquals(SeverityLevel.VeryHigh, breaches[0].severity)
    }

    @Test
    fun `untracked allergen not reported as breach`() {
        val profile = makeProfile("ryan", trackedTypes = listOf(PollenType.Birch))
        val conditions = makeConditions(grass = 100.0) // VeryHigh grass, but not tracked

        val breaches = PollenCheckLogic.findThresholdBreaches(profile, conditions)

        assertTrue("Untracked allergen should not produce breach", breaches.isEmpty())
    }

    @Test
    fun `multiple tracked allergens can breach simultaneously`() {
        val profile = makeProfile("ryan", trackedTypes = listOf(PollenType.Birch, PollenType.Grass))
        val conditions = makeConditions(birch = 55.0, grass = 85.0) // Both High+

        val breaches = PollenCheckLogic.findThresholdBreaches(profile, conditions)

        assertEquals(2, breaches.size)
        val types = breaches.map { it.type }.toSet()
        assertTrue(PollenType.Birch in types)
        assertTrue(PollenType.Grass in types)
    }

    @Test
    fun `breach at exact high threshold boundary`() {
        val profile = makeProfile("ryan", trackedTypes = listOf(PollenType.Birch))
        val conditions = makeConditions(birch = 51.0) // Exactly at high threshold

        val breaches = PollenCheckLogic.findThresholdBreaches(profile, conditions)

        assertEquals(1, breaches.size)
        assertEquals(SeverityLevel.High, breaches[0].severity)
    }

    @Test
    fun `no breach at value just below high threshold`() {
        val profile = makeProfile("ryan", trackedTypes = listOf(PollenType.Birch))
        val conditions = makeConditions(birch = 50.99) // Just below high (51)

        val breaches = PollenCheckLogic.findThresholdBreaches(profile, conditions)

        assertTrue(breaches.isEmpty())
    }

    @Test
    fun `breach uses custom threshold not default`() {
        val customThreshold = AllergenThreshold(PollenType.Birch, low = 1.0, moderate = 5.0, high = 20.0, veryHigh = 50.0)
        val profile = makeProfile("ryan", customAllergens = mapOf(PollenType.Birch to customThreshold))
        val conditions = makeConditions(birch = 25.0) // High with custom threshold, Moderate with default

        val breaches = PollenCheckLogic.findThresholdBreaches(profile, conditions)

        assertEquals(1, breaches.size)
        assertEquals(SeverityLevel.High, breaches[0].severity)
    }

    // ── Compound risk (asthma + PM) ────────────────────────────────────

    @Test
    fun `compound risk when pollen moderate and pm25 elevated`() {
        val profile = makeProfile("ryan", trackedTypes = listOf(PollenType.Birch), asthma = true)
        val conditions = makeConditions(birch = 15.0, pm25 = 30.0, pm10 = 10.0) // Moderate birch + PM2.5 > 25

        assertTrue(PollenCheckLogic.hasCompoundRisk(profile, conditions))
    }

    @Test
    fun `compound risk when pollen moderate and pm10 elevated`() {
        val profile = makeProfile("ryan", trackedTypes = listOf(PollenType.Birch), asthma = true)
        val conditions = makeConditions(birch = 15.0, pm25 = 10.0, pm10 = 55.0) // Moderate birch + PM10 > 50

        assertTrue(PollenCheckLogic.hasCompoundRisk(profile, conditions))
    }

    @Test
    fun `no compound risk when pollen low even with high pm`() {
        val profile = makeProfile("ryan", trackedTypes = listOf(PollenType.Birch), asthma = true)
        val conditions = makeConditions(birch = 5.0, pm25 = 40.0, pm10 = 80.0) // Low birch

        assertFalse(PollenCheckLogic.hasCompoundRisk(profile, conditions))
    }

    @Test
    fun `no compound risk when pollen high but pm normal`() {
        val profile = makeProfile("ryan", trackedTypes = listOf(PollenType.Birch), asthma = true)
        val conditions = makeConditions(birch = 100.0, pm25 = 10.0, pm10 = 20.0) // High birch but PM below thresholds

        assertFalse(PollenCheckLogic.hasCompoundRisk(profile, conditions))
    }

    @Test
    fun `no compound risk for untracked allergen even if high`() {
        val profile = makeProfile("ryan", trackedTypes = listOf(PollenType.Birch), asthma = true)
        val conditions = makeConditions(grass = 100.0, pm25 = 40.0) // Grass not tracked

        assertFalse(PollenCheckLogic.hasCompoundRisk(profile, conditions))
    }

    @Test
    fun `compound risk at pm25 boundary 25 is not elevated`() {
        val profile = makeProfile("ryan", trackedTypes = listOf(PollenType.Birch), asthma = true)
        val conditions = makeConditions(birch = 15.0, pm25 = 25.0, pm10 = 10.0) // PM2.5 == 25, not > 25

        assertFalse(PollenCheckLogic.hasCompoundRisk(profile, conditions))
    }

    @Test
    fun `compound risk at pm10 boundary 50 is not elevated`() {
        val profile = makeProfile("ryan", trackedTypes = listOf(PollenType.Birch), asthma = true)
        val conditions = makeConditions(birch = 15.0, pm25 = 10.0, pm10 = 50.0) // PM10 == 50, not > 50

        assertFalse(PollenCheckLogic.hasCompoundRisk(profile, conditions))
    }

    @Test
    fun `compound risk at pm25 just above boundary`() {
        val profile = makeProfile("ryan", trackedTypes = listOf(PollenType.Birch), asthma = true)
        val conditions = makeConditions(birch = 15.0, pm25 = 25.1, pm10 = 10.0)

        assertTrue(PollenCheckLogic.hasCompoundRisk(profile, conditions))
    }

    // ── Morning briefing trigger ───────────────────────────────────────

    @Test
    fun `morning briefing fires when enabled and not yet sent today`() {
        assertTrue(
            PollenCheckLogic.shouldSendMorningBriefing(
                enabled = true,
                lastBriefingDate = LocalDate.of(2026, 3, 27),
                today = LocalDate.of(2026, 3, 28),
                currentHour = 7,
                configuredHour = 7
            )
        )
    }

    @Test
    fun `morning briefing does not fire when disabled`() {
        assertFalse(
            PollenCheckLogic.shouldSendMorningBriefing(
                enabled = false,
                lastBriefingDate = null,
                today = LocalDate.of(2026, 3, 28),
                currentHour = 7,
                configuredHour = 7
            )
        )
    }

    @Test
    fun `morning briefing does not fire when already sent today`() {
        val today = LocalDate.of(2026, 3, 28)
        assertFalse(
            PollenCheckLogic.shouldSendMorningBriefing(
                enabled = true,
                lastBriefingDate = today,
                today = today,
                currentHour = 8,
                configuredHour = 7
            )
        )
    }

    @Test
    fun `morning briefing does not fire before configured hour`() {
        assertFalse(
            PollenCheckLogic.shouldSendMorningBriefing(
                enabled = true,
                lastBriefingDate = null,
                today = LocalDate.of(2026, 3, 28),
                currentHour = 5,
                configuredHour = 7
            )
        )
    }

    @Test
    fun `morning briefing fires at configured hour exactly`() {
        assertTrue(
            PollenCheckLogic.shouldSendMorningBriefing(
                enabled = true,
                lastBriefingDate = null,
                today = LocalDate.of(2026, 3, 28),
                currentHour = 7,
                configuredHour = 7
            )
        )
    }

    @Test
    fun `morning briefing fires after configured hour if not yet sent`() {
        assertTrue(
            PollenCheckLogic.shouldSendMorningBriefing(
                enabled = true,
                lastBriefingDate = null,
                today = LocalDate.of(2026, 3, 28),
                currentHour = 10,
                configuredHour = 7
            )
        )
    }

    @Test
    fun `morning briefing fires when last briefing date is null`() {
        assertTrue(
            PollenCheckLogic.shouldSendMorningBriefing(
                enabled = true,
                lastBriefingDate = null,
                today = LocalDate.of(2026, 3, 28),
                currentHour = 7,
                configuredHour = 7
            )
        )
    }

    // ── Pre-season dedup ───────────────────────────────────────────────

    @Test
    fun `pre-season alert fires when no alert sent this year`() {
        assertTrue(PollenCheckLogic.shouldSendPreSeasonAlert(lastAlertYear = null, currentYear = 2026))
    }

    @Test
    fun `pre-season alert fires when last alert was previous year`() {
        assertTrue(PollenCheckLogic.shouldSendPreSeasonAlert(lastAlertYear = 2025, currentYear = 2026))
    }

    @Test
    fun `pre-season alert suppressed when already sent this year`() {
        assertFalse(PollenCheckLogic.shouldSendPreSeasonAlert(lastAlertYear = 2026, currentYear = 2026))
    }

    @Test
    fun `pre-season alert fires for distant past year`() {
        assertTrue(PollenCheckLogic.shouldSendPreSeasonAlert(lastAlertYear = 2020, currentYear = 2026))
    }

    @Test
    fun `PollenSeasonCalendar pre-season window includes correct date for birch`() {
        // Birch season starts April 1 -> alert window starts March 4 (4 weeks before), ends March 10
        val alertTypes = PollenSeasonCalendar.preSeasonAlerts(
            setOf(PollenType.Birch), LocalDate.of(2026, 3, 5)
        )
        assertTrue("Birch should be in pre-season window on March 5", PollenType.Birch in alertTypes)
    }

    @Test
    fun `PollenSeasonCalendar pre-season window excludes date outside window`() {
        // Birch window: March 4-10. March 15 is outside.
        val alertTypes = PollenSeasonCalendar.preSeasonAlerts(
            setOf(PollenType.Birch), LocalDate.of(2026, 3, 15)
        )
        assertTrue("Birch should not be in window on March 15", alertTypes.isEmpty())
    }

    @Test
    fun `PollenSeasonCalendar only returns tracked allergens`() {
        // March 5 is in birch window but we only track grass
        val alertTypes = PollenSeasonCalendar.preSeasonAlerts(
            setOf(PollenType.Grass), LocalDate.of(2026, 3, 5)
        )
        assertTrue("Only grass is tracked, but March 5 is not in grass window", alertTypes.isEmpty())
    }

    // ── Medication reminder hour matching ──────────────────────────────

    @Test
    fun `reminder fires for matching hour when not confirmed`() {
        val assignments = listOf(
            MedicineAssignment(medicineId = "med1", dose = 1, timesPerDay = 1, reminderHours = listOf(8))
        )

        val pending = PollenCheckLogic.pendingMedicationReminders(assignments, emptySet(), currentHour = 8)

        assertEquals(1, pending.size)
        assertEquals("med1", pending[0].third)
    }

    @Test
    fun `no reminder when hour does not match`() {
        val assignments = listOf(
            MedicineAssignment(medicineId = "med1", dose = 1, timesPerDay = 1, reminderHours = listOf(8))
        )

        val pending = PollenCheckLogic.pendingMedicationReminders(assignments, emptySet(), currentHour = 9)

        assertTrue(pending.isEmpty())
    }

    @Test
    fun `no reminder when dose already confirmed`() {
        val assignments = listOf(
            MedicineAssignment(medicineId = "med1", dose = 1, timesPerDay = 1, reminderHours = listOf(8))
        )
        val confirmations = setOf(DoseConfirmation("med1", 0))

        val pending = PollenCheckLogic.pendingMedicationReminders(assignments, confirmations, currentHour = 8)

        assertTrue("Confirmed dose should not produce reminder", pending.isEmpty())
    }

    @Test
    fun `multiple slots only pending unconfirmed ones`() {
        val assignments = listOf(
            MedicineAssignment(medicineId = "med1", dose = 1, timesPerDay = 3, reminderHours = listOf(8, 14, 20))
        )
        val confirmations = setOf(DoseConfirmation("med1", 0)) // slot 0 (hour 8) confirmed

        val pending = PollenCheckLogic.pendingMedicationReminders(assignments, confirmations, currentHour = 8)

        assertTrue("Slot 0 at hour 8 is confirmed", pending.isEmpty())
    }

    @Test
    fun `unconfirmed slot at matching hour fires reminder`() {
        val assignments = listOf(
            MedicineAssignment(medicineId = "med1", dose = 1, timesPerDay = 3, reminderHours = listOf(8, 14, 20))
        )
        val confirmations = setOf(DoseConfirmation("med1", 0)) // only slot 0 confirmed

        val pending = PollenCheckLogic.pendingMedicationReminders(assignments, confirmations, currentHour = 14)

        assertEquals(1, pending.size)
        assertEquals(1, pending[0].second) // slotIndex 1
    }

    @Test
    fun `multiple assignments at same hour produce multiple reminders`() {
        val assignments = listOf(
            MedicineAssignment(medicineId = "med1", dose = 1, timesPerDay = 1, reminderHours = listOf(8)),
            MedicineAssignment(medicineId = "med2", dose = 2, timesPerDay = 1, reminderHours = listOf(8))
        )

        val pending = PollenCheckLogic.pendingMedicationReminders(assignments, emptySet(), currentHour = 8)

        assertEquals(2, pending.size)
        val meds = pending.map { it.third }.toSet()
        assertTrue("med1" in meds)
        assertTrue("med2" in meds)
    }

    @Test
    fun `empty assignments produce no reminders`() {
        val pending = PollenCheckLogic.pendingMedicationReminders(emptyList(), emptySet(), currentHour = 8)

        assertTrue(pending.isEmpty())
    }

    @Test
    fun `pending reminder returns correct assignment index`() {
        val assignments = listOf(
            MedicineAssignment(medicineId = "med1", dose = 1, timesPerDay = 1, reminderHours = listOf(7)),
            MedicineAssignment(medicineId = "med2", dose = 1, timesPerDay = 1, reminderHours = listOf(8))
        )

        val pending = PollenCheckLogic.pendingMedicationReminders(assignments, emptySet(), currentHour = 8)

        assertEquals(1, pending.size)
        assertEquals(1, pending[0].first)  // assignmentIndex = 1
        assertEquals(0, pending[0].second) // slotIndex = 0
        assertEquals("med2", pending[0].third)
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun makeProfile(
        id: String,
        trackedTypes: List<PollenType> = emptyList(),
        asthma: Boolean = false,
        location: ProfileLocation? = null,
        customAllergens: Map<PollenType, AllergenThreshold>? = null
    ): UserProfile {
        val allergens = customAllergens ?: trackedTypes.associateWith { UserProfile.defaultThreshold(it) }
        return UserProfile(
            id = id,
            displayName = id,
            trackedAllergens = allergens,
            hasAsthma = asthma,
            location = location
        )
    }

    private fun makeConditions(
        birch: Double = 0.0,
        alder: Double = 0.0,
        grass: Double = 0.0,
        mugwort: Double = 0.0,
        ragweed: Double = 0.0,
        olive: Double = 0.0,
        aqi: Int = 30,
        pm25: Double = 5.0,
        pm10: Double = 10.0
    ): CurrentConditions {
        val readings = listOf(
            PollenReading(PollenType.Birch, birch, SeverityClassifier.pollenSeverity(PollenType.Birch, birch)),
            PollenReading(PollenType.Alder, alder, SeverityClassifier.pollenSeverity(PollenType.Alder, alder)),
            PollenReading(PollenType.Grass, grass, SeverityClassifier.pollenSeverity(PollenType.Grass, grass)),
            PollenReading(PollenType.Mugwort, mugwort, SeverityClassifier.pollenSeverity(PollenType.Mugwort, mugwort)),
            PollenReading(PollenType.Ragweed, ragweed, SeverityClassifier.pollenSeverity(PollenType.Ragweed, ragweed)),
            PollenReading(PollenType.Olive, olive, SeverityClassifier.pollenSeverity(PollenType.Olive, olive))
        )
        return CurrentConditions(
            pollenReadings = readings,
            europeanAqi = aqi,
            pm25 = pm25,
            pm10 = pm10,
            aqiSeverity = SeverityClassifier.aqiSeverity(aqi),
            timestamp = LocalDateTime.of(2026, 3, 28, 10, 0)
        )
    }
}
