package com.garasi.desktop

import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * UI text. Loads the Android app's strings*.xml (copied in at build time) plus desktop_strings.xml, so all
 * UI text lives in resource files and other UI languages can be added the same way.
 */
object S {
    private val map: Map<String, String> by lazy {
        val out = mutableMapOf<String, String>()
        listOf("garasi/strings.xml", "garasi/strings_quality.xml", "garasi/desktop_strings.xml").forEach { res ->
            val stream = S::class.java.classLoader.getResourceAsStream(res) ?: return@forEach
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream)
            val nodes = doc.getElementsByTagName("string")
            for (i in 0 until nodes.length) {
                val n = nodes.item(i)
                val name = n.attributes.getNamedItem("name")?.nodeValue ?: continue
                out[name] = unescape(n.textContent)
            }
        }
        out
    }

    private fun unescape(s: String) = s.replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n").replace("\\@", "@")
        .trim().let { if (it.startsWith("\"") && it.endsWith("\"") && it.length > 1) it.substring(1, it.length - 1) else it }

    operator fun invoke(key: String, vararg args: Any): String {
        val raw = map[key] ?: return key
        return if (args.isEmpty()) raw.replace("%%", "%") else runCatching { String.format(Locale.US, raw, *args) }.getOrDefault(raw)
    }
}
