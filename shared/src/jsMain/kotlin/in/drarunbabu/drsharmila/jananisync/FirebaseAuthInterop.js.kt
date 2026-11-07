@file:JsModule("firebase/auth")

package `in`.drarunbabu.drsharmila.jananisync

internal external fun getAuth(app: FirebaseApp): FirebaseAuth
internal external fun onAuthStateChanged(
    auth: FirebaseAuth,
    nextOrObserver: (FirebaseUser?) -> Unit
): () -> Unit
internal external fun signInWithEmailAndPassword(
    auth: FirebaseAuth,
    email: String,
    password: String
): kotlin.js.Promise<UserCredential>
internal external fun createUserWithEmailAndPassword(
    auth: FirebaseAuth,
    email: String,
    password: String
): kotlin.js.Promise<UserCredential>
internal external fun signOut(auth: FirebaseAuth): kotlin.js.Promise<Unit>

internal external fun sendPasswordResetEmail(auth: FirebaseAuth, email: String): kotlin.js.Promise<Unit>

internal external fun sendEmailVerification(user: FirebaseUser): kotlin.js.Promise<Unit>

internal external fun reload(user: FirebaseUser): kotlin.js.Promise<Unit>

internal external fun getIdToken(user: FirebaseUser, forceRefresh: Boolean): kotlin.js.Promise<String>
