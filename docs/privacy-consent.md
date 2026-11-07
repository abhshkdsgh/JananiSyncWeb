# Account privacy decisions and evidence

The policy effective 30 October 2026 is displayed by `PrivacyPolicyDialog` (version 1.2; versions 1.0 and 1.1 are retained in the archive). The current decision presentation is version 2; it records the chosen action, account, policy version/hash, and server receipt time without placing separate acceptance and denial sentences above the policy. The header provides a read-only copy before sign-in. The sign-up form discloses that submission provisions a Firebase sign-in account and sends a verification email, then requires an explicit policy decision before organization access. Pressing Deny does not create an account as a side effect. An unauthenticated visitor reading or closing the policy is not logged as accepting or denying it.

The provisional account is necessary to associate a decision with an authenticated UID. Acceptance does not itself verify the email address; email verification and organization approval/key grants are separate requirements. Denied accounts retain their sign-in and history, remain blocked, and can sign out, review again, or request account deletion by email. Existing accounts without a current-policy receipt receive the same gate. No existing acceptance is fabricated or backfilled.

## Storage and access

- `users/{uid}/privacyEvents/{eventId}` is the append-only decision history. It stores UID, token-bound email and verification status at the decision, accepted/denied, exact policy version/hash, presentation/schema version, source, server `createdAt`, and labelled client-reported clock/browser/language fields.
- `users/{uid}/privacy/current` is a mutable current receipt pointing to the newest committed event. It stores UID, event ID, decision, version/hash, and server `updatedAt`.
- An online Firestore transaction creates an event and updates its receipt together. Rules require matching identity, decision, policy, and server timestamps. A receipt cannot point to an old event, and an event cannot be created without its receipt update. Clients cannot modify or delete history. Account owners can read their own history, including before email verification, with a query limit of 100.
- Only server-confirmed reads can newly grant account access. Failure, missing receipts, stale policy versions/hashes, and denial block the account. A live receipt observer handles later decisions in other tabs; after a decision, a fresh server check and subscription prevent an older readback from hiding a newer denial.
- `verifiedUser()` in Firestore rules also checks current-policy acceptance. A modified client cannot obtain organization/clinical access merely by hiding the dialog. This adds a prerequisite to all existing uses of that helper and an additional rule lookup; clinical multi-cell writes were tested against Firestore evaluation limits.

Settings provides a read-only policy, Change privacy decision, and Privacy decision history. Changing to denial stops local clinical use immediately; it is reported as recorded only after server confirmation. If saving fails, access stays blocked locally and the UI offers retry, history, or sign-out. The history dialog lists the newest 100 server records, including exact UTC times and hashes, with selectable details. Older events remain in Firestore and can be retrieved by an authorized operator under the applicable request/evidence procedure.

The policy text is in `docs/privacy-policy.md`; `scripts/generate_privacy_policy.py` produces the Kotlin text/constants and immutable version archive. `docs/privacy/1.0.md` preserves exact text bytes. `docs/privacy/1.0-manifest.json` preserves the original release wording. `docs/privacy/1.2-presentation-2-manifest.json` identifies the current presentation version and decision semantics; earlier manifests remain unchanged. Run:

```sh
python3 scripts/generate_privacy_policy.py --check
python3 -B -m unittest discover -s scripts -p 'test_*.py'
./gradlew :shared:jsBrowserTest --tests '*Privacy*' :webApp:compileKotlinJs
npm run test:security
```

Use a new policy version and retain old archives for a changed published notice. Update both Firestore policy pins and the app together; the generator check detects mismatched rules/content. Increase the presentation version and retain its manifest when decision wording or presentation semantics change. Review the validator and evidence schema before releasing such changes.

## Evidence limits and preservation

These are authenticated account-action records, not independent notarization, digital signatures by the individual, proof of reading, or patient consent. An initial decision with `emailVerified=false` must remain labelled unverified even if the account is later verified. Browser language, user-agent, source, and client clock are context supplied by the client; they are not trusted identity or location evidence. No IP address is collected into the consent event. Providers may process connection data separately as disclosed in the policy.

Firestore server time and client-rule immutability strengthen integrity against ordinary clients. Privileged Admin SDK/project access can bypass those rules. Production evidence preservation therefore requires controlled administrator access, deployment/version records, appropriate audit logging and backup/export controls, an approved retention schedule, and a record of who retrieved evidence and how. Preserve raw events, their matching policy text/manifest, and relevant account-verification context. Verify the archived policy SHA-256 against each event before relying on it; do not silently rewrite or relabel historical records. Have the responsible legal adviser assess admissibility and any required certification for a particular proceeding.

Revocation cannot erase screenshots, downloads, old plaintext, or keys already copied. An offline client that already had authorized data may not receive a new denial until it reconnects. Initial receipt checks and new decisions require network access; signing in offline without a server-confirmed receipt does not grant access.

## Rollout

No production services were changed by this implementation. The metadata lookup for the configured `(default)` database failed because saved Firebase CLI credentials were expired; live database metadata was not independently checked. Existing native Firestore SDK patterns and the supplied Mumbai location were retained without provisioning a database.

Release the app and rules as a coordinated update after local checks and operational review. New rules require every existing user, including organization administrators, to record current-policy acceptance; older app versions cannot do that and will lose server access. Schedule the rollout accordingly. Do not seed fabricated acceptance records to bypass the gate.

Deploying only the app leaves decisions denied by old rules; deploying only the rules blocks old clients. Check the actual web distribution/hosting pipeline: the repository's Firebase Hosting configuration still points to `public`, while the Compose app is built from `webApp`. Any public standalone HTML policy must also account for the service worker's app-shell fallback. The overlay itself does not change hosting.

Before real patient use, complete the operational items in [privacy-policy-review.md](privacy-policy-review.md), including retention, deletion requests, incident handling, provider arrangements, and appropriate notices. The UI and audit trail do not replace those responsibilities.
