package com.hikari.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Structure and value bounds of the exported Camera Raw / Lightroom profile: well-formed namespaced XMP,
 * the crs fields a profile needs (ProcessVersion 11.0, RGBTable hash and table attribute), the table block
 * layout, and every number inside its range. Adobe publishes no schema for the table; what is checked here
 * is the layout the exporter documents (XmpProfile).
 */
class XmpStructureTest {
    private val NS_X = "adobe:ns:meta/"
    private val NS_RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
    private val NS_CRS = "http://ns.adobe.com/camera-raw-settings/1.0/"

    private fun identity(n: Int) = FloatArray(n * n * n * 3).also { a ->
        for (b in 0 until n) for (g in 0 until n) for (r in 0 until n) { val i = ((b * n + g) * n + r) * 3; a[i] = r / (n - 1f); a[i + 1] = g / (n - 1f); a[i + 2] = b / (n - 1f) }
    }

    private fun parse(xml: String): Element {
        val f = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true; isExpandEntityReferences = false }
        return f.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))).documentElement
    }

    private fun description(xml: String): Element {
        val root = parse(xml)
        assertEquals("xmpmeta", root.localName); assertEquals(NS_X, root.namespaceURI)
        val rdf = root.getElementsByTagNameNS(NS_RDF, "RDF").item(0) as Element
        val d = rdf.getElementsByTagNameNS(NS_RDF, "Description")
        assertEquals(1, d.length)
        return d.item(0) as Element
    }

    private fun attr(e: Element, name: String) = e.getAttributeNS(NS_CRS, name)

    private fun altText(e: Element, name: String): String {
        val n = e.getElementsByTagNameNS(NS_CRS, name)
        assertEquals("one crs:$name", 1, n.length)
        val li = (n.item(0) as Element).getElementsByTagNameNS(NS_RDF, "li")
        assertEquals(1, li.length)
        assertEquals("x-default", (li.item(0) as Element).getAttributeNS("http://www.w3.org/XML/1998/namespace", "lang"))
        return li.item(0).textContent
    }

    @Test fun profileIsWellFormedXmpWithTheFieldsCameraRawNeeds() {
        val xml = XmpProfile.xmp("Hikari Test", "Hikari", "a look", identity(33), 33)
        assertTrue(xml.startsWith("<x:xmpmeta"))
        val d = description(xml)
        assertEquals("", d.getAttributeNS(NS_RDF, "about"))
        assertEquals("Look", attr(d, "PresetType"))
        assertEquals("11.0", attr(d, "ProcessVersion"))
        assertTrue(attr(d, "Version").matches(Regex("\\d+\\.\\d+")))
        assertTrue(attr(d, "UUID").matches(Regex("[0-9A-F]{32}")))
        val md5 = attr(d, "RGBTable")
        assertTrue(md5.matches(Regex("[0-9A-F]{32}")))
        assertTrue("table attribute for the hash", attr(d, "Table_$md5").isNotEmpty())
        for (flag in listOf("SupportsAmount", "SupportsColor", "SupportsMonochrome", "SupportsHighDynamicRange", "SupportsNormalDynamicRange",
            "SupportsSceneReferred", "SupportsOutputReferred", "RequiresRGBTables", "ConvertToGrayscale", "HasSettings"))
            assertTrue("$flag is True/False", attr(d, flag) in setOf("True", "False"))
        assertEquals("True", attr(d, "SupportsAmount"))
        assertEquals("False", attr(d, "ConvertToGrayscale"))
        assertEquals("Hikari Test", altText(d, "Name")); assertEquals("Hikari", altText(d, "Group")); assertEquals("a look", altText(d, "Description"))
    }

    @Test fun tableBlockHasTheDocumentedLayoutAndEveryNumberInRange() {
        val xml = XmpProfile.xmp("x", "g", "d", identity(33), 33)
        val d = description(xml)
        val md5 = attr(d, "RGBTable")
        val encoded = attr(d, "Table_$md5")
        assertFalse("attribute value must not contain XML-special characters", encoded.any { it in "\"<>&" })
        val payload = XmpProfile.b85Decode(encoded)
        val inflatedSize = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(16 + 32 * 32 * 32 * 6 + 12 + 16, inflatedSize)
        val block = ByteArray(inflatedSize)
        assertEquals(inflatedSize, Inflater().apply { setInput(payload, 4, payload.size - 4) }.inflate(block))
        assertEquals(md5, java.security.MessageDigest.getInstance("MD5").digest(block).joinToString("") { "%02X".format(it) })
        val b = ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(listOf(1, 1, 3, 32), listOf(b.getInt(0), b.getInt(4), b.getInt(8), b.getInt(12)))
        val footer = 16 + 32 * 32 * 32 * 6
        assertEquals(listOf(0, 1, 0), listOf(b.getInt(footer), b.getInt(footer + 4), b.getInt(footer + 8)))   // sRGB primaries, sRGB gamma, clip
        assertEquals(0.0, b.getDouble(footer + 12), 0.0); assertEquals(2.0, b.getDouble(footer + 20), 0.0)    // amount slider range
        val t = XmpProfile.decode(block)
        assertEquals(32 * 32 * 32 * 3, t.size)
        assertTrue("table values in 0..1", t.all { it in 0f..1f })
        // an identity table decodes to the identity ramp
        var k = 0
        for (r in 0 until 32) for (g in 0 until 32) for (bl in 0 until 32) { assertEquals(r / 31f, t[k], 2e-5f); k += 3 }
    }

    @Test fun valuesOutsideZeroToOneAreClampedNeverWrapped() {
        val n = 5; val lut = identity(n)
        lut[0] = -0.4f; lut[1] = 1.7f; lut[2] = Float.POSITIVE_INFINITY.coerceAtMost(9f)   // a corner pushed far out of range
        lut[lut.size - 1] = -3f
        val d = description(XmpProfile.xmp("x", "g", "d", lut, n))
        val md5 = attr(d, "RGBTable")
        val payload = XmpProfile.b85Decode(attr(d, "Table_$md5"))
        val block = ByteArray(ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).int)
        Inflater().apply { setInput(payload, 4, payload.size - 4) }.inflate(block)
        val t = XmpProfile.decode(block)
        assertTrue("clamped into 0..1, no wrap-around", t.all { it in 0f..1f })
        assertEquals(0f, t[0], 1e-4f); assertEquals(1f, t[1], 1e-4f); assertEquals(1f, t[2], 1e-4f)
    }

    @Test fun nameWithXmlSpecialCharactersAndUnicodeStaysWellFormed() {
        val name = "A & B <c> \"d\" 'e' 日系 Provia"
        val d = description(XmpProfile.xmp(name, "G&H", "<x>&</x>", identity(2), 2))
        assertEquals(name, altText(d, "Name")); assertEquals("G&H", altText(d, "Group")); assertEquals("<x>&</x>", altText(d, "Description"))
    }

    @Test fun smallestAndLargestLutSizesProduceValidProfiles() {
        for (n in listOf(2, 17, 33, 65)) {
            val d = description(XmpProfile.xmp("n$n", "g", "d", identity(n), n))
            assertTrue(attr(d, "Table_${attr(d, "RGBTable")}").length > 20)
        }
    }

    @Test fun sameLookGivesSameFileDifferentLooksDifferentIds() {
        val a = XmpProfile.xmp("Hikari A", "Hikari", "d", identity(9), 9)
        assertEquals(a, XmpProfile.xmp("Hikari A", "Hikari", "d", identity(9), 9))
        val warm = identity(9).also { it[it.size - 3] = 0.9f }
        val b = XmpProfile.xmp("Hikari A", "Hikari", "d", warm, 9)
        assertNotEquals(attr(description(a), "UUID"), attr(description(b), "UUID"))
        assertNotEquals(attr(description(a), "RGBTable"), attr(description(b), "RGBTable"))
    }

    @Test fun base85AlphabetHasNoDuplicatesAndIsAttributeSafe() {
        val alphabet = Regex("B85 = \"([^\"]+)\"").find(java.io.File(Awt.root, "studio/src/main/kotlin/com/hikari/studio/XmpProfile.kt").readText())!!.groupValues[1]
        assertEquals(85, alphabet.length); assertEquals(85, alphabet.toSet().size)
        assertFalse(alphabet.any { it in "\"<>&" })
    }
}
