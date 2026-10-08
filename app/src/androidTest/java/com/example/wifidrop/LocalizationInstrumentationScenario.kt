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

/** Real resource selection and runtime locale changes, without opening screens or requesting permissions. */
internal class LocalizationInstrumentationScenario(
    private val instrumentation: Instrumentation,
    private val expectedLanguage: String?
) {
    fun run() {
        val results = Bundle()
        val checks = mutableListOf<String>()
        val application = instrumentation.targetContext.applicationContext
        val originalResolver = AppStrings.current()

        fun verify(name: String, block: () -> Unit) { block(); checks += name }
        fun resourcesFor(language: String): Resources = application.createConfigurationContext(
            Configuration(application.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(language)) }
        ).resources

        try {
            verify("manifest_declares_english_and_spanish") {
                val languages = mutableListOf<String>()
                application.resources.getXml(R.xml.locales_config).use { parser ->
                    while (parser.next() != XmlPullParser.END_DOCUMENT) {
                        if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                            languages += parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name")
                        }
                    }
                }
                check(languages == listOf("en", "es"))
                if (Build.VERSION.SDK_INT >= 33) {
                    val localeConfig = LocaleConfig(application)
                    check(localeConfig.status == LocaleConfig.STATUS_SUCCESS)
                    check(localeConfig.supportedLocales?.toLanguageTags() == "en,es")
                }
            }

            val english = resourcesFor("en")
            val spanish = resourcesFor("es")
            val stringId = english.getIdentifier("flash_off", "string", application.packageName)
            val pluralId = english.getIdentifier("flash_files_ready_to_send", "plurals", application.packageName)
            check(stringId != 0 && pluralId != 0) { "Expected translated Flash resources are missing" }

            verify("real_android_resource_selection_en_es_and_fallback") {
                check(english.getString(stringId) != spanish.getString(stringId))
                check(resourcesFor("fr").getString(stringId) == english.getString(stringId))
            }

            verify("real_android_plural_rules_and_formatting") {
                for ((resources, language) in listOf(english to "en", spanish to "es")) {
                    for (quantity in listOf(0, 1, 2, 1_000_000)) {
                        val formatted = resources.getQuantityString(pluralId, quantity, quantity)
                        check(!formatted.contains("%1\$d"))
                        check(formatted.contains(quantity.toString()))
                        if (language == "en") check(formatted.contains(if (quantity == 1) "file ready" else "files ready"))
                        else check(formatted.contains(if (quantity == 1) "archivo preparado" else "archivos preparados"))
                    }
                }
            }

            verify("non_compose_accessors_read_current_resources_without_caching") {
                var currentResources = english
                AppStrings.resolver = AndroidAppStringResolver(
                    resources = { currentResources },
                    localizedResources = ::resourcesFor
                )
                check(appString(stringId) == english.getString(stringId))
                check(appQuantityString(pluralId, 1, 1) == english.getQuantityString(pluralId, 1, 1))
                check(appStringVariants(stringId) == listOf(english.getString(stringId), spanish.getString(stringId)))
                check(appQuantityStringVariants(pluralId, 2, 2) == listOf(
                    english.getQuantityString(pluralId, 2, 2), spanish.getQuantityString(pluralId, 2, 2)
                ))
                currentResources = spanish
                check(appString(stringId) == spanish.getString(stringId))
                check(appQuantityString(pluralId, 2, 2) == spanish.getQuantityString(pluralId, 2, 2))
                currentResources = english
                check(appString(stringId) == english.getString(stringId))
                AppStrings.resolver = originalResolver
            }

            verify("application_accessor_matches_effective_android_locale") {
                check(appString(stringId) == application.resources.getString(stringId))
                check(appQuantityString(pluralId, 2, 2) == application.resources.getQuantityString(pluralId, 2, 2))
                if (expectedLanguage != null) {
                    check(application.resources.configuration.locales[0].language == expectedLanguage)
                    check(appString(stringId) == resourcesFor(expectedLanguage).getString(stringId))
                }
            }
            results.putString("effective_language", application.resources.configuration.locales.toLanguageTags())
            results.putString("result", "PASS")
        } catch (failure: Throwable) {
            results.putString("result", "FAIL")
            results.putString("failure", failure.stackTraceToString())
        } finally {
            AppStrings.resolver = originalResolver
            results.putInt("checks_passed", checks.size)
            results.putString("checks", checks.joinToString(","))
            instrumentation.finish(if (results.getString("result") == "PASS") Activity.RESULT_OK else Activity.RESULT_CANCELED, results)
        }
    }

}
