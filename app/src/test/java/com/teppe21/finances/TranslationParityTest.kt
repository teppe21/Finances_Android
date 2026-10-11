package com.teppe21.finances

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * English and Hungarian must stay consistent: every string exists in both files, with the
 * same placeholders, and plurals have the same quantities.
 */
class TranslationParityTest {

    private data class Entry(val placeholders: List<String>, val quantities: Set<String>)

    private val placeholder = Regex("%(\\d+\\$)?[sdf]")

    private fun read(path: String): Map<String, Entry> {
        val file = File(path)
        assertTrue("missing $path", file.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val out = mutableMapOf<String, Entry>()
        val strings = doc.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val e = strings.item(i) as Element
            out[e.getAttribute("name")] = Entry(placeholder.findAll(e.textContent).map { it.value }.sorted().toList(), emptySet())
        }
        val plurals = doc.getElementsByTagName("plurals")
        for (i in 0 until plurals.length) {
            val e = plurals.item(i) as Element
            val items = e.getElementsByTagName("item")
            val quantities = (0 until items.length).map { (items.item(it) as Element).getAttribute("quantity") }.toSet()
            val other = (0 until items.length).map { items.item(it) as Element }.first { it.getAttribute("quantity") == "other" }
            out[e.getAttribute("name")] = Entry(placeholder.findAll(other.textContent).map { it.value }.sorted().toList(), quantities)
        }
        return out
    }

    @Test
    fun `hungarian has every english string with the same placeholders`() {
        val en = read("src/main/res/values/strings.xml")
        val hu = read("src/main/res/values-hu/strings.xml")
        assertEquals("keys only in English", emptySet<String>(), en.keys - hu.keys)
        assertEquals("keys only in Hungarian", emptySet<String>(), hu.keys - en.keys)
        val mismatched = en.filter { (key, entry) -> hu.getValue(key) != entry }.keys
        assertEquals("placeholders or plural quantities differ", emptySet<String>(), mismatched)
    }
}
