# JananiSync User Manual

**Work in progress — Dummy data only.** Do not enter, upload, or use actual patient or clinical data. This version is for demonstration and testing only and must not be used for clinical care.

**Audience:** authorised nurses, doctors, and organisation administrators

**Application:** JananiSync

**Manual reviewed:** 30 October 2026

This manual explains how to use JananiSync. The options you see depend on your account permissions and the version your organisation uses. Contact your administrator for help with setup.

## Contents

1. [About JananiSync](#1-about-jananisync)
2. [Before you begin](#2-before-you-begin)
3. [Quick start for clinical staff](#3-quick-start-for-clinical-staff)
4. [Accounts and privacy review](#4-accounts-and-privacy-review)
5. [Organisation access and recovery phrases](#5-organisation-access-and-recovery-phrases)
6. [Navigation and appearance](#6-navigation-and-appearance)
7. [Wards and admissions](#7-wards-and-admissions)
8. [Set up an admission chart](#8-set-up-an-admission-chart)
9. [Use the Labour Care Guide](#9-use-the-labour-care-guide)
10. [Use the Partograph](#10-use-the-partograph)
11. [Sign off, review history, and correct entries](#11-sign-off-review-history-and-correct-entries)
12. [Record second stage and birth](#12-record-second-stage-and-birth)
13. [Archive and restore admissions](#13-archive-and-restore-admissions)
14. [Export and print charts](#14-export-and-print-charts)
15. [Connectivity, local storage, and installed use](#15-connectivity-local-storage-and-installed-use)
16. [Administrator tasks](#16-administrator-tasks)
17. [Troubleshooting and support](#17-troubleshooting-and-support)
18. [Glossary](#18-glossary)

## 1. About JananiSync

JananiSync helps healthcare staff manage maternity admissions and document labour care. It provides a ward roster, a WHO Labour Care Guide (LCG) view, a Modified WHO Partograph view, clinical entry histories, column sign-offs, archiving, and PDF/print export.

The LCG and Partograph show the same admission's records. Switching views does not create a separate chart. Some observations are available in only one view, and the two views expose different half-hour recording fields.

Use the application to document observed care under your organisation's clinical procedures. Its alert markings and chart reference lines do not generate treatment decisions or enforce a clinical observation schedule.

### Roles and access levels

| Account status or role | Available work |
| --- | --- |
| Account setup in progress | Complete privacy review, verify email, and request organisation access. |
| Pending applicant | Wait for approval or cancel the joining request. Clinical records are unavailable. |
| Active nurse or doctor | After unlocking patient records, use wards, admit women, record and review observations, sign off eligible columns, and export charts. |
| Active administrator | Perform clinical work and use the Admin console to manage membership, wards, and access to records. |

Each organisation assigns a whole-number **access level from 10 to 99**. Editing a saved cell requires a level at least as high as that cell's recorded level. Different cells and half-hour readings can have different protection levels. Replacing an existing sign-off requires a level strictly higher than the previous signer's level. Archiving and unarchiving require level **60–99**.

Access levels primarily control changes and sign-offs. Clinical reading is not restricted to a member's assigned patients within the organisation.

## 2. Before you begin

Have the following ready:

- The JananiSync website address supplied by your organisation.
- An up-to-date web browser on a secured device.
- An internet connection for initial loading, account setup, verification, privacy acceptance, and organisation access setup.
- Access to your work email inbox.
- Your organisation's eight-character access code, if you need to join.
- Your personal 12-word recovery phrase, if you have already set up secure access.

Keep the device's date, time, and time zone correct. Clinical date/time pickers use local device time and a 24-hour clock; chart setup also shows the time zone. Privacy decision history explicitly displays UTC times.

Your sign-in password and recovery phrase serve different purposes:

| Item | Purpose | Handling |
| --- | --- | --- |
| Email and password | Sign in to your account. | Use only in the account sign-in flow. |
| Personal 12-word recovery phrase | Unlock patient records on a device. | Store privately, labelled with the organisation name. Never give it to another person. |
| Organisation access code | Identify the organisation when applying to join. | Obtain it from an administrator; it does not grant access by itself. |
| Public-key fingerprint | Check that the recovery key belongs to the correct person. | May be shared with your administrator through a trusted way to contact them, such as a phone call. |

## 3. Quick start for clinical staff

1. Open the organisation's JananiSync website and **Log in**, or **Sign up** if you do not have an account.
2. Record your privacy decision and verify your email. Organisation access requires acceptance of the current policy.
3. If needed, enter your name and organisation code, select Nurse or Doctor, prepare secure access, save the recovery phrase, and **Request access**.
4. After administrator approval, enter your personal phrase and select **Access patient records**.
5. Select **Open ward**, find the correct woman, and choose **Open guide** or **Partograph**. Use **Admit a woman** for a new admission.
6. For a new chart, check the start date/time, select **Set chart start**, then **Use this start time**.
7. Record observations. LCG cells save as editing finishes; Partograph marks require **Confirm and save**.
8. Review the saved values and any message about changes waiting to be shared. **Sign off column** when its observations are complete.
9. Use the stage controls at full dilation and birth, verifying the patient and time in each confirmation dialog.
10. Export if required, then open the account-name menu and select **Logout** when finished.

## 4. Accounts and privacy review

### Create an account

1. On the login screen, select **Sign up**.
2. Enter **Email address**, **Password**, and **Confirm password**.
3. Select **Create account and review privacy**. Correct any validation or account-creation error shown.
4. Read the privacy policy and choose **Accept and continue** or **Deny and stop**.
5. Open the verification link sent to your email address.
6. Return to JananiSync and select **I've verified my email**. Use **Resend verification email** if needed.

Account creation creates a sign-in account before the privacy decision is recorded. Organisation and clinical access remain blocked until your acceptance of the current policy has been saved and your email is verified.

### Sign in

Enter **Email** and **Password**, then select **Log in**. Complete any privacy review or email-verification step shown. If you have access to several organisations, select one and choose **Continue**.

### Review or change your privacy decision

The **Privacy policy** icon is available before sign-in. After sign-in, Settings provides **Privacy policy**, **Change privacy decision**, and **Privacy decision history**.

A denial blocks organisation and clinical access but retains the account and decision history. Use **Review policy again** to make a new decision, or **Sign out**. A new decision is added to history rather than replacing an earlier decision. Updated policy versions may require another review.

**Privacy decision history** shows the 100 most recent saved decisions, including decision, policy version, account details, and the time recorded in UTC (a common reference time zone). Use **Refresh** to reload or select and copy details when needed. If the check fails, use **Retry server check** after reconnecting to the internet.

### Reset a forgotten password

1. On the login screen, select **Forgot password?** to open **Reset password**. An email already entered on the login screen is filled in for you.
2. Enter or check your account's **Email**, then select **Send reset link**. Wait while **Sending reset link…** is shown.
3. If the address belongs to an eligible account, JananiSync sends a password reset email. Check your inbox and spam folder. The confirmation does not disclose whether an account exists.
4. Open the link in the email and follow the instructions to choose a new password.
5. Return to JananiSync, select **Back to log in**, and sign in using the new password.

If an error appears, check the email address and your connection, then retry. If requests are temporarily limited, wait before trying again. Contact your organisation administrator or application support if you still cannot reset your password.

Changing a sign-in password does not restore access to patient records if you have lost your recovery phrase.

## 5. Organisation access and recovery phrases

### Request to join

1. Obtain the organisation's access code from its administrator. Organisations are not listed or searchable.
2. In the joining form, enter **Your name** and **Organisation access code**. The code contains eight letters or numbers.
3. Select the requested role: **Nurse** or **Doctor**.
4. Select **Prepare secure access**.
5. Privately record the generated 12-word phrase in its displayed order, together with the organisation name. The phrase is shown during this setup and cannot be retrieved later from the application.
6. Select **I have safely recorded my recovery phrase**, then **Request access**.
7. Wait at **Access pending** for an administrator to approve membership and allow access to patient records.

To withdraw a pending application, select **Cancel joining request**, then **Confirm cancellation**. You can apply again using the registered recovery key. If the request is declined or membership is deactivated, contact the administrator.

### Unlock patient records

After approval, enter **Your 12-word recovery phrase** and select **Access patient records**. A chart may instead show **Unlock organisation key**; enter your own personal phrase there too.

Your phrase unlocks patient records on your device and is not sent to the service. Enter your own phrase, not your administrator's.

Access is remembered in the same browser on this device so you can open records again. Your recovery phrase is not saved; keep it safely elsewhere. Use a secured personal or work device and select **Logout** when finished.

### Work with multiple organisations

Use the account-name menu's **Join another organisation** to request a new nurse or doctor membership. Each organisation has its own approval, role, access level, clinical data, and key access.

To use an existing active membership:

1. Finish editing and wait for saving to complete.
2. Open the **account-name menu** in the top bar and select **Switch organisation**. This option appears when your account has more than one active organisation membership. It is unavailable while logging out or leaving an organisation.
3. On **Choose an organisation**, find the target organisation's card and select **Continue**.
4. Unlock that organisation's patient records if prompted, then select the ward and admission again.

The switching control is in the account-name menu, including when viewing patient care, the Admin console, Settings, or the joining form. Switching closes the menu, exits an open joining form, and clears the selected admission. It does not submit or withdraw a joining request already sent for review.

If the joining form reports **You already have access to [organisation name]. Open your account menu and choose Switch organisation.**, use the switching steps above. A second joining request is unnecessary and the form blocks duplicate submission. If a request is already awaiting administrator review, check the pending request instead of submitting another.

If a project owner has already added your active admin membership to a second organisation, switch to it directly. You do not need to sign up again or submit a joining request. If the organisation is missing from the chooser, reload or sign out and back in with the same account. Ask the person responsible for account setup to check that your administrator membership is active and linked to the same email account.

After switching as an administrator, open **Admin console**. Set up secure access if the selected organisation is new, saving your displayed phrase, or unlock existing access using your own personal recovery phrase for that organisation. The first organisation's phrase does not automatically unlock the second. See [Prepare administrator access](#prepare-administrator-access) for the controls.

Additional membership can use a separate generated recovery key. If the joining form offers reuse, select the source organisation whose phrase you possess, enter **Selected organisation's recovery phrase**, then select **Verify and reuse selected key**. Reuse is an explicit choice. An already registered key for the target organisation is preserved; rejoining does not automatically replace it.

When rejoining with an existing registered key, no new phrase is generated. Select **I understand I need the old phrase or an admin-approved replacement to access records** before requesting access. After approval, use the old phrase or request replacement in Settings.

Label every saved phrase with its organisation name, even if you deliberately reuse one key.

### Replace a lost recovery key

Active doctors and nurses can request replacement for the selected organisation:

1. Open **Settings → Replace lost recovery key**.
2. Select **Generate new recovery key**.
3. Safely record the new phrase and its displayed fingerprint.
4. Select **I have safely saved my new recovery phrase**.
5. Select **Request administrator approval**.
6. Verify your identity and compare the **New key fingerprint** with your administrator through a trusted channel. Share the fingerprint, never the phrase.
7. After approval, unlock using the new phrase.

Until approval, the previous key remains registered. Use **Discard new key** before submission or **Cancel replacement request** while pending if you need to abandon the request. Replacement changes only the selected organisation's key; your account, permissions, and clinical contributions remain.

Administrators cannot replace their own key through this flow. An administrator must still be able to unlock the organisation’s records to approve replacement access. If nobody can unlock them, the records may be lost; contact your administrator immediately.

### Leave an organisation or log out

The account-name menu displays your organisation, role, and access level. It also offers **Join another organisation** and, when you have multiple active memberships, **Switch organisation**.

- **Leave organisation → Confirm leave** ends that organisation's membership access immediately. Clinical contributions and membership history remain. An administrator must complete an Access Update for remaining members. Administrators cannot leave through this control.
- **Logout** signs you out and attempts to remove remembered access. It does not erase all records stored on the device, appearance settings, downloaded PDFs, or printouts.

## 6. Navigation and appearance

The top-bar **User manual** help icon opens this guide as a public web page. You can also open `user-manual.html` directly beside the app's website address. Reading the guide does not require signing in; use **Open JananiSync** on the guide to return to the app.

Use the top-bar **Go back** and **Go forward** arrows or the browser's navigation controls. If a saved link is unavailable, return through the dashboard and select the ward and admission again.

Administrators can switch between **Admin console** and **Patient care** in the top bar. Clinical staff work through the clinical dashboard.

Hold **Ctrl** (Windows/Linux) or **Cmd** (Mac) and scroll the mouse wheel to zoom the entire app in or out (50–300%). Press **Ctrl+0** or **Cmd+0** to reset zoom. Ordinary scrolling works without a modifier key. This zoom lasts until you reload the app and does not change exported chart layouts.

Open **Settings** to change:

| Setting | Options |
| --- | --- |
| Theme | System default, Light, Dark |
| Font size | 12–24; default 16. Release the slider to apply. Text and interface sizes change together. |
| Accent colour | Purple, Blue, Teal, Green, Orange, Pink |

Appearance preferences are saved in this browser on this device across sessions and logins. Select **Done** to return. These settings do not change exported chart layouts.

For key verification, select **Public-key fingerprint**. If requested, enter **Your personal recovery phrase** and select **Verify and show fingerprint**. Check the organisation shown before sharing the fingerprint with its administrator.

## 7. Wards and admissions

### Find an admission

1. On the clinical dashboard, locate the ward and select **Open ward**.
2. Use **Search women** to search by name, CR number, IP number, or either mobile number. Use **Clear** to remove the search.
3. Choose a status filter if needed:

| Filter | Meaning |
| --- | --- |
| All women | All active admissions in the ward. |
| Labour yet to start | No recorded labour onset, diagnosis, or progress establishing labour status. |
| In labour | Recorded labour activity; includes second stage. |
| Second stage | Full dilation has been confirmed. |
| Birth given | Birth has been confirmed. |

Status is derived from recorded information. It is unavailable while clinical data is locked or not loaded. The initial filter favours **In labour**, then **Labour yet to start**, otherwise **All women**, according to the ward's records. Switch to **All women** if an expected admission is missing.

Lists show up to 50 admissions per page. Use **Previous page** and **Next page**. Check the woman's name, CR number, and IP number before choosing **Open guide** or **Partograph**.

### Admit a woman

1. Open the intended ward and select **Admit a woman**.
2. Complete the admission fields:

| Field | Requirement |
| --- | --- |
| Woman's name | Required. |
| CR number | Required numeric registration number. |
| IP number | Required numeric inpatient number. |
| Date of birth | Required; select using the date picker. |
| Informant name | Optional. |
| Contact number | Optional. |
| Alternate contact number | Optional. |

3. Check the ward and identifiers, then select **Save admission**.
4. Wait for the admission to appear in the roster. On failure, review the error and check that patient records are unlocked before retrying.

Admission date/time is set when the admission is created. Roster-backed names, hospital numbers, and admission dates/times appear as read-only chart details. The current screens do not provide a general form for editing those admission details; refer mistakes to your administrator.

## 8. Set up an admission chart

### Choose the chart start

For an admission without recorded time columns:

1. Open its LCG or Partograph.
2. In **Chart start time**, review **Start date and time** and the displayed time zone. It defaults to the current time.
3. Use the calendar/time picker to select the correct current or past start, or select **Use current time**.
4. Select **Set chart start**.
5. Read the setup warning and choose **Use this start time**.
6. Save the first observation to establish that start permanently.

**After the first saved observation, the chart start cannot be changed here.** Check it carefully for retrospective documentation. Chart start establishes the shared hourly schedule for both views; it is distinct from admission time.

Each ordinary column represents an hour, with half-hour subdivisions where supported. Empty elapsed slots can be filled without first signing off earlier columns. Future observations are unavailable. A first-half reading becoming available does not make its +30-minute reading available early.

Older charts may have observations between scheduled grid positions. Their recorded times remain unchanged; use the entry controls to select the intended column and check its exact time.

### Complete the chart header

| View | Editable header information |
| --- | --- |
| LCG | Parity, Labour onset (spontaneous/induced), Active labour diagnosis, Ruptured membranes date/time, Risk factors. |
| Partograph | Gravida, Parity, Living children, POG completed weeks and additional days, Ruptured membranes in hours. |

POG additional days must be **0–6**. Patient name comes from the admission and is read only. The Partograph also displays the roster hospital number and admission date/time.

Valid typed header edits save when the field loses focus. Category and date/time selections save immediately. Use the adjacent history icon to review a header field's changes.

## 9. Use the Labour Care Guide

The LCG is a table with observation rows and time columns.

| Section | Information recorded |
| --- | --- |
| SUPPORT | Companion, pain relief, oral fluid, posture. |
| BABY | Baseline FHR, decelerations, amniotic fluid, fetal position, caput, moulding. |
| WOMAN | Pulse, systolic/diastolic BP, temperature, urine protein and acetone. |
| LABOUR | Contraction frequency/duration, cervical dilation, descent, and second-stage pushing onset. |
| MEDICATION | Oxytocin, medicines, IV fluids. |
| SHARED DECISION MAKING | Assessment, plan, and sign-off/initials. |

### Record observations

1. Check the patient and time heading. Choose an eligible elapsed column.
2. Select the appropriate cell and type a value or choose a category.
3. For typed entries, tap or click another cell to finish editing and trigger saving. Category selections save immediately.
4. Check for validation errors or **Saving cell…**, and review the resulting value.
5. Use the labour-progress grid to plot **X** for cervical dilation and **O** for descent. In second stage, **P** marks pushing onset.

There is no separate whole-chart Save button. Each cell saves separately. A red invalid field has not been successfully committed; correct it before continuing.

LCG provides separate first-half and +30-minute readings for **FHR, decelerations, contraction frequency, and contraction duration**. Check which half you are editing.

To clear a categorical entry, choose **Clear recorded value**. For a typed field, remove its contents and finish editing. X/O/P plot selections save immediately; tapping an already selected mark again clears it. Clearing retains history; a blank is distinct from an observed zero.

### Read alerts

The **ALERT** column displays the app's reference thresholds. An ellipse highlights a recorded value that matches its alert logic. Cervical-delay highlighting also considers the history of recorded examinations. Blank cells are not measurements.

Review highlighted values under your organisation's clinical procedures. The application permits recording actual observations without enforcing when examinations must occur.

### Use the table on a phone

Compact screens retain the section, observation, and alert columns on the left and show one time column on the right. Use the **Previous time column** and **Next time column** arrowheads in the sticky heading to page through time. Relevant half-hour subdivisions remain within that column.

The heading stays visible while scrolling vertically. Presentation columns marked Skipped after full dilation are skipped by mobile paging; they remain visible in the wide chart and PDF. On wider screens, scroll horizontally to reach later columns.

## 10. Use the Partograph

Choose **Partograph** from the admission card, or **View Partograph** from the LCG. Select **View LCG** to return. The selected admission and shared records remain the same.

### Enter and save a mark

1. Under **Enter chart data**, select the time column and parameter, then select **Record parameter**. Alternatively, tap within the intended graph time band at the desired reading height.
2. Review the preview's patient context, actual observation time, parameter, and value. A graph tap creates a local preview only.
3. Adjust the number or category in the form. Further graph taps adjust the preview without saving it.
4. Complete related fields when needed: BP uses a systolic/diastolic pair; contractions use count and duration.
5. Select **Confirm and save** to record the mark, or **Cancel** to discard the preview.
6. Review the saved mark and any message about saving or sharing changes.

The entry form appears over the chart on wide screens and as a bottom sheet on phones. Numeric fields show allowed limits; saving is disabled for invalid values. Systolic pressure must be above diastolic pressure. Where provided, the +1/−1 arrowheads adjust numeric entries within their bounds.

### Understand chart symbols

| Symbol or lane | Meaning in this application |
| --- | --- |
| X | Cervical dilation. |
| O | Head descent in fifths palpable above the pubic symphysis, 5/5 to 0/5; not vaginal station. |
| Dots | FHR and maternal pulse readings. |
| Paired vertical BP marker | Systolic and diastolic readings. |
| Contraction boxes | Number of contractions per 10 minutes; shading represents duration. |
| Dotted / hatched / solid shading | Duration below 20 seconds / 20–40 seconds inclusive / above 40 seconds. |
| Oxytocin rows | Concentration in U/L and rate in drops/min. |
| Alert and action lines | Reference lines; they do not move the recorded observations or choose an entry value. |

The modified Partograph's cervical reference chart begins at 4 cm. Elapsed hours refer to its plotted chart origin, which is not automatically the admission time. Read the chart-start label, actual clock times, and date changes at midnight together.

The on-screen graph covers 0–12 hours from its origin. A notice identifies recorded time points outside that view; inspect them through **Cell edit history**. PDF export continues into additional 12-hour windows.

### Half-hour readings and view differences

The Partograph supports first-half and +30-minute readings for FHR, amniotic fluid, moulding, cervical dilation, descent, pulse, BP, temperature, contractions, and oxytocin. Drugs, IV fluids, and urine rows do not have separate half-hour entries.

LCG has fewer half-hour fields. Additional second-half Partograph readings remain stored and available in history; they are not substituted into the LCG's first-half values. Missing readings stay blank.

### Erase a saved parameter

Open the saved parameter's form, choose **Erase saved value**, and review **Confirm erase**. Use **Keep value** to cancel. Erasing clears the selected field and half-hour while preserving its history. For BP or contractions, it clears only the chosen field, not automatically its paired value.

### Move and zoom

Scroll horizontally or use the earlier/later hour controls to reach other chart times. Use the highlighted **Chart zoom** panel immediately above the chart: select **Zoom in**, **Zoom out**, or **Reset to 100%**. The panel shows the current zoom percentage; the chart supports 60–300% zoom. On touch devices, pinch with two fingers to zoom and swipe with one finger to scroll. Use the entry controls when tapping the graph precisely is difficult.

## 11. Sign off, review history, and correct entries

### Sign off a column

1. Review the saved observations in the intended time column.
2. Select **Sign off column** beneath the column.
3. Check the patient and time in the confirmation dialog, then confirm.

Sign-off is an audit acknowledgement. It does not lock every cell and is not required before entering another eligible column. An existing sign-off can be superseded only by someone with a strictly higher access level; previous sign-offs remain in history.

### Review cell history

Select a cell and its history badge, a header's history icon, or **Cell edit history**. In the history dialog, select the column/header and row to inspect.

History shows initial values, changes, the author, recorded access level, saved time when available, and correction reasons. Cleared values remain in the history. Some older records may not have complete cell history. A message about pending changes means they have not yet been confirmed as saved online.

### Edit or make a reasoned correction

Ordinary eligible edits remain possible after sign-off. Your access level must meet the recorded level of each cell you change, including the selected half-hour. A grouped mark requires access to every field it changes. Sign-off does not add a separate confirmer-level threshold to ordinary cell editing.

To document a correction with a reason:

1. Select the pencil icon on an eligible signed-off column, or open history and select **Correct this cell**.
2. In **Correct confirmed cells**, select up to four eligible cells and enter corrected values.
3. Enter **Reason (required)**.
4. Select **Save correction**.

Corrections add changes and retain previous entries and sign-offs. If the column changed while the dialog was open, close and reopen it to review the latest values. If a cell is unavailable at your access level, ask an appropriately authorised colleague to review it.

## 12. Record second stage and birth

Both chart views expose the same stage actions. They are separate from entering a dilation number or signing off an ordinary column.

### Confirm full dilation

1. Review the correct patient's column and saved observations.
2. Select **Full Dilation: Confirm and skip to second stage**, or **Confirm full dilation** on a compact layout.
3. Verify the patient and column time, read the consequence, then select **Confirm full dilation**.

**This transition cannot be undone.** It records full dilation and advances the guide to second stage. Remaining first-stage presentation slots are marked Skipped. Use **Add second-stage column** when offered to continue documenting eligible second-stage times.

Use **Skipped to second stage … View history** to inspect the transition. Record pushing onset using the second-stage **P** control where applicable.

### Confirm birth

1. Review the appropriate second-stage column and time.
2. Select **Birth: Confirm and mark birth**, or **Confirm birth** on a compact layout.
3. Verify the patient and time in the dialog, then select **Confirm birth**.

Birth is marked at that column time. Earlier observation gaps can still be completed subject to permissions, but new observations after the birth time are unavailable. The roster status updates to **Birth given**. Marking birth does not automatically archive the admission.

## 13. Archive and restore admissions

Staff with access level **60–99** can archive or unarchive an admission.

### Archive

1. On the woman's card or chart, select the archive icon (**Archive woman**).
2. Check the name, ward, CR number, and IP number.
3. Select **Archive woman** in the confirmation dialog.

The admission moves out of the active ward list to **Archived women**. Its clinical records remain available, and its chart becomes read only.

### View or restore an archived admission

1. From the ward-selection screen, choose **Archived women (count)**.
2. Search by name, CR, IP, or either mobile number, and use the page controls if needed.
3. Open the LCG or Partograph to review the retained records.
4. To restore it, select **Unarchive woman**, check the confirmation, and confirm. It returns to the active list in its original ward.

Use **Archive history** to see archive/unarchive actions, authors, access levels, and saved times. Select **Load older actions** when available. Archiving changes visibility and editability; it does not delete records.

## 14. Export and print charts

1. Open the required admission's LCG or Partograph and check its identity and saved observations.
2. Finish editing, confirm any Partograph preview, and wait for saving to finish. For a final shared record, also check that changes waiting to be shared have been saved online.
3. Select **Export PDF / Print**. Allow the preview pop-up if your browser blocks it.
4. In the preview, select **Download PDF**, or **Print / Save as PDF** for the browser print dialog.
5. Use **A4 portrait** and disable browser headers/footers when printing.

Export is unavailable while patient records are locked, records are loading, saving is in progress, an error loading records is present, or there are no saved observations. Unsaved graph previews are excluded.

Charts use a fixed paper layout independent of theme, app font size, and zoom. LCG pages contain up to 12 columns; Partograph pages cover 12-hour windows. Long text appears in numbered continuation notes. Missing readings remain blank. A contraction count without a recorded duration is marked with a question mark.

PDF and print rendering run locally in the browser. Downloaded PDF pages preserve visual symbols as images, so their text is not selectable. The recreated layouts are not official WHO-generated documents.

Exports and printouts contain readable patient information outside the app's access controls. Store and share them under your organisation's records policy.

## 15. Connectivity, local storage, and installed use

### Understand save status

| Message or state | What to do |
| --- | --- |
| Saving cell… / Saving… | Wait for the operation to finish and review the value. |
| Saved in this browser; waiting for a connection to sync. | The change is saved on this device but may not yet appear on other devices. Reconnect and check that it has been saved online. |
| Loading encrypted observation grid… | Wait for loading before relying on the chart contents. |
| Error loading records or checking access | Review the message and contact an administrator if it persists. Do not assume the displayed chart is complete. |

Seeing a change on your device does not mean it has been saved online. After reconnecting, check for messages about waiting changes and review the saved history. Some tasks need an internet connection, and not all records may be available offline.

### Install and update the web app

When your version of JananiSync supports installation, use your browser's install or add-to-home-screen option if offered. Installation is optional; the browser website remains usable.

Open JananiSync while connected to the internet first and allow it to finish loading. The browser saves the files needed to open the app later. To view patient records, they must already be stored on the device, you must be signed in, and access must be unlocked. First-time sign-in and installation require a network connection.

Updates install in the background. Close all JananiSync browser tabs and installed-app windows, then reopen while online to use an available update.

### Protect local information

Your browser may keep protected patient records and information about your use of the app. Remembered access works only in the same browser on the same device. Signing out does not remove every saved item or exported copy.

Before clearing site data, make sure all entries have been saved online and recovery phrases are safely available. Clearing local data can remove records stored on the device, remembered access, and preferences; it does not delete the organisation's records saved online. Offline devices may not learn of revoked access until they reconnect.

## 16. Administrator tasks

### Prepare administrator access

The first organisation administrator must be set up by the person responsible for JananiSync at your organisation. Users cannot make themselves administrators through the joining form. Obtain this setup from the person responsible for the app.

For the first secure access setup:

1. Open the administrator dashboard.
2. Select **Prepare admin encryption key**.
3. Safely record the displayed 12-word phrase and confirm **I safely recorded the phrase**. If an existing account key is reused, confirm that you still have its phrase.
4. Select **Initialise organisation key** and keep the page open while saving completes.

In later sessions, open **Admin console**, enter **Your personal 12-word recovery phrase**, and select **Verify and open dashboard** before managing members or wards. Administrator access is remembered on the device. Use a secured browser and sign out when finished.

The console contains **Requests**, **Members**, **Wards**, and **Security** tabs. Use **Patient care** in the top bar to return to clinical work.

### Approve joining requests

1. Open **Requests**. The organisation access code is shown here for distribution to authorised staff.
2. Review the applicant's name, email, user ID, and requested role.
3. Assign an **Access level (10–99)** appropriate to your organisation's responsibilities.
4. Select **Approve and grant access as nurse** or **Approve and grant access as doctor**.
5. Wait for completion and verify the member's access status.

The current request list provides approval and key granting together. It does not expose a reject button for new membership requests.

### Manage members

Under **Members**, select the intended member and use the applicable controls:

| Action | Steps and effect |
| --- | --- |
| Change clinical role | Select Doctor or Nurse, then **Save role**. Promotion to administrator is not offered here. |
| Change access level | Enter 10–99, then **Save access level**. Historical entry levels remain part of the audit history. |
| Check key access | Use **Check access status** if status could not be verified. |
| Finish key access | Use **Complete access setup** or **Register key for automatic updates** when shown. |
| Deactivate | Select **Deactivate access**, read the consequences, then **Confirm deactivation**. Access is revoked and an Access Update begins. |
| Reactivate | Use **Reactivate access** for an inactive member. Members who left must apply to join again. |

### Configure wards

1. Open **Wards**.
2. Enter **Ward name**, **Location**, and **Ward head**. Ward name is required.
3. Select **Add ward**.
4. To change a ward, select **Configure**, edit the fields, then **Save ward**. Use **Cancel** to abandon the edit.

Ward changes are protected and apply only to your organisation. Once wards exist, clinical staff can open them and create admissions. If no wards exist, an administrator must complete this setup first.

### Approve a replacement recovery key

1. Under **Requests**, locate **Recovery key replacement requested**.
2. Verify the member's identity and compare the displayed **New key fingerprint** with the member through a trusted channel.
3. Select **Review and approve replacement**.
4. Confirm **Verified — approve replacement** only after completing that comparison. Use **Reject request** if the request should not proceed.

Approval replaces the registered key and grants access to existing organisation records. The previous phrase no longer unlocks that registered replacement key. It cannot erase keys or information someone previously copied.

### Rotate keys and complete Access Update

To initiate a manual rotation, open **Security → Rotate organisation key**, review the dialog, then select **Start Access Update**. Deactivation and a member leaving can also require an Access Update.

During the update, clinical access pauses. New approvals, member changes, and ward administration are also paused. Keep the administrator account online and unlocked while progress completes.

If interrupted, sign back in and follow the maintenance screen. Enter **Admin's 12-word recovery phrase** and select **Unlock and continue update**, or use **Resume access update** when enabled. Wait for **Access Update complete**. Remaining members may need to unlock their refreshed access afterward.

Rotation updates access for active members; it does not remotely erase previously copied keys, clinical information, PDFs, or printouts.

## 17. Troubleshooting and support

| Problem | Action |
| --- | --- |
| Email/password rejected | Check the address and credentials. If sign-up reports an existing account, use login. Select **Forgot password?**, enter your account email, and select **Send reset link** to choose a new password through the emailed link. Check spam or contact support if needed. |
| Verification email missing | Check the address and spam/junk folder; use **Resend verification email**, open the link, then **I've verified my email**. |
| Privacy review blocks access | Record a decision for the current policy. Your acceptance must be saved online. For a failed check, reconnect and select **Retry server check**. |
| Access pending | Wait for the organisation administrator. Confirm the intended code and requested role with them. |
| Joining form says you already have access | Open the **account-name menu → Switch organisation**, then select **Continue** for that organisation. Do not submit another joining request. |
| Switch organisation is missing or disabled | It appears in the account-name menu only when multiple active memberships are available. Wait for logout or leaving to finish if busy. If an expected membership is missing, reload or sign in again and ask the administrator to verify it. |
| Access declined or deactivated | Contact the organisation administrator. A different device or password does not restore membership. |
| Could not load organisation access | Reconnect and select **Try again**. Refer persistent failures to the administrator. |
| Recovery phrase rejected or records remain locked | Use your own phrase for the selected organisation, with all 12 words in order. Ask the administrator to check key access. If lost, use the doctor/nurse replacement flow. |
| No wards configured | Ask an administrator to create a ward under **Admin console → Wards**. |
| Expected woman not listed | Verify organisation and ward; clear search, select **All women**, check other pages, and check **Archived women**. |
| Future time or second-half field disabled | Check device time and the exact observation time. Wait until that time is eligible. |
| Typed LCG value did not save | Finish editing by selecting another cell. Resolve red validation errors and review messages about saving or sharing changes. |
| Partograph tap did not save | A tap is a preview. Review it and select **Confirm and save**. |
| Cannot edit or correct a saved cell | Your access may be below the cell's recorded level, the admission may be archived, or saving/maintenance may be active. Ask an authorised colleague or administrator. |
| Cannot replace a sign-off | Only a strictly higher access level can supersede the current sign-off. Cell editing follows separate per-cell rules. |
| Read only · Senior staff setup needed / Preparing older records… | Older records need admission preparation. An authorised member with level 60 or above can perform it. Use **Retry** if preparation fails, or contact the administrator. |
| Some records unreadable or withheld | Report the displayed identifiers/error to the administrator. The chart may be incomplete; do not recreate missing entries merely to hide the warning. |
| Maintenance / Access Update remains visible | The administrator must come online, unlock, and resume or finish the update. |
| Export button disabled | Unlock records, wait for loading/saving, resolve feed errors, and ensure there is a saved observation. |
| Export preview does not open | Allow pop-ups for the site and select **Export PDF / Print** again. |
| Pending-sync message persists | Restore the connection and keep the app open to check that changes have been saved online. Do not clear site data while changes are pending. |
| Archive history cannot load | Select **Retry**. Ask the administrator to check your access and the setup issue described in the error. |
| Old app version still appears | Close all JananiSync tabs and installed windows, then reopen online. |
| Logout reports that remembered access could not be cleared | Follow the displayed instructions, close the browser, and notify the administrator before leaving a shared device. |

For access, ward configuration, entry permissions, and treatment-record questions, contact your **organisation administrator** first.

For application/privacy requests, the current in-app policy lists **sharmilaarunbabuandabhishekdas@gmail.com**. Confirm contact details in **Privacy policy** if they have changed. Include the organisation, affected task, approximate time, browser/device, and exact error. Use synthetic or redacted examples where possible. Do not send passwords, recovery phrases, or unnecessary patient details.

There is no self-service account or patient-record deletion control. Send deletion or privacy requests through the contact in the policy or your healthcare organisation's records office. Logout, denial, leaving, and archiving do not automatically delete retained records.

## 18. Glossary

| Term | Meaning |
| --- | --- |
| Admission | A woman's recorded stay/session in a ward, with its associated chart. |
| Cell | One observation or header field at a particular time, where applicable. |
| Column | A chart time point containing several observation cells. |
| LCG | Labour Care Guide. |
| FHR | Fetal heart rate. |
| BP | Blood pressure. |
| G / P / L | Gravida / parity / living children. |
| POG | Period of gestation, entered as completed weeks and additional days. |
| CR / IP | Registration / inpatient identifiers used by the organisation. |
| Sign-off | An attributed acknowledgement of a column's observations. |
| Recovery phrase | Twelve secret words used to unlock patient records. |
| Fingerprint | A code you can share with your administrator to check your recovery key. |
| Access Update | The organisation's key-update process, during which clinical access pauses. |
| Archive | Retain an admission in the archived list with read-only clinical access. |
| Pending sync | A change saved on your device that is waiting to be saved online. |
