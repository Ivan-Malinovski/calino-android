package calino.malinov.ski.data.caldav

import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/** WebDAV / CalDAV namespaces. */
object DavNs {
    const val Dav = "DAV:"
    const val CalDav = "urn:ietf:params:xml:ns:caldav"
    const val CardDav = "urn:ietf:params:xml:ns:carddav"
    const val CalendarServer = "http://calendarserver.org/ns/"
    const val Apple = "http://apple.com/ns/ical/"
}

/**
 * DOM parsing for multistatus responses.
 *
 * Every lookup falls back to matching on local name when the namespace-aware
 * lookup finds nothing. That fallback is not defensive padding: strict
 * namespace matching alone broke against Radicale in the Calino web app,
 * because servers vary in whether they bind a prefix or use a default
 * namespace, and some emit prefixes they never declared.
 */
object DavXml {

    private val factory: DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // Untrusted XML from an arbitrary server: no external entities.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            isExpandEntityReferences = false
        }

    fun parse(xml: String): Element? = runCatching {
        val cleaned = xml.removePrefix("\uFEFF").trim()
        if (cleaned.isEmpty()) return null
        val builder = synchronized(factory) { factory.newDocumentBuilder() }
        builder.parse(ByteArrayInputStream(cleaned.toByteArray(Charsets.UTF_8)))
            .documentElement
    }.getOrNull()

    /** Direct and nested descendants named [localName], namespace-tolerant. */
    fun elements(scope: Element, ns: String, localName: String): List<Element> {
        val strict = scope.getElementsByTagNameNS(ns, localName).toElementList()
        if (strict.isNotEmpty()) return strict
        return scope.getElementsByTagName("*").toElementList()
            .filter { it.localNameOrTag() == localName }
    }

    fun element(scope: Element, ns: String, localName: String): Element? =
        elements(scope, ns, localName).firstOrNull()

    fun text(scope: Element, ns: String, localName: String): String? =
        element(scope, ns, localName)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    /** Read a property only from a successful propstat for this response. */
    fun successfulProperty(response: Element, ns: String, localName: String): Element? =
        directChildren(response, DavNs.Dav, "propstat")
            .asSequence().filter { propstat ->
                directChildren(propstat, DavNs.Dav, "status")
                    .firstOrNull()?.textContent?.trim()?.let {
                        Regex("^HTTP/\\S+\\s+2\\d\\d(?:\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(it)
                    } == true
            }.mapNotNull { directChildren(it, DavNs.Dav, "prop").firstOrNull() }
            .mapNotNull { prop -> directChildren(prop, ns, localName).firstOrNull() }
            .firstOrNull()

    fun successfulText(response: Element, ns: String, localName: String): String? =
        successfulProperty(response, ns, localName)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    fun directText(scope: Element, ns: String, localName: String): String? =
        directChildren(scope, ns, localName).firstOrNull()?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private fun directChildren(scope: Element, ns: String, localName: String): List<Element> =
        (0 until scope.childNodes.length).mapNotNull { scope.childNodes.item(it) as? Element }
            .filter { (it.namespaceURI == ns || it.namespaceURI == null) && it.localNameOrTag() == localName }

    /**
     * Whether [scope] contains an element with this local name at any depth.
     * Used for resourcetype probing, where only presence matters.
     */
    fun hasElement(scope: Element, localName: String): Boolean =
        scope.getElementsByTagName("*").toElementList()
            .any { it.localNameOrTag() == localName }

    fun Element.localNameOrTag(): String = localName ?: tagName.substringAfterLast(':')

    private fun org.w3c.dom.NodeList.toElementList(): List<Element> =
        (0 until length).mapNotNull { index ->
            item(index).takeIf { it.nodeType == Node.ELEMENT_NODE } as? Element
        }
}
