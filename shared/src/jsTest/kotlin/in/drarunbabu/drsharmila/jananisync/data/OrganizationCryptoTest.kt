package `in`.drarunbabu.drsharmila.jananisync.data

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class OrganizationCryptoTest {
    @Test fun replacementIdentityDecryptsExistingRecordsAndNextRotationWithoutChangingUid() = runTest {
        val uid = "same-clinician"
        val oldPhrase = OrganizationKeyCrypto.generateRecoveryPhrase()
        val oldMaterial = OrganizationKeyCrypto.createMemberKeyMaterial(uid, oldPhrase)
        val newPhrase = OrganizationKeyCrypto.generateRecoveryPhrase()
        val replacement = OrganizationKeyCrypto.createMemberKeyMaterial(uid, newPhrase)
        assertNotEquals(oldMaterial.publicKeyFingerprint, replacement.publicKeyFingerprint)
        val historicKey = SyntheticCrypto.generateKey()
        val currentKey = SyntheticCrypto.generateKey()
        val (history, historyIv) = SyntheticCrypto.encrypt(historicKey, currentKey)
        val (record, recordIv) = SyntheticCrypto.encrypt("existing clinical record", historicKey)
        val grant = OrganizationKeyCrypto.wrapOrganizationDataKey(currentKey, replacement.publicKeyJwk, replacement.publicKeyFingerprint)
        val privateKey = OrganizationKeyCrypto.unlockMemberPrivateKey(uid, replacement, newPhrase)
        val recovered = OrganizationKeyCrypto.unwrapOrganizationDataKey(grant, privateKey)
        assertEquals("existing clinical record", SyntheticCrypto.decrypt(record, recordIv, SyntheticCrypto.decrypt(history, historyIv, recovered)))
        val oldPrivateKey = OrganizationKeyCrypto.unlockMemberPrivateKey(uid, oldMaterial, oldPhrase)
        assertFails { OrganizationKeyCrypto.unwrapOrganizationDataKey(grant, oldPrivateKey) }
        assertFails { OrganizationKeyCrypto.unlockMemberPrivateKey(uid, replacement, oldPhrase) }
        val nextKey = SyntheticCrypto.generateKey()
        val nextGrant = OrganizationKeyCrypto.wrapOrganizationDataKey(nextKey, replacement.publicKeyJwk, replacement.publicKeyFingerprint)
        assertEquals(nextKey, OrganizationKeyCrypto.unwrapOrganizationDataKey(nextGrant, privateKey))
    }

    @Test fun generatedFingerprintsMatchRulesAndIndependentVerificationRejectsSubstitution() = runTest {
        val phrase = OrganizationKeyCrypto.generateRecoveryPhrase()
        val material = OrganizationKeyCrypto.createMemberKeyMaterial("test-user", phrase)
        assertTrue(material.publicKeyFingerprint.matches(Regex("^[A-Za-z0-9_-]{43}$")))
        val dataKey = OrganizationKeyCrypto.generateOrganizationDataKey()
        val wrapped = OrganizationKeyCrypto.wrapOrganizationDataKey(dataKey, material.publicKeyJwk, material.publicKeyFingerprint)
        val privateKey = OrganizationKeyCrypto.unlockMemberPrivateKey("test-user", material, phrase)
        assertEquals(dataKey, OrganizationKeyCrypto.unwrapOrganizationDataKey(wrapped, privateKey))
        assertFailsWith<IllegalArgumentException> {
            OrganizationKeyCrypto.wrapOrganizationDataKey(dataKey, material.publicKeyJwk, "A".repeat(43))
        }
    }
    @Test fun successorKeyUnlocksHistoryButPriorKeyCannotUnlockFutureRecords() = runTest {
        val old = SyntheticCrypto.generateKey()
        val next = SyntheticCrypto.generateKey()
        val (bridge, iv) = SyntheticCrypto.encrypt(old, next)
        assertEquals(old, SyntheticCrypto.decrypt(bridge, iv, next))
        val (future, futureIv) = SyntheticCrypto.encrypt("future", next)
        assertFails { SyntheticCrypto.decrypt(future, futureIv, old) }
        val ring = OrganizationKeyRing(2, mapOf(1 to old, 2 to next)).encode()
        assertEquals(next, OrganizationKeyRing.current(ring))
        assertEquals(old, OrganizationKeyRing.key(ring, 1))
    }
}
