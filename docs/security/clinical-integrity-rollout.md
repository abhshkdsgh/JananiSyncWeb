# Clinical integrity and key lifecycle rollout

This change is local until deliberately deployed. It changes the write contract; deploy the client and rules together under maintenance. Existing schema-1 clinical events remain readable. New clinical events and roster archive actions use schema 2. Roster ward/admission envelopes retain their existing schema and acquire the current key version on new writes.

## Storage and authorization

- `lcgObservations` remains the immutable audit log. Schema-2 ciphertext contains only declared cell values and column identity. Client readers validate declarations and reconstruct columns from those values, including legacy snapshots. Unrelated snapshot fields cannot change a protected cell.
- `lcgColumns` freezes the roster document, session identifier, timestamp, chart origin and column index. The client checks ciphertext identity against this metadata. Roster views also compare the session to the decrypted admission.
- `lcgCells/{columnId}~{cellKey}` stores the latest immutable event for each cell. Rules require matching event/projection writes in the same commit and reject projection forgery, replay and undeclared fields. Sign-off and transition projections are separate cells. Roster views listen to these current cells; a selected admission loads its immutable history with a `womanDocumentId` query. Organization-wide historical event listeners are removed.
- Per-cell permissions treat original hourly keys and their supported first-half aliases as the same protected reading. Second-half access remains independent. Unknown first-half aliases are rejected.
- LCG, Partograph, corrections and repositories share value validation. Blank means explicit erase. Existing schema-1 clinical values are preserved for review rather than silently changed. No clinical schedule or treatment recommendation is introduced.
- Both charts use one stage-aware schedule and column index. The Partograph offers full-dilation and birth actions using the LCG confirmation workflow. Earlier gaps and original timestamps remain available.

## Migrate existing records

1. Back up the database and stop all writers, including old browser tabs. Keep maintenance in effect until all steps and verification are complete.
2. Use a trusted administrator environment with Application Default Credentials. Supply keys through `JANANISYNC_ORG_AES_KEY_BASE64` for version 1, or `JANANISYNC_ORG_KEYS_JSON` containing a JSON object mapping version numbers to Base64 keys. Do not put keys in CLI arguments, source control, logs or browser consoles.
3. Run `node scripts/maintenance/migrate-clinical-events.cjs ORG_CODE` for a dry-run. It decrypts and checks complete event history, revisions and admission bindings before any write. Resolve inconsistent/unbound legacy history explicitly; do not invent patient mappings.
4. Run the same command with `--apply` while writers remain stopped. It backfills query metadata, frozen column identity and per-cell access levels, then builds current-cell projections. Batches use update-time preconditions. After interruption, run a fresh dry-run and resume. Partial migration must not be exposed as a complete roster.
5. Run dry-run again and compare expected column/current-cell counts. Verify a representative admission's displayed readings, corrections, sign-offs, stage transition and audit history against the backup.
6. Deploy the index in `firestore.indexes.json`, updated rules and matching client during the maintenance window. The archive-history query uses a composite index on roster document and descending revision.
7. Force old clients to reload before releasing maintenance. Old schema-1 clinical writes are denied by the new rules. Roster status must not be interpreted until its current-cell projections have been migrated.

The utility does not rewrite historical ciphertext or retimestamp observations. It refuses missing, conflicting or duplicate event history. It is intended for a reviewed maintenance window, not an unattended production backfill.

## Key lifecycle

Clinical and roster writes carry the actual current DEK version. Rules reject stale versions. Deactivation atomically sets membership inactive, advances key metadata, creates an immutable history bridge (old DEK encrypted with its successor) and grants the next key to the initiating administrator. The administrator must unlock the current key and recovery-derived control key in the current session. If the commit fails, revocation/rotation do not partially apply.

Other members are locked when they receive the version change. Their administrator issues each new envelope to the member's registered Firestore public key; the client computes fingerprints and automatically pins any key not previously registered. No manual fingerprint entry is required. A previously pinned key that changes blocks rotation. Trusting the first-seen Firestore profile means a malicious backend could substitute a key before that first pin and intercept that member's envelope. Unlocking the current envelope walks the encrypted history chain to read authorized historical data. This preserves remaining members' historical access; it is not selective historical-access revocation or historical ciphertext re-encryption. A former member's old DEK cannot decrypt future-version ciphertext, but copied historical plaintext and keys cannot be revoked.

The optional remembered-device cache stores the key ring inside its existing encrypted storage. Restored caches are checked against current server metadata before use; a version mismatch clears them. Sign-out and membership deactivation clear in-memory keys and the relevant remembered cache when the client learns about the change. A disconnected browser cannot learn revocation until reconnecting; server rules still reject its stale writes.

New public-key fingerprints use Base64URL and are computed by the client from each Firestore public JWK. On first grant or rotation, the admin client accepts that profile and saves an authenticated pin encrypted under the admin-only control key; no separate fingerprint exchange is required. This improves usability but no longer protects against a malicious backend substituting a key before the first pin. Later substitution of an already pinned key is detected. Browser code delivery and the administrator/member devices remain trusted.

## Availability and performance

Unreadable roster records are isolated and their document IDs shown in an incomplete-roster warning. Healthy admissions remain visible. Archive history is fetched in 50-action pages for the requested woman; it is no longer downloaded for every roster screen. Admission lists render 50 cards per page. Decrypted records and imported AES keys are reused within the session; superseded decode jobs are cancelled, and column events are indexed once.

Current-cell and roster snapshots still scale with the organization's current stored admissions and columns. Very large installations should additionally partition these current projections by ward/active admission and benchmark representative datasets. Full immutable clinical history is scoped to one selected admission.

## Verification

Run:

- `./gradlew :shared:jsBrowserTest :webApp:jsBrowserProductionWebpack`
- `npm run test:security`
- `node --test tests/security/*migration.test.cjs tests/export/chart-pdf.test.cjs`

The local emulator validates rules in Standard edition. Production database inspection requires valid Firebase credentials; no production data, key material or deployed configuration is modified by these tests. Review the prototype rules and rehearse the migration against a disposable copy before deployment.
