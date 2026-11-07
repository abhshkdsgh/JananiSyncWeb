# JananiSync organization key and recovery policy (implementation target)

This is a proposed implementation policy for review with the clinical and security owners before clinical records are enabled. It treats Firebase as an authorization, storage, and sync service; Firebase must receive ciphertext for clinical content, not plaintext keys or observations.

## Security boundary

Client-side encryption can keep clinical content unreadable to Firebase. Firestore Rules still need plaintext authorization metadata, so Firebase will see organization document IDs/access codes, Firebase UIDs, roles, membership/request statuses, document paths, key versions, and timing/size metadata. Do not describe this as hiding all application metadata from Firebase.

## Key hierarchy

- Each organization has one random 256-bit AES-GCM data-encryption key (DEK), generated in an authorized browser. All approved members receive access to that organization-wide key; separate organizations have separate keys. Generate a fresh random nonce for every encryption. Store only ciphertext, nonce, algorithm/schema version, and key version with records.
- Each member has an RSA-OAEP-3072/SHA-256 key pair. The public key and fingerprint can be read by organization administrators for key grants. The private key is encrypted on the client using a key derived from the member's 12-word recovery phrase; only that encrypted backup may be stored.
- Store one versioned DEK envelope per organization and member. The envelope contains the DEK encrypted to that member's public key. Neither the plaintext DEK nor the recovery phrase is sent to Firebase.
- Firestore Rules independently enforce active membership for data and envelope reads/writes. Possessing a decryption key does not grant server access or write permission.
- This is organization-wide confidentiality: all active members granted the current key can decrypt the same organization's records. Role rules still determine who may perform actions.

## Organization-wide access model

| Capability | Admin | Nurse | Doctor |
| --- | --- | --- | --- |
| Organization DEK: decrypt organization data | Yes, while active and granted | Yes, while active and granted | Yes, while active and granted |
| Read/write clinical records | According to role permissions | According to role permissions | According to role permissions |
| Approve/reject requests and grant key envelopes | Yes | No | No |
| Change another member's role or membership | Yes | No | No |

Organization key access is shared across active members after an admin grants an envelope. Role permissions separately control which clinical actions members can perform. A member with multiple responsibilities should receive the same organization key envelope once; role/capability modeling can be refined independently.

## Member approval and key delivery

1. A verified user creates a member key pair on their device. The access request, organization-scoped public profile, and recovery-encrypted private-key backup are queued as one Firestore batch. Never upload the recovery phrase or an unwrapped private key.
2. An admin approves the user's requested organization role.
3. An authorized key custodian approves the request, unlocks their own private key and organization DEK in the client, and encrypts the current DEK to the public key in the member's Firestore profile. The client computes the key fingerprint and stores an authenticated recipient pin for later rotations.
4. The member receives only their own envelope. The client unlocks the private key with the recovery phrase and unwraps the DEK. The DEK stays in memory for the session. If the member selects **Remember this device**, the browser also stores the DEK encrypted under a non-extractable Web Crypto AES key in IndexedDB, so a reload in that browser profile does not require the phrase again. The recovery phrase and private RSA key are never stored by this convenience feature. Firestore Rules still gate record access and writes.

### Implemented storage paths (prototype)

- `organizations/{orgId}/keyProfiles/{uid}` stores the member's public JWK and fingerprint. It is write-once from that member's verified account; organization admins can read it to prepare a grant.
- `organizations/{orgId}/keyBackups/{uid}` stores only the encrypted private-key backup and recovery KDF parameters. Only that verified UID can read or replace it.
- `organizations/{orgId}/keyMetadata/current` stores the current key version and non-secret algorithm metadata. Active members can read; admins control initial creation and sequential version changes.
- `organizations/{orgId}/keyEnvelopes/{uid}` stores the organization key wrapped to that member's public key. The member and admins can read; only admins can issue or replace envelopes.

The join screen now generates and displays a recovery phrase before submitting access, requires the user to confirm it is recorded, and submits the key material with the request. When joining another organization from an existing active account, the same member key and recovery backup are reused. The admin dashboard can initialize the first organization key, unlock it with the recovery phrase, and grant a current key envelope to active members. Active members can unlock the organization key from the clinical flow; an optional remembered-device cache restores it after reload. Versioned key rotation, current-key write checks, cell-level event reduction and automatic recipient-key pinning are implemented locally. See [client-only key rotation](client-only-key-rotation-design.md) and [clinical integrity rollout](security/clinical-integrity-rollout.md) for the protocol and coordinated deployment. Production clinical use still requires review and deployment verification; development and test records must remain synthetic.

