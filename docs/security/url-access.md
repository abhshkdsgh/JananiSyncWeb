# URL access protection

An address-bar route selects a page; it never grants permissions. Names, clinical data,
roles, access levels, and organization access codes are not route arguments.

The UI requires verified authentication, validates route IDs, and resolves wards and
admissions only from the current organization's decrypted roster. An admission must
belong to the selected ward. Loading/error roster snapshots cannot select a patient.
Roster repositories are recreated when the organization or signed-in identity changes.
The guide waits for the clinical session to match the selected organization/admission.

Firestore remains the authorization boundary even if someone changes browser code or
sends requests directly. The checked-in rules require verified authentication and active
organization membership, and require a doctor/nurse role for clinical reads. Membership
changes, key grants, ward administration, and clinical writes have separate role and
validation checks. All unmodeled paths are denied.

The current policy permits clinicians to read all records in their organization.
Selecting another authorized patient's URL is therefore allowed. Per-patient or per-ward
assignment restrictions would require a different server authorization model; URL
validation alone cannot provide them.

## Verification

Run `npm run test:security` from the repository root. It uses the Firebase CLI, Java,
Node's built-in test runner, and the Firestore emulator under `demo-jananisync`.
The REST test refuses to run against a non-local endpoint. It sends requests directly,
without using the application's UI, and checks document reads, list queries,
cross-organization reads, account verification, inactive membership, and attempted
privilege escalation. Fixture writes use the emulator's owner token only for setup.
No real project or patient data is used.

Run the Kotlin JS/Wasm browser test tasks for malformed-route, wrong-ward admission,
loading/error roster, and membership-role regressions. These UI checks supplement the
server tests; they do not replace authorization rules.

These tests load the local `firestore.rules`. They do not verify the rules currently
deployed in Firebase, other deployments, IAM access, or every possible vulnerability.

See `url-access-review.json` for the focused audit assessment and the existing
admin roster workflow mismatch found while reviewing the rules. No Firestore rules
were changed or deployed as part of route hardening.
