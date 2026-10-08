package com.example.wifidrop

import android.app.Activity
import android.app.Instrumentation
import android.app.LocaleConfig
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import org.xmlpull.v1.XmlPullParser

/**
 * Black-box resource checks for the final APK, including builds optimized by R8.
 * Only Android APIs and resource names cross into the target application. Do not reference its
 * R class, accessors, internal models or implementation classes from this scenario.
 */
internal class LocalizationReleaseInstrumentationScenario(
    private val instrumentation: Instrumentation,
    private val expectedLanguage: String?
) {
    fun run() {
        val results = Bundle()
        val checks = mutableListOf<String>()
        val context = instrumentation.targetContext

        fun verify(name: String, block: () -> Unit) { block(); checks += name }
        fun resourceId(name: String, kind: String): Int =
            context.resources.getIdentifier(name, kind, context.packageName).also {
                check(it != 0) { "Missing target resource $kind/$name" }
            }
        fun resourcesFor(language: String): Resources = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(language))
            }
        ).resources

        try {
            verify("release_manifest_declares_english_and_spanish") {
                val languages = mutableListOf<String>()
                context.resources.getXml(resourceId("locales_config", "xml")).use { parser ->
                    while (parser.next() != XmlPullParser.END_DOCUMENT) {
                        if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                            languages += parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name")
                        }
                    }
                }
                check(languages == listOf("en", "es"))
                if (Build.VERSION.SDK_INT >= 33) {
                    val localeConfig = LocaleConfig(context)
                    check(localeConfig.status == LocaleConfig.STATUS_SUCCESS)
                    check(localeConfig.supportedLocales?.toLanguageTags() == "en,es")
                }
            }

            val stringId = resourceId("flash_off", "string")
            val pluralId = resourceId("flash_files_ready_to_send", "plurals")
            val english = resourcesFor("en")
            val spanish = resourcesFor("es")

            verify("release_resources_resolve_english_and_spanish") {
                check(english.getString(stringId) == "Flash is off.")
                check(spanish.getString(stringId) == "Flash está desactivado.")
            }

            verify("release_unsupported_language_falls_back_to_english") {
                val fallback = resourcesFor("fr")
                check(fallback.getString(stringId) == english.getString(stringId))
                check(fallback.getQuantityString(pluralId, 2, 2) == english.getQuantityString(pluralId, 2, 2))
            }

            verify("release_real_android_plurals_preserve_counts_and_formats") {
                for (quantity in listOf(0, 1, 2, 1_000_000)) {
                    val expectedEnglish = if (quantity == 1) "$quantity file ready to send" else "$quantity files ready to send"
                    val expectedSpanish = if (quantity == 1) "$quantity archivo preparado para enviar" else "$quantity archivos preparados para enviar"
                    check(english.getQuantityString(pluralId, quantity, quantity) == expectedEnglish)
                    check(spanish.getQuantityString(pluralId, quantity, quantity) == expectedSpanish)
                }
            }

            if (expectedLanguage != null) {
                verify("release_effective_language_matches_external_selection") {
                    check(expectedLanguage in listOf("en", "es")) { "expected-language must be en or es" }
                    check(context.resources.configuration.locales[0].language == expectedLanguage)
                    val expected = resourcesFor(expectedLanguage)
                    check(context.resources.getString(stringId) == expected.getString(stringId))
                    check(context.resources.getQuantityString(pluralId, 2, 2) == expected.getQuantityString(pluralId, 2, 2))
                }
            }
            results.putString("effective_language", context.resources.configuration.locales.toLanguageTags())
            results.putString("target_package", context.packageName)
            results.putString("result", "PASS")
        } catch (failure: Throwable) {
            results.putString("result", "FAIL")
            results.putString("failure", failure.stackTraceToString())
        } finally {
            results.putInt("checks_passed", checks.size)
            results.putString("checks", checks.joinToString(","))
            instrumentation.finish(if (results.getString("result") == "PASS") Activity.RESULT_OK else Activity.RESULT_CANCELED, results)
        }
    }
}
