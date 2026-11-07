# Per-cell access rollout

The new client writes `lcgColumns.cellAccessLevels` and each new cell event's
`cellKeys`. Existing encrypted events remain unchanged. Firestore cannot inspect
the encrypted graph payloads, so old history must be migrated by a trusted
administrator before enabling the new rules. Client reconstruction alone does
not enforce historical permissions against direct API requests.

1. Stop clinical writes for the organization and back up Firestore. Keep writes
   paused through migration, rules deployment, and client rollout; older clients
   cannot supply the new required metadata.
2. Install the maintenance dependencies with `npm install --prefix scripts/maintenance`. Configure Application Default
   Credentials with authorized Firestore administrator access and the intended
   Google Cloud project (`GOOGLE_CLOUD_PROJECT`).
3. If history contains graph events, securely provide the organization's raw
   32-byte AES key through `JANANISYNC_ORG_AES_KEY_BASE64`. Do not put the key on
   the command line or commit it to a file. It is used only in process memory.
4. From the repository root run the default dry-run:
   `node scripts/maintenance/migrate-cell-access.cjs ORG_ID`.
   Every column is reconstructed by revision before any writes. Missing keys,
   invalid payloads, duplicate revisions, and incomplete history abort the run.
5. Review the target organization and validated column count, then run the same
   command with `--apply`. A single transaction checks every column's original
   update time before writing all maps. Concurrent column changes abort it.
   Organizations above 450 columns require a separately reviewed maintenance
   migration; this script refuses to partially migrate them.
6. Deploy the updated rules and client, verify permitted and denied edits with
   test accounts, then resume writes. Clear the AES key from the environment.

The script does not deploy anything and has not been run against live data.
Local reconstruction tests: `node --test tests/security/cell-access-migration.test.cjs`.
Rules tests: `npm run test:security` (local emulator only).

Signed-column corrections append `cellCorrection` observation events and atomically
replace only the selected cells. Each requires `correctedRevision` matching the
prior column revision and an encrypted reason (`reasonCiphertext` / `reasonIv`).
An editor must meet each selected cell's latest recorded access level; the
current implementation does not impose an additional offset above the signer's
level. Normal authorized edits also remain available after sign-off.

Sign-offs are separate immutable confirmation events. An unsigned column may be
signed by clinical staff; replacing a sign-off requires a strictly higher access
level than the current signer's level frozen in the referenced event. Later
membership changes do not alter that frozen threshold. Earlier signatures remain
in history. Full-dilation and birth markers on an already signed column are
ordinary cell events that preserve its current signer.

New note-only `lcgCorrections` writes are denied; historical notes remain readable.
The migration reconstructs `cellCorrection` events, so rerunning it preserves
per-cell levels.
Encrypted reason text is validated as nonblank by the client; Firestore validates
its envelope, since it cannot inspect encrypted plaintext.
