package calino.malinov.ski.data.jmap

import kotlin.coroutines.cancellation.CancellationException
import org.json.JSONObject

/** What the account may do about invitations: the addresses it sends as, and whether the server delivers them. */
internal data class JmapSchedulingInfo(val addresses: Set<String>, val enabled: Boolean)

private fun asMailto(value: String): String? {
    val trimmed = value.trim()
    return when {
        trimmed.startsWith("mailto:", ignoreCase = true) -> "mailto:" + trimmed.substring(7).trim()
        '@' in trimmed && ' ' !in trimmed -> "mailto:$trimmed"
        else -> null
    }?.takeIf { it.length > "mailto:".length }
}

/**
 * Reads the account's scheduling identities (`ParticipantIdentity/get`) and
 * capability flags. A server without scheduling answers "none", never an error:
 * the editor then offers no invitations, as for a CalDAV server without
 * auto-schedule. The login name counts as an address only when it is one.
 */
internal suspend fun JmapClient.schedulingInfo(): JmapSchedulingInfo {
    val account = session.accounts[accountId]?.accountCapabilities
    val flagged = listOf(JmapCalendars, JmapPrincipals).any { name ->
        val capability = account?.objectOrNull(name) ?: session.capabilities.objectOrNull(name)
        capability?.boolOrNull("supportsScheduling") == true || capability?.boolOrNull("maySendSchedulingMessages") == true
    }
    val availability = session.capabilities.objectOrNull(JmapPrincipalsAvailability) != null ||
        account?.objectOrNull(JmapPrincipalsAvailability) != null
    val identities = try {
        invoke("ParticipantIdentity/get", JSONObject().put("ids", JSONObject.NULL))
            .arrayOrNull("list")?.toList()?.filterIsInstance<JSONObject>().orEmpty()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        emptyList()
    }
    val addresses = linkedSetOf<String>()
    for (identity in identities) {
        val sendTo = identity.objectOrNull("sendTo") ?: continue
        // iMIP is the method that reaches people outside the server.
        val ordered = sendTo.keyList().sortedBy { if (it == "imip") 0 else 1 }
        for (method in ordered) sendTo.stringOrNull(method)?.let(::asMailto)?.let(addresses::add)
    }
    asMailto(username)?.takeIf { addresses.isEmpty() || it !in addresses }?.let(addresses::add)
    return JmapSchedulingInfo(addresses, enabled = flagged || availability || identities.isNotEmpty())
}
