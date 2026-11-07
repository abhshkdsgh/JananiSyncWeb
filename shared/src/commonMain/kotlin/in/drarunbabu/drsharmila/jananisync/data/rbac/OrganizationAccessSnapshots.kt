package `in`.drarunbabu.drsharmila.jananisync.data.rbac

/** An empty local cache is not evidence that a newly signed-in account has no membership. */
internal class OrganizationAccessSnapshots(
    private val onData: (List<String>, List<String>) -> Unit
) {
    private var members = emptyList<String>()
    private var requests = emptyList<String>()
    private var membersResolved = false
    private var requestsResolved = false

    fun members(documents: List<String>, fromCache: Boolean) {
        members = documents
        membersResolved = !fromCache || documents.isNotEmpty()
        publish()
    }

    fun requests(documents: List<String>, fromCache: Boolean) {
        requests = documents
        requestsResolved = !fromCache || documents.isNotEmpty()
        publish()
    }

    private fun publish() {
        if (membersResolved && requestsResolved) onData(members, requests)
    }
}
