package com.ryan.pollenwitan.data.security

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * AES-256-GCM encryption/decryption for data export files.
 *
 * - Key derivation: PBKDF2-HMAC-SHA256, 120 000 iterations, random 16-byte salt
 * - Cipher: AES/GCM/NoPadding with random 12-byte IV
 * - GCM auth tag provides both confidentiality and authenticity (no separate HMAC needed)
 * - Pure JCE — no Android Keystore dependency, portable and testable
 */
object ExportCrypto {

    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"

    /**
     * Encrypts a plaintext JSON string with the given password.
     *
     * @param plaintext The JSON string to encrypt.
     * @param password  The user-provided password for key derivation.
     * @return An [EncryptedExportEnvelope] containing salt, IV, and ciphertext (all base64-encoded).
     */
    fun encrypt(plaintext: String, password: String): EncryptedExportEnvelope {
        val random = SecureRandom()

        val salt = ByteArray(SALT_LENGTH_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH_BYTES).also { random.nextBytes(it) }

        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        return EncryptedExportEnvelope(
            salt = Base64.encodeToString(salt, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP),
            ciphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        )
    }

    /**
     * Decrypts an [EncryptedExportEnvelope] with the given password.
     *
     * @param envelope The encrypted envelope (salt, IV, ciphertext).
     * @param password The user-provided password for key derivation.
     * @return The decrypted plaintext JSON string.
     * @throws ExportDecryptionException if the password is wrong or data is corrupted.
     */
    fun decrypt(envelope: EncryptedExportEnvelope, password: String): String {
        try {
            val salt = Base64.decode(envelope.salt, Base64.NO_WRAP)
            val iv = Base64.decode(envelope.iv, Base64.NO_WRAP)
            val ciphertext = Base64.decode(envelope.ciphertext, Base64.NO_WRAP)

            val key = deriveKey(password, salt)

            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

            val plaintext = cipher.doFinal(ciphertext)
            return String(plaintext, Charsets.UTF_8)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw ExportDecryptionException("Wrong password or corrupted data", e)
        } catch (e: javax.crypto.BadPaddingException) {
            throw ExportDecryptionException("Wrong password or corrupted data", e)
        } catch (e: IllegalArgumentException) {
            throw ExportDecryptionException("Invalid envelope data (base64 decoding failed)", e)
        }
    }

    /**
     * Serializes an [EncryptedExportEnvelope] to a JSON string for file storage.
     */
    fun envelopeToJson(envelope: EncryptedExportEnvelope): String =
        Json.encodeToString(envelope)

    /**
     * Deserializes a JSON string back into an [EncryptedExportEnvelope].
     *
     * @throws kotlinx.serialization.SerializationException if the JSON is malformed.
     */
    fun envelopeFromJson(json: String): EncryptedExportEnvelope =
        Json.decodeFromString(json)

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(keyBytes, "AES")
    }
}

/**
 * Envelope holding the encrypted export data.
 * All binary fields are base64-encoded for JSON storage.
 */
@Serializable
data class EncryptedExportEnvelope(
    val salt: String,
    val iv: String,
    val ciphertext: String
)

/**
 * Thrown when decryption fails due to wrong password or corrupted data.
 */
class ExportDecryptionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
