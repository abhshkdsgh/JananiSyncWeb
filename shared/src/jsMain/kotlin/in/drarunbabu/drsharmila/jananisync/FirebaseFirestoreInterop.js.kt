@file:JsModule("firebase/firestore")

package `in`.drarunbabu.drsharmila.jananisync

internal external fun getFirestore(app: FirebaseApp): FirebaseFirestore
internal external fun enableIndexedDbPersistence(firestore: FirebaseFirestore): kotlin.js.Promise<Unit>
internal external fun collection(firestore: FirebaseFirestore, path: String): QueryReference
internal external fun doc(query: QueryReference): DocumentReference
internal external fun collectionGroup(firestore: FirebaseFirestore, collectionId: String): QueryReference
internal external fun where(field: String, operator: String, value: String): QueryConstraint
internal external fun query(reference: QueryReference, vararg constraints: QueryConstraint): QueryReference
internal external fun onSnapshot(
    query: QueryReference,
    onNext: (QuerySnapshot) -> Unit,
    onError: (FirebaseError) -> Unit
): () -> Unit
internal external fun onSnapshot(
    query: QueryReference,
    options: dynamic,
    onNext: (QuerySnapshot) -> Unit,
    onError: (FirebaseError) -> Unit
): () -> Unit
internal external fun onSnapshot(
    reference: DocumentReference,
    options: dynamic,
    onNext: (DocumentSnapshot) -> Unit,
    onError: (FirebaseError) -> Unit
): () -> Unit
internal external fun doc(firestore: FirebaseFirestore, path: String): DocumentReference
internal external fun setDoc(reference: DocumentReference, data: dynamic): kotlin.js.Promise<Unit>
internal external fun updateDoc(reference: DocumentReference, data: dynamic): kotlin.js.Promise<Unit>
internal external fun orderBy(field: String, direction: String): QueryConstraint
internal external fun limit(count: Int): QueryConstraint
internal external fun startAfter(value: Int): QueryConstraint
internal external fun getDocs(query: QueryReference): kotlin.js.Promise<QuerySnapshot>
internal external fun getDoc(reference: DocumentReference): kotlin.js.Promise<DocumentSnapshot>
internal external fun getDocFromServer(reference: DocumentReference): kotlin.js.Promise<DocumentSnapshot>
internal external fun getDocsFromServer(query: QueryReference): kotlin.js.Promise<QuerySnapshot>
internal external fun runTransaction(firestore: FirebaseFirestore, update: (FirestoreTransaction) -> kotlin.js.Promise<Unit>): kotlin.js.Promise<Unit>
internal external fun serverTimestamp(): dynamic
internal external fun writeBatch(firestore: FirebaseFirestore): FirestoreWriteBatch

internal external interface QueryConstraint
internal external interface FirestoreTransaction {
    fun get(reference: DocumentReference): kotlin.js.Promise<DocumentSnapshot>
    fun set(reference: DocumentReference, data: dynamic): FirestoreTransaction
    fun update(reference: DocumentReference, data: dynamic): FirestoreTransaction
}
internal external interface FirestoreWriteBatch {
    fun set(reference: DocumentReference, data: dynamic): FirestoreWriteBatch
    fun update(reference: DocumentReference, data: dynamic): FirestoreWriteBatch
    fun commit(): kotlin.js.Promise<Unit>
}
