@file:JsModule("firebase/app-check")

package `in`.drarunbabu.drsharmila.jananisync

internal external class ReCaptchaEnterpriseProvider(siteKey: String)

internal external interface AppCheckOptions {
    var provider: ReCaptchaEnterpriseProvider
    var isTokenAutoRefreshEnabled: Boolean
}

internal external interface FirebaseAppCheck

internal external fun initializeAppCheck(app: FirebaseApp, options: AppCheckOptions): FirebaseAppCheck
