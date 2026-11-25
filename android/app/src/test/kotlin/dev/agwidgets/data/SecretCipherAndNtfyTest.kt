package dev.agwidgets.data

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * JVM-only checks for the [SecretCipher] contract and for the topic-validation
 * preconditions of [NtfyClient]. No AndroidKeyStore, no Android APIs and no
 * network access are involved.
 */
class SecretCipherAndNtfyTest {

    private val cipher = FakeSecretCipher()

    // ------------------------------------------------------------------
    // Part A: SecretCipher contract
    // ------------------------------------------------------------------

    @Test
    fun prefixMatchesDocumentedFormat() {
        assertEquals("ks:v1:", SecretCipher.PREFIX)
    }

    @Test
    fun realCipherReturnsLegacyPlaintextUnchanged() {
        // Legacy values never reach the keystore: `decrypt` short-circuits on
        // the missing prefix, which is the only safe JVM path.
        val real = KeystoreSecretCipher()
        assertFalse(real.isEncrypted("plain-token"))
        assertEquals("plain-token", real.decrypt("plain-token"))
    }

    @Test
    fun fakeEncryptProducesPrefixedBlobWithTwoBodySegments() {
        val encrypted = cipher.encrypt("secret")
        assertTrue(encrypted.startsWith(SecretCipher.PREFIX))
        // Blob layout is `ks:v1:<base64(iv)>:<base64(ciphertext)>`: two colons
        // live inside the prefix and the third separates the body segments.
        assertEquals(3, encrypted.count { it == ':' })
        assertEquals(4, encrypted.split(":").size)
        val body = encrypted.removePrefix(SecretCipher.PREFIX)
        val segments = body.split(":")
        assertEquals(2, segments.size)
        assertTrue(segments[0].isNotEmpty())
        assertTrue(segments[1].isNotEmpty())
    }

    @Test
    fun fakeRoundTripsSimpleSecret() {
        assertRoundTrip("secret")
    }

    @Test
    fun fakeRoundTripsEmptyString() {
        assertRoundTrip("")
    }

    @Test
    fun fakeRoundTripsCyrillicText() {
        assertRoundTrip("простой")
    }

    @Test
    fun fakeRoundTripsTopicLikeToken() {
        assertRoundTrip("agw-abc123")
    }

    @Test
    fun fakeRoundTripsLongString() {
        assertRoundTrip("a".repeat(10_000))
    }

    @Test
    fun fakeRoundTripsEmoji() {
        assertRoundTrip("🔐")
    }

    @Test
    fun fakeEncryptUsesFreshIvOnEveryCall() {
        val first = cipher.encrypt("secret")
        val second = cipher.encrypt("secret")
        assertNotEquals(
            "Two encryptions of the same plaintext must differ because the IV is random",
            first,
            second,
        )
    }

    @Test
    fun fakeIsEncryptedDetectsOnlyPrefixedBlobs() {
        assertTrue(cipher.isEncrypted(cipher.encrypt("secret")))
        assertFalse(cipher.isEncrypted("legacy"))
        assertFalse(cipher.isEncrypted(""))
    }

    @Test
    fun fakeDecryptReturnsLegacyPlaintextAsIs() {
        assertEquals("legacy-plain", cipher.decrypt("legacy-plain"))
    }

    @Test
    fun fakeDecryptReturnsNullForBlobWithoutCiphertextSegment() {
        assertNull(cipher.decrypt("ks:v1:bm90LWEtYmxvYg=="))
    }

    @Test
    fun fakeDecryptReturnsNullForInvalidBase64OrIvLength() {
        assertNull(cipher.decrypt("ks:v1:AAAA:BBBB"))
        assertNull(cipher.decrypt("ks:v1:%%%:%%%"))
    }

    // ------------------------------------------------------------------
    // Part B: NtfyClient topic validation (rejected before any HTTP call)
    // ------------------------------------------------------------------

    @Test
    fun rejectsEmptyTopic() {
        assertInvalidTopic("")
    }

    @Test
    fun rejectsWhitespaceOnlyTopic() {
        assertInvalidTopic("   ")
    }

    @Test
    fun rejectsPathTraversalTopic() {
        assertInvalidTopic("../etc/passwd")
    }

    @Test
    fun rejectsTopicWithSlash() {
        assertInvalidTopic("topic/with/slash")
    }

    @Test
    fun rejectsTopicWithQueryString() {
        assertInvalidTopic("topic?x=1")
    }

    @Test
    fun rejectsTopicWithSpace() {
        assertInvalidTopic("a b")
    }

    @Test
    fun rejectsNonAsciiTopic() {
        assertInvalidTopic("тема")
    }

    @Test
    fun rejectsOverlongTopic() {
        assertInvalidTopic("a".repeat(65))
    }

    @Test
    fun rejectsTopicWithNewline() {
        assertInvalidTopic("topic\nevent")
    }

    @Test
    fun invalidTopicFailureMessageMentionsInvalidTopic() {
        val result = runBlocking { NtfyClient.fetchLatestMessage("bad topic!") }
        assertTrue("Expected failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Expected IllegalArgumentException, got $error", error is IllegalArgumentException)
        assertTrue(
            "UI/log contract expects 'Invalid ntfy topic', got <${error?.message}>",
            error?.message?.contains("Invalid ntfy topic") == true,
        )
    }

    private fun assertRoundTrip(plaintext: String) {
        val encrypted = cipher.encrypt(plaintext)
        assertTrue(
            "Ciphertext must carry the versioned prefix",
            encrypted.startsWith(SecretCipher.PREFIX),
        )
        assertEquals(plaintext, cipher.decrypt(encrypted))
    }

    private fun assertInvalidTopic(topic: String) {
        val result = runBlocking { NtfyClient.fetchLatestMessage(topic) }
        assertTrue("Expected failure for topic <$topic>", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(
            "Expected IllegalArgumentException for topic <$topic>, got $error",
            error is IllegalArgumentException,
        )
        assertTrue(
            "Expected 'Invalid ntfy topic' message for topic <$topic>, got <${error?.message}>",
            error?.message?.contains("Invalid ntfy topic") == true,
        )
    }
}

/**
 * JVM-only [SecretCipher] that mirrors the production blob format
 * (`ks:v1:<base64(iv)>:<base64(ciphertext)>`) with AES-256-GCM and a static
 * in-memory key. It never touches AndroidKeyStore or `android.util.Base64`, so
 * it runs on a plain JVM; corrupt blobs return `null` instead of throwing,
 * which is the contract [KeystoreSecretCipher] promises to callers.
 */
private class FakeSecretCipher : SecretCipher {

    private val key = SecretKeySpec(ByteArray(KEY_BYTES) { (it * 7 + 3).toByte() }, "AES")
    private val random = SecureRandom()

    override fun encrypt(plaintext: String): String {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return SecretCipher.PREFIX + encode(iv) + ":" + encode(ciphertext)
    }

    override fun decrypt(stored: String): String? {
        if (!isEncrypted(stored)) return stored
        return try {
            val parts = stored.removePrefix(SecretCipher.PREFIX).split(":")
            require(parts.size == 2) { "Malformed secret blob" }
            val iv = decode(parts[0])
            require(iv.size == IV_BYTES) { "Unexpected IV length" }
            val ciphertext = decode(parts[1])
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    override fun isEncrypted(stored: String): Boolean =
        stored.startsWith(SecretCipher.PREFIX)

    private fun encode(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)

    private fun decode(value: String): ByteArray =
        Base64.getDecoder().decode(value)

    private companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val KEY_BYTES = 32
    }
}
