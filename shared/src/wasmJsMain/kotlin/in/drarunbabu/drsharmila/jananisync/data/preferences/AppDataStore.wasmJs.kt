package `in`.drarunbabu.drsharmila.jananisync.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.WebLocalStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer

private const val DATASTORE_NAME = "jananisync.preferences_pb"

actual fun createAppDataStore(): DataStore<Preferences> = DataStoreFactory.create(
    storage = WebLocalStorage(
        serializer = PreferencesSerializer,
        name = DATASTORE_NAME
    )
)
