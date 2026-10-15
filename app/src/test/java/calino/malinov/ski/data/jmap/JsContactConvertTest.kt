package calino.malinov.ski.data.jmap

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JsContactConvertTest {
    private val person = """
        BEGIN:VCARD
        VERSION:4.0
        UID:urn:uuid:person-1
        FN:Ann Lovelace
        N:Lovelace;Ann;Augusta;;
        NICKNAME:Countess
        ORG:Analytical Engines;Research
        TITLE:Mathematician
        EMAIL;TYPE=work;PREF=1:ann@example.com
        EMAIL;TYPE=home:ann@home.example
        TEL;TYPE=cell,voice:+44 20 7946 0000
        ADR;TYPE=home:;;1 Byron St\;Apt 2;London;;N1;UK
        URL:https://example.com/ann
        NOTE:Line one\nLine two\, with comma
        BDAY:18151210
        CATEGORIES:friends,math
        END:VCARD
    """.trimIndent().replace("\n", "\r\n") + "\r\n"

    @Test
    fun personConvertsToJsContact() {
        val card = VCardToJsContact.convert(person)
        assertEquals("Card", card.getString("@type"))
        assertEquals("urn:uuid:person-1", card.getString("uid"))
        val name = card.getJSONObject("name")
        assertEquals("Ann Lovelace", name.getString("full"))
        val parts = name.getJSONArray("components")
        assertEquals("surname", parts.getJSONObject(0).getString("kind"))
        assertEquals("Lovelace", parts.getJSONObject(0).getString("value"))
        assertEquals("Countess", card.getJSONObject("nicknames").getJSONObject("nicknames1").getString("name"))
        val organization = card.getJSONObject("organizations").getJSONObject("organizations1")
        assertEquals("Analytical Engines", organization.getString("name"))
        assertEquals("Research", organization.getJSONArray("units").getJSONObject(0).getString("name"))
        val emails = card.getJSONObject("emails")
        val work = emails.getJSONObject("emails1")
        assertEquals("ann@example.com", work.getString("address"))
        assertTrue(work.getJSONObject("contexts").getBoolean("work"))
        assertEquals(1, work.getInt("pref"))
        assertTrue(emails.getJSONObject("emails2").getJSONObject("contexts").getBoolean("private"))
        val phone = card.getJSONObject("phones").getJSONObject("phones1")
        assertTrue(phone.getJSONObject("features").getBoolean("mobile"))
        assertTrue(phone.getJSONObject("features").getBoolean("voice"))
        val address = card.getJSONObject("addresses").getJSONObject("addresses1").getJSONArray("components")
        val street = (0 until address.length()).map { address.getJSONObject(it) }.first { it.getString("kind") == "street" }
        assertEquals("1 Byron St;Apt 2", street.getString("value"))
        assertEquals("Line one\nLine two, with comma", card.getJSONObject("notes").getJSONObject("notes1").getString("note"))
        val birthday = card.getJSONObject("anniversaries").getJSONObject("anniversaries1")
        assertEquals("birth", birthday.getString("kind"))
        assertEquals(1815, birthday.getJSONObject("date").getInt("year"))
        assertTrue(card.getJSONObject("keywords").getBoolean("math"))
    }

    @Test
    fun personSurvivesAVCardRoundTrip() {
        val card = VCardToJsContact.convert(person)
        val again = VCardToJsContact.convert(JsContactToVCard.convert(card))
        val diff = card.keyList().filter { it != "emails" && !equalJson(card.opt(it), again.opt(it)) } + again.keyList().filter { !card.has(it) }
        assertTrue("differs: $diff\n$card\n$again", diff.isEmpty())
        // The writer lists the preferred address first, so generated map ids may swap; the values must not.
        fun emailValues(c: JSONObject) = c.getJSONObject("emails").entryList().map { (it.second as JSONObject).toString() }.toSet()
        assertEquals(setOf("ann@example.com", "ann@home.example"), emailValues(card).map { JSONObject(it).getString("address") }.toSet())
        assertEquals(emailValues(card).map { JSONObject(it).getString("address") }.toSet(), emailValues(again).map { JSONObject(it).getString("address") }.toSet())
    }

    @Test
    fun vCardOutputIsCrlfAndFoldedAt75Octets() {
        val long = "x".repeat(200)
        val vcard = JsContactToVCard.convert(
            VCardToJsContact.convert("BEGIN:VCARD\r\nVERSION:4.0\r\nUID:u\r\nFN:A\r\nNOTE:$long\r\nEND:VCARD\r\n"),
        )
        assertTrue(vcard.endsWith("END:VCARD\r\n"))
        vcard.split("\r\n").forEach { assertTrue(it.toByteArray().size <= 75) }
        assertEquals(long, VCardToJsContact.convert(vcard).getJSONObject("notes").getJSONObject("notes1").getString("note"))
    }

    @Test
    fun groupKeepsItsMembers() {
        val card = VCardToJsContact.convert(
            "BEGIN:VCARD\r\nVERSION:4.0\r\nUID:g\r\nFN:Team\r\nKIND:group\r\nMEMBER:urn:uuid:one\r\nMEMBER:urn:uuid:two\r\nEND:VCARD\r\n",
        )
        assertEquals("group", card.getString("kind"))
        assertTrue(card.getJSONObject("members").getBoolean("urn:uuid:two"))
        val vcard = JsContactToVCard.convert(card)
        assertTrue(vcard.contains("KIND:group\r\n"))
        assertTrue(vcard.contains("MEMBER:urn:uuid:one\r\n"))
    }

    @Test
    fun inlinePhotoBecomesADataUriAndBack() {
        val card = VCardToJsContact.convert(
            "BEGIN:VCARD\r\nVERSION:3.0\r\nUID:p\r\nFN:Pic\r\nPHOTO;ENCODING=b;TYPE=PNG:iVBORw0KGgo=\r\nEND:VCARD\r\n",
        )
        val media = card.getJSONObject("media").getJSONObject("media1")
        assertEquals("photo", media.getString("kind"))
        assertEquals("data:image/png;base64,iVBORw0KGgo=", media.getString("uri"))
        assertEquals("image/png", media.getString("mediaType"))
        assertTrue(JsContactToVCard.convert(card).contains("PHOTO;VALUE=URI:data:image/png;base64,iVBORw0KGgo="))
    }

    @Test
    fun aBlobOnlyPhotoIsNotInventedIntoAUri() {
        val card = JSONObject(
            """{"@type":"Card","version":"1.0","uid":"b","name":{"full":"B"},
               "media":{"m":{"@type":"Media","kind":"photo","blobId":"G123","mediaType":"image/jpeg"}}}""",
        )
        assertFalse(JsContactToVCard.convert(card).contains("PHOTO"))
    }

    @Test
    fun vendorPropertiesAreNotCarried() {
        val card = VCardToJsContact.convert(
            "BEGIN:VCARD\r\nVERSION:4.0\r\nUID:v\r\nFN:V\r\nX-CUSTOM:secret\r\nEND:VCARD\r\n",
        )
        assertFalse(card.toString().contains("secret"))
    }

    @Test
    fun parameterValuesAreCaretDecodedAndQuotedColonsSurvive() {
        val props = parseVCardProperties(
            "BEGIN:VCARD\r\nVERSION:4.0\r\nitem1.ADR;LABEL=\"A^nB: ^'C^'\":;;x\r\nEND:VCARD\r\n",
        )
        val adr = props.first { it.name == "ADR" }
        assertEquals("A\nB: \"C\"", adr.param("LABEL"))
        assertEquals(";;x", adr.value)
    }

    @Test
    fun notExactlyOneVCardIsRefused() {
        assertThrows(JsContactException::class.java) { VCardToJsContact.convert("FN:nobody") }
    }

    // --- patches ------------------------------------------------------------------------------

    private fun serverCard(): JSONObject {
        val card = VCardToJsContact.convert(person)
        card.put("id", "c1").put("addressBookIds", jsonObject("b1" to true))
        card.put("prodId", "-//server//").put("created", "2026-01-01T00:00:00Z")
        return card
    }

    @Test
    fun anUntouchedCardProducesNoPatch() {
        val previous = serverCard()
        assertEquals(0, contactPatch(previous, VCardToJsContact.convert(JsContactToVCard.convert(previous))).length())
    }

    @Test
    fun editingOnePhoneTouchesOnlyThatPhone() {
        val previous = serverCard()
        val vcard = JsContactToVCard.convert(previous).replace("+44 20 7946 0000", "+44 20 7946 1111")
        val patch = contactPatch(previous, VCardToJsContact.convert(vcard))
        assertEquals(listOf("phones/phones1/number"), patch.keyList())
        assertEquals("+44 20 7946 1111", patch.getString("phones/phones1/number"))
    }

    @Test
    fun removingAnEmailDeletesOnlyItsServerId() {
        val previous = serverCard()
        val vcard = JsContactToVCard.convert(previous).replace("EMAIL;TYPE=home:ann@home.example\r\n", "")
        val patch = contactPatch(previous, VCardToJsContact.convert(vcard))
        assertEquals(1, patch.length())
        assertTrue(patch.isNull("emails/emails2") || patch.opt("emails/emails2") == JSONObject.NULL)
    }

    @Test
    fun serverOnlyDataSurvivesAnEdit() {
        val previous = serverCard()
        val vcard = JsContactToVCard.convert(previous).replace("FN:Ann Lovelace", "FN:Ann L")
        val patch = contactPatch(previous, VCardToJsContact.convert(vcard))
        assertFalse(patch.keyList().any { it.startsWith("prodId") || it.startsWith("created") })
        assertEquals("Ann L", patch.getString("name/full"))
    }

    // --- urls ---------------------------------------------------------------------------------

    @Test
    fun contactUrlsRoundTripAndStayApartFromCalendarUrls() {
        val url = jmapCardUrl("https://h.example/jmap", "acc/1", "b 1", "c#2")
        assertEquals("https://h.example/.jmap/acc%2F1/ab/b%201/c%232", url)
        val id = parseJmapContactUrl(url, "https://h.example", "acc/1", card = true)
        assertEquals("b 1", id.addressBookId)
        assertEquals("c#2", id.cardId)
        val book = parseJmapContactUrl(jmapAddressBookUrl("https://h.example", "acc/1", "b 1"), "https://h.example", "acc/1", card = false)
        assertNull(book.cardId)
        assertThrows(JmapException::class.java) {
            parseJmapUrl(url, "https://h.example", "acc/1", event = true)
        }
        assertThrows(JmapException::class.java) {
            parseJmapContactUrl(jmapEventUrl("https://h.example", "acc/1", "ab", "x"), "https://h.example", "acc/1", card = true)
        }
    }
}
