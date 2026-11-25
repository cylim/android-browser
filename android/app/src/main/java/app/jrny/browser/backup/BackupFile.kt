package app.jrny.browser.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Why a backup file couldn't be read. */
enum class BackupProblem {
    /** Not an Okapi backup, or a damaged one. */
    NotABackup,

    /** Made by a later version of Okapi, which this one can't read. */
    TooNew,

    /** The password doesn't open it, or the file was changed after it was made. */
    WrongPassword,

    /** Larger than any backup Okapi makes. */
    TooLarge,
}

class BackupException(val problem: BackupProblem) : Exception(problem.name)

/** A backup file, read: a plain one, or one that needs its password. */
sealed interface OpenedBackup {
    data class Plain(val backup: Backup) : OpenedBackup

    class Locked internal constructor(internal val sealed: SealedBackup) : OpenedBackup
}

/**
 * Writes and reads backup files (SPEC, "Backup"). Both kinds are JSON. A plain one is the
 * [Backup] itself. An encrypted one holds the [Backup]'s JSON encrypted with AES-256-GCM, under a
 * key derived from the person's password with PBKDF2-HMAC-SHA256, and the salt, nonce and
 * iteration count it needs. Those are bound to the ciphertext as associated data, so a changed
 * header fails like a wrong password.
 *
 * Plain Kotlin and the JDK's own crypto, so it is unit-tested on the JVM.
 */
object BackupFile {
    /**
     * No backup Okapi makes comes near this: 5,000 visits per Profile is about 1 MB. Reading one
     * takes a few times its size in memory, so the limit stays well below what the app may use.
     */
    const val MAX_BYTES = 16 * 1024 * 1024

    /** PBKDF2's work factor: OWASP's 2023 figure for HMAC-SHA256. */
    const val ITERATIONS = 600_000

    /** A file that asks for more than this is refused, so a crafted file can't stall the app. */
    private const val MAX_ITERATIONS = 10_000_000

    const val MIN_PASSWORD_LENGTH = 8

    private const val SEALED_FORMAT = "okapi-backup-encrypted"
    private const val SEALED_VERSION = 1
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val CIPHER = "AES/GCM/NoPadding"
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val random = SecureRandom()

    /**
     * The file for [backup]: encrypted with [password], or plain JSON when [password] is null.
     * [iterations] is for tests, which can't wait for the real work factor.
     */
    fun write(backup: Backup, password: CharArray?, iterations: Int = ITERATIONS): ByteArray {
        val plain = json.encodeToString(Backup.serializer(), backup).toByteArray(Charsets.UTF_8)
        if (password == null) return plain
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = SealedBackup(SEALED_FORMAT, SEALED_VERSION, KDF, iterations, encode(salt), encode(nonce), data = "")
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(header.associatedData())
        val sealed = header.copy(data = encode(cipher.doFinal(plain)))
        plain.fill(0)
        return json.encodeToString(SealedBackup.serializer(), sealed).toByteArray(Charsets.UTF_8)
    }

    /** Reads [bytes] as a backup file. Throws [BackupException] when it isn't one this version can read. */
    fun read(bytes: ByteArray): OpenedBackup {
        if (bytes.size > MAX_BYTES) throw BackupException(BackupProblem.TooLarge)
        val text = bytes.toString(Charsets.UTF_8)
        val fields = decoding { json.parseToJsonElement(text).jsonObject }
        val format = fields["format"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
        val version = fields["version"]?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() } ?: throw BackupException(BackupProblem.NotABackup)
        return when (format) {
            Backup.FORMAT -> {
                if (version > Backup.VERSION) throw BackupException(BackupProblem.TooNew)
                OpenedBackup.Plain(decodeBackup(text))
            }
            SEALED_FORMAT -> {
                if (version > SEALED_VERSION) throw BackupException(BackupProblem.TooNew)
                val sealed = decoding { json.decodeFromString(SealedBackup.serializer(), text) }
                if (sealed.kdf != KDF || sealed.iterations !in 1..MAX_ITERATIONS) throw BackupException(BackupProblem.NotABackup)
                OpenedBackup.Locked(sealed)
            }
            else -> throw BackupException(BackupProblem.NotABackup)
        }
    }

    /** Opens [locked] with [password]. Throws [BackupException] with [BackupProblem.WrongPassword] when it doesn't. */
    fun unlock(locked: OpenedBackup.Locked, password: CharArray): Backup {
        val sealed = locked.sealed
        val plain = try {
            val salt = decode(sealed.salt)
            val nonce = decode(sealed.iv)
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(Cipher.DECRYPT_MODE, key(password, salt, sealed.iterations), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(sealed.copy(data = "").associatedData())
            cipher.doFinal(decode(sealed.data))
        } catch (_: AEADBadTagException) {
            throw BackupException(BackupProblem.WrongPassword)
        } catch (_: GeneralSecurityException) {
            throw BackupException(BackupProblem.NotABackup)
        } catch (_: IllegalArgumentException) {
            // Not Base64.
            throw BackupException(BackupProblem.NotABackup)
        }
        try {
            val text = plain.toString(Charsets.UTF_8)
            val inner = OpenedBackup.Plain(decodeBackup(text))
            // What was inside must itself be a backup this version reads.
            if (inner.backup.format != Backup.FORMAT) throw BackupException(BackupProblem.NotABackup)
            if (inner.backup.version > Backup.VERSION) throw BackupException(BackupProblem.TooNew)
            return inner.backup
        } finally {
            plain.fill(0)
        }
    }

    private fun decodeBackup(text: String): Backup {
        val backup = decoding { json.decodeFromString(Backup.serializer(), text) }
        if (backup.profiles.isEmpty()) throw BackupException(BackupProblem.NotABackup)
        return backup
    }

    /** Runs [block], which reads JSON; what isn't the JSON it expects is no backup. */
    private inline fun <T> decoding(block: () -> T): T = try {
        block()
    } catch (_: SerializationException) {
        throw BackupException(BackupProblem.NotABackup)
    } catch (_: IllegalArgumentException) {
        // kotlinx-serialization's own errors are IllegalArgumentExceptions too.
        throw BackupException(BackupProblem.NotABackup)
    }

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            val derived = SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
            return SecretKeySpec(derived, "AES").also { derived.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }

    /** The header, as the bytes the ciphertext is bound to. */
    private fun SealedBackup.associatedData(): ByteArray = "$format|$version|$kdf|$iterations|$salt|$iv".toByteArray(Charsets.UTF_8)

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)
}

/** An encrypted backup file. [data] is the ciphertext with GCM's tag at its end, in Base64. */
@Serializable
internal data class SealedBackup(
    val format: String,
    val version: Int,
    val kdf: String,
    val iterations: Int,
    val salt: String,
    val iv: String,
    val data: String,
)
