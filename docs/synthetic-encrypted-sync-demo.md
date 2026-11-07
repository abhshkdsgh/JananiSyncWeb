# Synthetic encrypted sync walkthrough

This demo uses a fixed, clearly labeled fake observation. It does not use the production clinical-record paths and must not be used with patient information.

The signed-in clinical flow reads encrypted ward and woman envelopes from `organizations/{orgId}/wards` and `organizations/{orgId}/women`. The client requires the selected organization's unlocked AES-GCM key to decrypt roster data or create a roster record. Every admission gets a cryptographically random 128-bit opaque `admissionId`, generated once and persisted inside the encrypted woman payload; it is the session key for all observations in that admission. It is independent of the CR/IP numbers and must not be regenerated when editing or transferring that admission. Patient identifiers, contact details, and clinical fields are encrypted; Firestore document IDs are random and contain no patient identifier. Envelope metadata includes author UID, key/schema versions, algorithm, IV, and server creation time. Active doctors and nurses can read and create envelopes only in their selected organization; updates and deletes are denied. Existing plaintext roster documents must be re-entered through the encrypted client path before use. LCG observations are append-only documents with a random opaque observation ID in the encrypted body. Their entire clinical payload, admission ID, and author UID are encrypted; the envelope separately records authenticated `createdBy` and server `createdAt` for audit and sync ordering. Updates and deletes are denied by rules. Timestamps inside the encrypted clinical payload are ISO-8601 strings, date of birth is `YYYYMMDD`, and contact numbers are strings.

## Prepare two accounts

1. In Firebase Auth, create/verify two test accounts.
2. Provision both as active members of the same organization, one with the `doctor` role and the other with the `nurse` role. The organization document and membership are still managed through the existing admin workflow.
3. Open JananiSync in two separate browser profiles so each profile can remain signed in as a different account.

## Send while offline

1. Sign in as the first approved clinician and open **Two-account encrypted sync demo**.
2. Select **Generate demo key** and transfer the displayed base64 key to the second test account through a test-only channel.
3. Select **Simulate offline**, then **Encrypt and publish fake observation**. The browser encrypts a fixed fake LCG record with AES-GCM and queues only the ciphertext envelope in Firestore's local cache.
4. Select **Reconnect Firestore**. The listener should move from pending local writes to a synced snapshot.

## Receive on the second account

1. Sign in to the second browser profile as the other approved clinician.
2. Enter the same demo key. The listener observes the shared ciphertext document and decrypts it in that browser.

The fixed demo document is at `organizations/{orgId}/clinicalData/v1/observations/latest`. The Labour Care Guide writes append-only envelopes at `organizations/{orgId}/clinicalData/v1/lcgObservations/{observationId}`. Active clinical members can read; creates freeze the author's access level and server timestamp in the envelope, while updates and deletes are denied. A correction appends a `cellCorrection` event to `lcgObservations`, replaces selected values in the current confirmed-column snapshot, and records an encrypted mandatory reason. Access must be at least 5 points above the original confirmer’s frozen access level. Original events and confirmation metadata remain unchanged, and each touched cell’s history includes the correction. The former `lcgCorrections` note collection is read-only for historical records. The encrypted roster paths are described above. Other unmodeled clinical collections and key-envelope paths remain denied.

## Scope and deployment notes

The displayed demo key is plaintext so it can be manually moved between test accounts. This demonstrates encryption plus offline sync, not the production public-key envelope, recovery phrase, or membership key-grant design. Do not use this flow as production key delivery.

The Firestore rules are a local prototype and must be deployed before using these paths against the hosted project. After reviewing them, sign in to the Firebase CLI if needed and run `firebase deploy --only firestore:rules`. The development environment's Firebase CLI previously needed reauthentication, so the rules may not yet be deployed to the hosted project.
