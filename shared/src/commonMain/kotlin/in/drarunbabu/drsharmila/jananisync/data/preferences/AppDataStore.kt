package `in`.drarunbabu.drsharmila.jananisync.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

/** Creates the browser-backed DataStore shared by the app's preference repositories. */
expect fun createAppDataStore(): DataStore<Preferences>
