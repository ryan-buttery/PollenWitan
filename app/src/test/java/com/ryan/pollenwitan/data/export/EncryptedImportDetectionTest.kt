package com.ryan.pollenwitan.data.export

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for encrypted vs plaintext import detection logic.
 *
 * These tests verify that [EncryptedExportEnvelope] serialisation and format
 * detection work correctly, mirroring the detection logic in
 * [AppDataImporter.tryDecrypt]. No Android dependencies — pure model tests.
 */
class EncryptedImportDetectionTest {

    private val exportJson = Json { prettyPrint = true; encodeDefaults = true }
    private val importJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ── Encrypted envelope serialisation ───────────────────────────────

    @Test
    fun `encrypted envelope round-trips with all fields`() {
        val envelope = EncryptedExportEnvelope(
            salt = "dGVzdHNhbHQ=",
            iv = "dGVzdGl2MTIz",
            ciphertext = "ZW5jcnlwdGVkZGF0YQ=="
        )
        val jsonString = exportJson.encodeToString(envelope)
        val restored = importJson.decodeFromString<EncryptedExportEnvelope>(jsonString)

        assertEquals(envelope, restored)
    }

    @Test
    fun `encrypted envelope has correct default format`() {
        val envelope = EncryptedExportEnvelope(
            salt = "dGVzdHNhbHQ=",
            iv = "dGVzdGl2MTIz",
            ciphertext = "ZW5jcnlwdGVkZGF0YQ=="
        )

        assertEquals("pollenwitan-encrypted-backup", envelope.format)
    }

    @Test
    fun `encrypted envelope has correct default version`() {
        val envelope = EncryptedExportEnvelope(
            salt = "dGVzdHNhbHQ=",
            iv = "dGVzdGl2MTIz",
            ciphertext = "ZW5jcnlwdGVkZGF0YQ=="
        )

        assertEquals(1, envelope.version)
    }

    @Test
    fun `encrypted envelope format field appears in serialised JSON`() {
        val envelope = EncryptedExportEnvelope(
            salt = "dGVzdHNhbHQ=",
            iv = "dGVzdGl2MTIz",
            ciphertext = "ZW5jcnlwdGVkZGF0YQ=="
        )
        val jsonString = exportJson.encodeToString(envelope)

        assertTrue(jsonString.contains("\"format\": \"pollenwitan-encrypted-backup\""))
    }

    // ── Detection: encrypted vs plaintext ──────────────────────────────

    @Test
    fun `encrypted envelope JSON is parseable as EncryptedExportEnvelope`() {
        val envelope = EncryptedExportEnvelope(
            salt = "dGVzdHNhbHQ=",
            iv = "dGVzdGl2MTIz",
            ciphertext = "ZW5jcnlwdGVkZGF0YQ=="
        )
        val jsonString = exportJson.encodeToString(envelope)

        val parsed = try {
            importJson.decodeFromString<EncryptedExportEnvelope>(jsonString)
        } catch (_: Exception) {
            null
        }

        assertNotNull(parsed)
        assertEquals("pollenwitan-encrypted-backup", parsed!!.format)
    }

    @Test
    fun `plaintext ExportData JSON does not match encrypted format`() {
        // Plaintext ExportData might technically parse as EncryptedExportEnvelope
        // (with ignoreUnknownKeys), but the format field won't match.
        val data = minimalExportData()
        val jsonString = exportJson.encodeToString(data)

        val parsed = try {
            importJson.decodeFromString<EncryptedExportEnvelope>(jsonString)
        } catch (_: Exception) {
            null
        }

        // Either parsing fails entirely or the format field doesn't match
        if (parsed != null) {
            assertTrue(
                "Plaintext data should not have encrypted backup format",
                parsed.format != "pollenwitan-encrypted-backup"
            )
        }
    }

    @Test
    fun `completely invalid JSON fails to parse as envelope`() {
        val parsed = try {
            importJson.decodeFromString<EncryptedExportEnvelope>("this is not json")
        } catch (_: Exception) {
            null
        }

        assertNull(parsed)
    }

    @Test
    fun `empty string fails to parse as envelope`() {
        val parsed = try {
            importJson.decodeFromString<EncryptedExportEnvelope>("")
        } catch (_: Exception) {
            null
        }

        assertNull(parsed)
    }

    @Test
    fun `JSON without required envelope fields fails to parse`() {
        val incompleteJson = """{"format": "pollenwitan-encrypted-backup", "version": 1}"""

        val parsed = try {
            importJson.decodeFromString<EncryptedExportEnvelope>(incompleteJson)
        } catch (_: Exception) {
            null
        }

        assertNull(parsed)
    }

    // ── Envelope with custom format (future-proofing) ──────────────────

    @Test
    fun `envelope with different format is detectable`() {
        val jsonString = """
            {
                "format": "some-other-format",
                "version": 1,
                "salt": "dGVzdHNhbHQ=",
                "iv": "dGVzdGl2MTIz",
                "ciphertext": "ZW5jcnlwdGVkZGF0YQ=="
            }
        """.trimIndent()

        val parsed = importJson.decodeFromString<EncryptedExportEnvelope>(jsonString)

        assertEquals("some-other-format", parsed.format)
        assertTrue(parsed.format != "pollenwitan-encrypted-backup")
    }

    @Test
    fun `envelope with future version parses correctly`() {
        val jsonString = """
            {
                "format": "pollenwitan-encrypted-backup",
                "version": 2,
                "salt": "dGVzdHNhbHQ=",
                "iv": "dGVzdGl2MTIz",
                "ciphertext": "ZW5jcnlwdGVkZGF0YQ=="
            }
        """.trimIndent()

        val parsed = importJson.decodeFromString<EncryptedExportEnvelope>(jsonString)

        assertEquals(2, parsed.version)
        assertEquals("pollenwitan-encrypted-backup", parsed.format)
    }

    // ── Envelope with extra unknown fields (forward compat) ────────────

    @Test
    fun `envelope with unknown fields parses with ignoreUnknownKeys`() {
        val jsonString = """
            {
                "format": "pollenwitan-encrypted-backup",
                "version": 1,
                "algorithm": "AES-256-GCM",
                "salt": "dGVzdHNhbHQ=",
                "iv": "dGVzdGl2MTIz",
                "ciphertext": "ZW5jcnlwdGVkZGF0YQ==",
                "futureField": "ignored"
            }
        """.trimIndent()

        val parsed = importJson.decodeFromString<EncryptedExportEnvelope>(jsonString)

        assertEquals("pollenwitan-encrypted-backup", parsed.format)
        assertEquals("dGVzdHNhbHQ=", parsed.salt)
        assertEquals("dGVzdGl2MTIz", parsed.iv)
        assertEquals("ZW5jcnlwdGVkZGF0YQ==", parsed.ciphertext)
    }

    // ── Helper ─────────────────────────────────────────────────────────

    private fun minimalExportData() = ExportData(
        version = 1,
        exportedAt = "2026-03-30T10:00:00Z",
        profiles = emptyList(),
        medicines = emptyList(),
        doseHistory = emptyList(),
        symptomEntries = emptyList(),
        locationSettings = ExportLocation(
            mode = "Manual",
            manualLatitude = 52.4064,
            manualLongitude = 16.9252,
            manualDisplayName = "Poznań"
        ),
        notificationPrefs = ExportNotificationPrefs(
            morningBriefingEnabled = false,
            morningBriefingHour = 6,
            thresholdAlertsEnabled = false,
            compoundRiskAlertsEnabled = false,
            preSeasonAlertsEnabled = false,
            symptomReminderEnabled = false,
            symptomReminderHour = 20
        )
    )
}
