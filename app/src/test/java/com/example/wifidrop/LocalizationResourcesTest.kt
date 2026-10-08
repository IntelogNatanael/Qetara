package com.example.wifidrop

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizationResourcesTest : LocalizedResourcesTest() {
    @Test
    fun englishAndSpanishHaveTheSameTranslatableResources() {
        val english = TestResourceCatalog.read("values").filterValues { it.translatable }
        val spanish = TestResourceCatalog.read("values-es").filterValues { it.translatable }
        assertFalse("The English catalog must include translated UI copy", english.isEmpty())
        assertEquals("Every supported language must provide the same resource keys", english.keys, spanish.keys)
        english.forEach { (name, entry) ->
            assertEquals("Resource type differs for $name", entry.isPlural, spanish.getValue(name).isPlural)
        }
    }

    @Test
    fun translationsPreserveEveryFormattingArgument() {
        val english = TestResourceCatalog.read("values").filterValues { it.translatable }
        val spanish = TestResourceCatalog.read("values-es")
        english.forEach { (name, entry) ->
            val translated = spanish.getValue(name)
            assertEquals("Formatting mode differs for $name", entry.formatted, translated.formatted)
            if (!entry.formatted) return@forEach
            // Languages can have different CLDR categories; a missing category uses other.
            (entry.values.keys + translated.values.keys).forEach { quantity ->
                val text = entry.values[quantity] ?: entry.values.getValue("other")
                val reference = formatSignature(text)
                val translatedText = translated.values[quantity] ?: translated.values.getValue("other")
                assertEquals("Formatting arguments differ for $name/$quantity", reference, formatSignature(translatedText))
                verifyFormatting("$name/$quantity/en", text, reference, Locale.ENGLISH)
                verifyFormatting("$name/$quantity/es", translatedText, reference, Locale.forLanguageTag("es"))
            }
        }
    }

    @Test
    fun everyPluralHasSingularAndOtherFormsWithCompatibleArguments() {
        listOf("values", "values-es").forEach { directory ->
            val locale = if (directory == "values-es") Locale.forLanguageTag("es") else Locale.ENGLISH
            val plurals = TestResourceCatalog.read(directory).values.filter { it.isPlural }
            assertFalse("$directory must use quantity resources for count-dependent copy", plurals.isEmpty())
            plurals.forEach { entry ->
                val requiredQuantities = if (directory == "values-es") listOf("one", "many", "other")
                    else listOf("one", "other")
                assertTrue(
                    "$directory/${entry.name} must provide $requiredQuantities",
                    entry.values.keys.containsAll(requiredQuantities)
                )
                val expected = formatSignature(entry.values.getValue("other"))
                entry.values.forEach { (quantity, text) ->
                    // Other defines the full argument contract. A singular form such as
                    // "Send file" may omit the count, but cannot introduce a new argument
                    // position or reinterpret an existing argument as another type.
                    formatSignature(text).forEach { (index, conversion) ->
                        assertEquals(
                            "Incompatible argument $index in $directory/${entry.name}/$quantity",
                            expected[index], conversion
                        )
                    }
                    // Use the complete argument list for every form, just as the caller does.
                    verifyFormatting("$directory/${entry.name}/$quantity", text, expected, locale)
                }
            }
        }
    }

    @Test
    fun singularSendActionCanOmitItsCountWhilePluralRendersIt() {
        useAppLocale("en")
        assertEquals("Send file", appQuantityString(R.plurals.shell_send_files, 1, 1))
        assertEquals("Send 2 files", appQuantityString(R.plurals.shell_send_files, 2, 2))
        useAppLocale("es")
        assertEquals("Enviar archivo", appQuantityString(R.plurals.shell_send_files, 1, 1))
        assertEquals("Enviar 2 archivos", appQuantityString(R.plurals.shell_send_files, 2, 2))
    }

    @Test
    fun nonComposeAccessorResolvesAgainAfterLocaleChanges() {
        val english = TestResourceCatalog.read("values")
        val spanish = TestResourceCatalog.read("values-es")
        val candidate = english.values.first { entry ->
            !entry.isPlural && entry.translatable && formatSignature(entry.values.getValue("string")).isEmpty() &&
                spanish["string/${entry.name}"]?.values?.get("string") != entry.values.getValue("string")
        }
        val id = R.string::class.java.getField(candidate.name).getInt(null)
        useAppLocale("en")
        assertEquals(candidate.values.getValue("string"), appString(id))
        useAppLocale("es")
        assertEquals(spanish.getValue("string/${candidate.name}").values.getValue("string"), appString(id))
        useAppLocale("en")
        assertEquals(candidate.values.getValue("string"), appString(id))
    }

    private fun verifyFormatting(name: String, text: String, signature: Map<Int, String>, locale: Locale) {
        if (signature.isEmpty()) return
        val arguments = Array<Any>(signature.keys.max()) { index ->
            when (val conversion = signature[index + 1]) {
                "d", "o", "x" -> 7
                "e", "f", "g", "a" -> 2.5
                "c" -> 'Q'
                "b" -> true
                else -> if (conversion?.startsWith("t") == true) 0L else "sample"
            }
        }
        val result = runCatching { String.format(locale, text, *arguments) }
        assertTrue("Invalid format in $name: ${result.exceptionOrNull()}", result.isSuccess)
    }
}

/** Compare argument position and conversion, allowing translators to reorder arguments. */
internal fun formatSignature(value: String): Map<Int, String> {
    val pattern = Regex("%(?:(\\d+)\\$)?([-#+ 0,(<]*)(?:\\d+)?(?:\\.\\d+)?([tT])?([a-zA-Z%])")
    var implicitIndex = 0
    var previousIndex = 0
    return buildMap {
        pattern.findAll(value).forEach { match ->
            val conversion = match.groupValues[4].lowercase(Locale.ROOT)
            if (conversion == "%" || conversion == "n") return@forEach
            val explicitIndex = match.groupValues[1].toIntOrNull()
            val index = explicitIndex ?: if ('<' in match.groupValues[2]) previousIndex else ++implicitIndex
            check(index > 0) { "Invalid format argument index in $value" }
            previousIndex = index
            val type = match.groupValues[3].lowercase(Locale.ROOT) + conversion
            val prior = put(index, type)
            check(prior == null || prior == type) { "Conflicting argument types in $value" }
        }
    }
}
