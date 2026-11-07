# Provision the first organization admin

The first admin must be added by a trusted Firebase project owner. A user cannot make themselves an admin or approve their own request. After this one-time bootstrap, the admin can approve doctor and nurse requests from the app.

## 1. Create and verify the admin account

The programme admin signs up in JananiSync and verifies their email. In Firebase Console, open **Authentication → Users**, locate that verified email, and copy its **User UID**.

If the administrator already has a verified JananiSync account for another organization, use that existing account and its same **User UID**. A separate sign-up is unnecessary.

## 2. Create the organization

In **Firestore Database → Data**, create the document:

```text
organizations/{CODE}
```

`CODE` must be exactly 8 ASCII letters or digits (for example, `AB12CD34`). This document ID is the access code users enter. Add a `name` string field containing the organization display name (up to 160 characters). Active members can read this organization document to display its name; clients cannot list organizations or write organization documents. Keep this root document limited to the `name` field.

## 3. Seed the initial admin membership

Under that organization document, create:

```text
members/{ADMIN_UID}
```

Use the Firebase Auth UID copied in step 1 as the member document ID, and set these fields:

| Field | Type | Value |
| --- | --- | --- |
| `userId` | string | The same Firebase Auth UID |
| `name` | string | Admin's organization display name |
| `role` | string | `admin` |
| `status` | string | `active` |
| `accessLevel` | integer | A value from `10` to `99` |
| `joinedAt` | timestamp | Current date and time |

This is a trusted console bootstrap. Firestore Security Rules prevent users from creating or changing their own admin membership through the app.

The admin approval dashboard also lets admins assign and change each member's organization access level. The number is currently stored as an organization-scoped ranking value; doctor/nurse/admin roles continue to determine app and Firestore permissions until explicit numeric permission bands are defined.

## 4. Deploy Firestore rules and indexes, then sign in

From the project root, deploy the collection-group indexes:

```bash
firebase deploy --only firestore:rules,firestore:indexes
```

If Firebase CLI asks you to authenticate, run `firebase login` with an account that has permission to publish rules and indexes for `jananisyncweb`. The Labour Care Guide's encrypted observation path is denied until the current rules are deployed. The rules allow reads and creates there only for verified, active doctor/nurse members.

After the indexes finish building, the first admin signs into JananiSync again or reloads the signed-in screen. The membership listener should recognize their active admin record and show the admin approval dashboard. They do not submit a nurse or doctor request for their own organization. If an account has active memberships in multiple organizations, JananiSync asks the user to choose one after sign-in.

### Switch an existing administrator to a second organization

1. Create the second organization and its initial admin membership using steps 2–3 above. Use the existing administrator's Auth UID in both `organizations/{NEW_CODE}/members/{ADMIN_UID}` and that document's `userId` field. Set `role` to `admin` and `status` to `active`, with the other fields and types from the table above.
2. In JananiSync, open the account-name menu and select **Switch organisation** (shown when you have multiple active memberships). Select **Continue** on the new organization's card.
3. If the new organization is missing, reload the page or sign out and back in with that same account. Have the project owner check the exact organization path, member document ID, `userId`, `role`, and `status`, and confirm that the rules and indexes in step 4 are deployed.
4. Open **Admin console** for the selected organization. For a new encryption setup, select **Prepare admin encryption key**, safely record the displayed recovery phrase, confirm that it is saved, and select **Initialise organisation key**. If encryption already exists, enter your own personal recovery phrase registered for this organization and select **Verify and open dashboard**.

Once the active admin membership has been seeded, access is already granted. **Join another organisation** is for requesting a new nurse or doctor membership; no joining request is needed for that bootstrapped admin membership. A message saying **You already have active membership** means to switch to the organization instead.

Encryption access is separate for each organization. Keep your own saved recovery phrase labelled with its organization; the phrase for the first organization is not automatically the phrase for the second.

## 5. Onboard the rest of the team

Give each verified clinician the organization’s 8-character code. They enter their name, select `doctor` or `nurse`, and request access in JananiSync. The active admin reviews the name and role, assigns an access level from `10` to `99`, and approves the request in the dashboard. Admins can later change an existing member’s level from the organization member list.

After joining one organization, a user can request a new nurse or doctor membership with **Join another organisation**. Each organization has its own membership, role, access level, admin approval, and clinical data scope. For an existing active membership, use **Switch organisation** in the account-name menu or on the active dashboard, then **Continue** on the target organization's card.
