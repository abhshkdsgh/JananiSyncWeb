# Member recovery key replacement

Active doctors and nurses can use **Replace lost recovery key** in Settings beside Public-key fingerprint. The browser generates a fresh RSA keypair and a new 12-word recovery phrase. The member must save the phrase before submitting. The request applies only to the selected organization; the Firebase Auth UID, membership, role, access level, and clinical authorship remain unchanged.

The request contains the public key, its fingerprint, and a private-key backup encrypted with the new phrase. The phrase and plaintext private key never leave the browser. Existing key material remains registered while the request is pending. Members can cancel pending requests; administrators can reject them.

The administrator opens the organization admin console’s Requests tab, unlocks their own key, verifies the member's identity and compares the displayed replacement fingerprint through a trusted channel. After explicit confirmation, an online Firestore transaction atomically replaces the member's public profile and encrypted backup, wraps the existing current organization key for the new public key, replaces the encrypted rotation-recipient pin, and marks the request approved. Approval checks the request ID, previous fingerprint, active clinical membership, organization key version, and maintenance state. Concurrent request changes or rotation invalidate the transaction and require retrying.

The member can then unlock existing records with the new phrase. The current organization key unlocks historical key versions through the existing history bridges. Future rotations use the replacement recipient pin. Replacement does not revoke organization keys already copied to other devices; existing unlocked sessions can continue using those keys. Use the existing access revocation/rotation workflow for suspected compromise.

Administrators cannot replace their own key through this flow. It requires an administrator who can unlock the existing organization key. This flow cannot recover an organization whose only usable keys have all been lost.

Deploy the updated `firestore.rules` together with the app feature. Existing rules intentionally reject public-profile replacement. No production deployment is performed by the implementation or the tests.

Validation:

- `npm run test:security` uses only the `demo-jananisync` Firestore emulator. Tests cover atomic approval, same-version regrant, unchanged membership, rejected partial writes, self-approval, replay, cancellation, rejection, malformed requests, stale identities, inactive memberships, and maintenance.
- `CHROME_BIN='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' ./gradlew :shared:jsBrowserTest` covers phrase confirmation, UI approval, delayed operations after organization switching, existing-record decryption, and rotation with the replacement recipient.

## Multiple organizations

Settings and the fingerprint dialog show the selected organization's name and code. The recovery form repeats that context and asks users to label each saved phrase with its organization. Switching organizations clears the displayed fingerprint, closes its dialog, and discards unsubmitted key preparation. Delayed verification results from an earlier organization context are ignored, including after switching away and back.

When joining an organization that has no registered key for the account, preparing secure access generates a separate key and recovery phrase. To reuse an existing key instead, the user explicitly selects a source organization and supplies its recovery phrase. The browser fetches the source material from the server and verifies that the phrase unlocks the registered private key before preparing the request. The phrase is cleared from the input on submission and never stored in view-model state or sent to Firebase. A target organization's existing registered key is preserved; this joining flow cannot replace it with another organization's key.
