# Firebase App Check

The web client initializes Firebase App Check before creating Auth or Firestore,
using the reCAPTCHA Enterprise provider. Replace
`RECAPTCHA_ENTERPRISE_SITE_KEY` in `FirebaseService.js.kt` with the **site key**
for the registered web app in Firebase Console. The site key is public; do not
put a reCAPTCHA secret key in client code.

On `localhost` and `127.0.0.1`, the client enables Firebase's App Check debug
token mode. Run the app and copy the debug token printed in the browser console
into the app's registered debug tokens in Firebase Console. This mode is not
enabled on production hosts. Never publish or use a debug token as the
production site key.

If App Check is enforced for a Firebase service, requests from clients without
a valid token will be rejected. Confirm the production domain is allowed by the
reCAPTCHA Enterprise site key and that valid App Check traffic appears before
enabling enforcement.
