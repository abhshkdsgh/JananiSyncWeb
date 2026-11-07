**Work in progress — Dummy data only.** Do not enter, upload, or use actual patient or clinical data. This version is for demonstration and testing only and must not be used for clinical care.

This is a Kotlin Multiplatform project targeting Web.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
    - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
    - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
      For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
      the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
      Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
      folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and
options:

- Web app (JavaScript): `./gradlew :webApp:jsBrowserDevelopmentRun`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/)…

## Organization access

The first organization admin must be provisioned by a trusted Firebase project owner. See [First organization admin setup](./docs/first-organization-admin.md) for the console steps and required indexes.

## Public user manual

The top-bar **User manual** help icon opens `user-manual.html` beside the app, before or after sign-in. This standalone page needs no authentication, JavaScript, or Firebase connection. It is included in development resources, production distributions, and the offline asset cache.

Edit [USER_MANUAL.md](./USER_MANUAL.md) to update its content. Web resource builds regenerate the HTML automatically; run `python3 scripts/generate_user_manual.py` to regenerate it directly, or add `--check` to verify it is current. The page layout lives in `scripts/user_manual_template.html`.

## Privacy policy and account decisions

The complete policy is available from the app's privacy icon before sign-in. New and existing accounts must record acceptance of the current policy before organization access. Denial remains in the account history and blocks access. Settings allows changing the decision and inspecting the newest 100 server records.

See [Account privacy decisions and evidence](./docs/privacy-consent.md) for the record schema, integrity limits, validation commands, and coordinated app/rules rollout. The policy source is [privacy-policy.md](./docs/privacy-policy.md); validate its generated content and archived hash with `python3 scripts/generate_privacy_policy.py --check`.

We would appreciate your feedback on Compose/Web in the public Slack
channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).
