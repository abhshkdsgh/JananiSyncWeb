# Client-only organization key rotation

The JavaScript client implements resumable key rotation without Cloud Functions.
Deploy the updated client and Firestore rules together before using the flow.
Older clients cannot read grants stored by this protocol and must reload the new
application before rotation begins. This change does not deploy either artifact.

## Verified recipients and private state

On admin unlock, the browser derives a domain-separated 256-bit control key from
the recovery-decrypted RSA private exponent, organization ID and admin UID. The
control key is separate from the organization DEK and is never sent to Firebase.
Sign-out clears it. Knowing an old shared organization key does not allow a
removed member to forge recipient records or rotation progress.

A recipient fingerprint is computed from their Firestore public JWK. On first
approval or first rotation, the browser trusts that profile and encrypts and authenticates
the verified org/admin/recipient/fingerprint/JWK binding in
`rotationRecipients/{uid}` under the admin-only control key. Existing members
with no pin are automatically enrolled at rotation; subsequent key changes are
detected against the authenticated pin and block rotation before revocation starts.
This intentionally removes independent first-use verification: a malicious
backend can substitute a key before its first pin, but cannot forge the
admin-authenticated pin or silently replace a key after pinning.

This implementation expects exactly one active admin. It supports up to 500
remaining active recipients, an encrypted journal of at most 900,000 characters,
and a source roster of at most 1,000 total members. Exceeding a limit stops before
revocation. The same admin recovery material is needed to resume the operation;
there is no private-key replacement or support recovery mechanism added here.

## Durable protocol and atomic activation

The admin dashboard also provides **Rotate organization key** for a manual update
without removing anyone. After confirmation, the same resumable protocol includes
every active member and changes no membership status. Removal-triggered journals
retain the removed UID; manual journals use an empty `removedUserId`.

The **Access Update** linear progress indicator shows grant preparation plus one
final verification/activation step. The percentage remains below 100% while
maintenance is active, then shows 100% when the final transaction succeeds.
Progress is read from the durable maintenance record and survives reconnects.

1. The admin verifies that all remaining active members have authenticated
   recipient records. The browser generates a new DEK and encrypts a journal
   containing the old/new key and recipient roster under the admin control key.
2. One online Firestore transaction stores the journal in `keyRotations/{id}`,
   starts `keyMaintenance/current`, and deactivates the removed member. The
   current key version is still unchanged. Clinical reads/writes and membership
   changes are denied during maintenance.
3. After the freeze is confirmed, the browser rereads the complete member
   collection from the server, reconciles the remaining recipients, and seals
   the frozen journal. This handles membership changes racing the initial
   snapshot. If a newly approved member lacks verification, the admin can verify
   their fingerprint while the journal is still preparing, then resume.
4. For each frozen recipient the browser creates a new RSA-wrapped DEK and an
   admin-authenticated proof binding the operation, recipient, fingerprint,
   target version and ciphertext. A bounded online transaction saves an
   immutable `keyRotations/{id}/grants/{uid}` and advances the progress counter.
   Rules require the next exact recipient slot, preventing skips or overwrites.
5. The browser rereads all grants from the server, verifies every proof and
   ciphertext binding, and compares the frozen roster against current active
   membership. A single final transaction publishes the new key version and
   rotation pointer, stores the encrypted history bridge, completes the journal,
   and exits maintenance. Members can read their staged grant only after this
   activation. All intended grants become available together.

Rules enforce frozen membership, ordered progress and completeness relative to
the admin's frozen manifest. The client checks that manifest against a fresh
server query before activation. Firebase remains the authority for membership
metadata; a malicious backend can omit membership data or deny service. These
checks do not claim to provide independent roster transparency or backend
availability. Recipient authenticity remains anchored in the admin's recovery
secret and the separately verified fingerprints.

## Interruption, sign-out and maintenance

Rotation writes use server transactions, not offline queued batches. If a browser
closes, loses its connection or signs out, completed progress and the encrypted
journal remain in Firestore. The admin's next authenticated session discovers
maintenance. Unlocking with their personal recovery phrase recreates the control
key and resumes from the next committed recipient; no plaintext DEK, phrase or
RSA private key is persisted for resumption. A live unlocked client also retries
interrupted maintenance, and the dashboard provides **Resume access update**.

Other members see **Maintenance in progress** and a request to ask their admin to
come online and unlock their account. Clinical content and its listeners are
suspended until maintenance ends. Members unlock the new organization key after
activation. A disconnected device cannot learn of maintenance until it reconnects;
server rules reject stale writes. Previously copied keys/plaintext cannot be
revoked retroactively.

## Validation

The emulator suite covers atomic revocation/start, paused clinical access and
membership changes, ordered immutable grant delivery, attempts to activate early,
replayed operations, recipient isolation and atomic availability at completion.
Browser tests exercise authenticated journal persistence, a new protocol instance
after session interruption, recovery-based unlock, exact-once deliveries, key
history decryption, automatic first-use recipient pinning, changed-key detection,
tampered grants and maintenance UI.
The protocol and rules need independent security review before clinical rollout.