The current UX deliberately trusts the Firestore public-key profile when an admin first approves a member or first encounters an unpinned active member during rotation. The client computes the fingerprint from the JWK and pins that binding under the admin-only control key. This removes out-of-band verification friction, but a malicious or compromised Firebase backend can substitute a key before that first pin and intercept the corresponding organization-key envelope. Once pinned, a changed profile is detected and rotation stops rather than silently accepting the replacement. This is a deliberate reduction in first-use authenticity, not protection against a malicious backend; an independently verifiable key-transparency or managed identity system would be needed to restore that guarantee.

## Twelve-word recovery phrase

- Generate the mnemonic from cryptographically secure random entropy; do not accept a user-invented phrase. If using BIP-39, 12 words encode 128 bits of entropy plus a checksum.
- Generate the phrase from cryptographically secure entropy, normalize it consistently, and use a reviewed, versioned key-derivation scheme with a random salt to derive a 256-bit recovery wrapping key. Do not use the Firebase password, a bare hash, or a hand-written KDF.
- Encrypt the user's private-key backup with that wrapping key using AES-GCM. Store only the encrypted private key, nonce, salt, and KDF/format versions. Require the user to confirm the phrase before enabling the account for clinical use.
- The phrase is a recovery secret, not an account password. Anyone who obtains it and the encrypted private-key backup can unlock that key. Never transmit it, log it, or store it in Firestore, DataStore, localStorage, or analytics. The optional remembered-device cache stores only an encrypted organization DEK alongside a non-extractable local wrapping key in IndexedDB; same-origin malicious script or someone using the unlocked browser profile may still use that key, so enable it only on a managed or dedicated device.
- If both the phrase and every unlocked device copy are lost, support cannot recover that private key. Existing authorized key custodians can instead issue a new key pair and re-wrap still-available organization scope keys to it. If no authorized copy of a scope key remains, data encrypted only under that key is permanently unrecoverable.

## Loss, compromise, and revocation behavior

| Event | Required response | What cannot be promised |
| --- | --- | --- |
| Device lost, phrase safe | Sign out/revoke the device session; restore the encrypted private-key backup on a replacement device using the phrase; re-fetch envelopes after online authorization. | Remote deletion of data already copied off the device. |
| Phrase forgotten, an unlocked authorized device remains | From that device, generate a new phrase and re-wrap the private key; replace the encrypted backup and retire the old backup. | Recovery if the device is locked/erased and no other authorized device can re-wrap the organization key envelopes. |
| Phrase or private key suspected compromised | Revoke the public key, generate a new user key pair, and have the admin re-wrap the current organization key to the new public key. Rotate the organization key for future records. | Making old plaintext or old key material already copied by the attacker unreadable. |
| Member removed | Set membership inactive so server Rules deny future reads/writes; stop listeners; retire their current envelope; rotate the organization DEK and issue the new version only to remaining members. Re-encrypt historical ciphertext if the organization requires future access to that history to be withheld. | Revoking old keys, cached ciphertext, or plaintext from an offline device or copy the member already controls. |
| Role downgraded | Keep the organization key grant if the member remains active; enforce role changes through Firestore Rules. If they should lose all organization data access, deactivate membership and rotate the key. | Taking away historical data the person already decrypted. |
| All key custodians unavailable | Use a pre-approved organizational recovery quorum, if configured; otherwise data without any surviving key copy is lost. | A Firebase/support “password reset” that recovers zero-knowledge keys without a trusted recovery secret. |

Firestore Rules provide online authorization changes once they reach the server. Offline Firestore persistence may retain cached ciphertext, so logout/removal must clear decrypted keys from application memory and any local key cache. Explicit sign-out clears the remembered-device cache; a membership deactivation clears that organization's cached key when the client receives the updated membership snapshot. A disconnected device cannot learn about revocation until it reconnects. A web app cannot reliably erase plaintext a user copied or a key a compromised device extracted. Key rotation protects future data and future grants; it does not retroactively revoke past access. The browser's same-origin JavaScript can use the remembered key, so this is convenience against routine reloads, not protection against XSS, a compromised browser profile, or an attacker with access to the active workstation.

## Before enabling clinical writes

Implement and review this lifecycle with synthetic records: create a member, approve them, grant envelopes, decrypt a record on their device, remove them, rotate keys, and verify that a fresh server request is denied while the documented offline/history limits remain true. Keep clinical Firestore paths denied until these transitions and the recovery UX are implemented and reviewed.
