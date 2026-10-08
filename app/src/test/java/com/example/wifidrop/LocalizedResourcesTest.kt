package com.example.wifidrop

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.After
import org.junit.Before
import org.w3c.dom.Element

/** Uses the shipped XML copy in local JVM tests without a production-language fallback. */
open class LocalizedResourcesTest {
    private var previousResolver: AppStringResolver? = null

    @Before
    fun installSpanishResources() {
        previousResolver = AppStrings.resolver
        useAppLocale("es")
    }

    @After
    fun restoreResources() {
        AppStrings.resolver = previousResolver
    }

    protected fun useAppLocale(languageTag: String) {
        AppStrings.resolver = XmlAppStringResolver(Locale.forLanguageTag(languageTag))
    }
}

internal data class StringResourceEntry(
    val name: String,
    val values: Map<String, String>,
    val translatable: Boolean,
    val formatted: Boolean
) {
    val isPlural: Boolean get() = "string" !in values
}

internal object TestResourceCatalog {
    private val catalogs = mutableMapOf<String, Map<String, StringResourceEntry>>()

    val resourceRoot: File by lazy {
        listOf(File("src/main/res"), File("app/src/main/res"))
            .firstOrNull { File(it, "values/strings.xml").isFile }
            ?: error("Run these tests from the repository or Android app module.")
    }

    @Synchronized
    fun read(directory: String): Map<String, StringResourceEntry> =
        catalogs.getOrPut(directory) { parse(directory) }

    private fun parse(directory: String): Map<String, StringResourceEntry> {
        val entries = linkedMapOf<String, StringResourceEntry>()
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        File(resourceRoot, directory).listFiles().orEmpty()
            .filter { it.extension == "xml" }.sortedBy { it.name }.forEach { file ->
                val document = factory.newDocumentBuilder().parse(file)
                val children = document.documentElement.childNodes
                for (index in 0 until children.length) {
                    val child = children.item(index) as? Element ?: continue
                    if (child.tagName != "string" && child.tagName != "plurals") continue
                    val name = child.getAttribute("name")
                    val values = if (child.tagName == "string") {
                        mapOf("string" to decode(child.textContent))
                    } else {
                        val items = child.getElementsByTagName("item")
                        buildMap {
                            for (itemIndex in 0 until items.length) {
                                val item = items.item(itemIndex) as Element
                                val quantity = item.getAttribute("quantity")
                                check(put(quantity, decode(item.textContent)) == null) {
                                    "Duplicate plural quantity $name/$quantity in $file"
                                }
                            }
                        }
                    }
                    val entry = StringResourceEntry(
                        name, values,
                        child.getAttribute("translatable") != "false",
                        child.getAttribute("formatted") != "false"
                    )
                    val key = "${child.tagName}/$name"
                    check(entries.put(key, entry) == null) { "Duplicate string resource $key in $file" }
                }
            }
        return entries
    }

    /** The XML parser handles entities; this handles Android's string escaping. */
    private fun decode(raw: String): String {
        val quoted = raw.trim().startsWith('"') && raw.trim().endsWith('"')
        val normalized = if (quoted) raw.trim().removeSurrounding("\"")
            else raw.replace(Regex("\\s+"), " ").trim()
        return buildString {
            var index = 0
            while (index < normalized.length) {
                val current = normalized[index++]
                if (current != '\\' || index == normalized.length) {
                    append(current)
                    continue
                }
                when (val escaped = normalized[index++]) {
                    'n' -> append('\n')
                    't' -> append('\t')
                    'u' -> {
                        check(index + 4 <= normalized.length) { "Incomplete Unicode escape in resource" }
                        append(normalized.substring(index, index + 4).toInt(16).toChar())
                        index += 4
                    }
                    else -> append(escaped)
                }
            }
        }
    }
}

internal class XmlAppStringResolver(private val locale: Locale) : AppStringResolver {
    private val defaults = TestResourceCatalog.read("values")
    private val translated = if (locale.language == "es") TestResourceCatalog.read("values-es") else emptyMap()
    private val stringNames = R.string::class.java.fields.associate { it.getInt(null) to it.name }
    private val pluralNames = R.plurals::class.java.fields.associate { it.getInt(null) to it.name }

    override fun string(id: Int, vararg formatArgs: Any): String {
        val name = checkNotNull(stringNames[id]) { "Unknown string resource ID $id" }
        val value = entry("string/$name").values.getValue("string")
        return format(value, formatArgs)
    }

    override fun quantityString(id: Int, quantity: Int, vararg formatArgs: Any): String {
        val name = checkNotNull(pluralNames[id]) { "Unknown plural resource ID $id" }
        val values = entry("plurals/$name").values
        // Both supported languages use one for integer 1 and other for ordinary remaining counts.
        // Android's complete CLDR rules (including large Spanish counts) are tested on-device.
        val value = values[if (quantity == 1) "one" else "other"] ?: values.getValue("other")
        return format(value, formatArgs)
    }

    override fun stringVariants(id: Int, vararg formatArgs: Any): List<String> =
        listOf(Locale.ENGLISH, Locale.forLanguageTag("es")).map { language ->
            XmlAppStringResolver(language).string(id, *formatArgs)
        }.distinct()

    override fun quantityStringVariants(id: Int, quantity: Int, vararg formatArgs: Any): List<String> =
        listOf(Locale.ENGLISH, Locale.forLanguageTag("es")).map { language ->
            XmlAppStringResolver(language).quantityString(id, quantity, *formatArgs)
        }.distinct()

    private fun entry(name: String): StringResourceEntry = translated[name] ?: defaults.getValue(name)

    private fun format(value: String, args: Array<out Any>): String =
        if (args.isEmpty()) value else String.format(locale, value, *args)
}
