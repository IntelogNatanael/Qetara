package com.example.wifidrop

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import java.util.Locale

/** Localized copy for non-Compose presenters, model defaults and background services. */
fun appString(@StringRes id: Int, vararg formatArgs: Any): String =
    AppStrings.current().string(id, *formatArgs)

fun appQuantityString(@PluralsRes id: Int, quantity: Int, vararg formatArgs: Any): String =
    AppStrings.current().quantityString(id, quantity, *formatArgs)

/** Compatibility for identifying app-generated legacy status copy, never arbitrary/user text. */
fun appStringVariants(@StringRes id: Int, vararg formatArgs: Any): List<String> =
    AppStrings.current().stringVariants(id, *formatArgs)

fun appQuantityStringVariants(@PluralsRes id: Int, quantity: Int, vararg formatArgs: Any): List<String> =
    AppStrings.current().quantityStringVariants(id, quantity, *formatArgs)

internal interface AppStringResolver {
    fun string(@StringRes id: Int, vararg formatArgs: Any): String
    fun quantityString(@PluralsRes id: Int, quantity: Int, vararg formatArgs: Any): String
    fun stringVariants(@StringRes id: Int, vararg formatArgs: Any): List<String>
    fun quantityStringVariants(@PluralsRes id: Int, quantity: Int, vararg formatArgs: Any): List<String>
}

internal class AndroidAppStringResolver(
    private val resources: () -> Resources,
    private val localizedResources: (String) -> Resources
) : AppStringResolver {
    override fun string(id: Int, vararg formatArgs: Any): String = resources().let {
        if (formatArgs.isEmpty()) it.getString(id) else it.getString(id, *formatArgs)
    }

    override fun quantityString(id: Int, quantity: Int, vararg formatArgs: Any): String = resources().let {
        if (formatArgs.isEmpty()) it.getQuantityString(id, quantity)
        else it.getQuantityString(id, quantity, *formatArgs)
    }

    override fun stringVariants(id: Int, vararg formatArgs: Any): List<String> =
        listOf("en", "es").map { language ->
            localizedResources(language).let {
                if (formatArgs.isEmpty()) it.getString(id) else it.getString(id, *formatArgs)
            }
        }.distinct()

    override fun quantityStringVariants(id: Int, quantity: Int, vararg formatArgs: Any): List<String> =
        listOf("en", "es").map { language ->
            localizedResources(language).let {
                if (formatArgs.isEmpty()) it.getQuantityString(id, quantity)
                else it.getQuantityString(id, quantity, *formatArgs)
            }
        }.distinct()
}

internal object AppStrings {
    @Volatile
    internal var resolver: AppStringResolver? = null

    fun initialize(context: Context) {
        val application = context.applicationContext
        // Read the current Resources on every call. Android updates this configuration for
        // system-language changes and, on Android 13+, the platform per-app language setting.
        // Retaining an Activity or a copied Configuration would make later calls stale.
        resolver = AndroidAppStringResolver(
            resources = { application.resources },
            localizedResources = { language ->
                val configuration = Configuration(application.resources.configuration)
                configuration.setLocale(Locale.forLanguageTag(language))
                application.createConfigurationContext(configuration).resources
            }
        )
    }

    fun current(): AppStringResolver = checkNotNull(resolver) {
        "QetaraApplication must initialize localized resources before they are used."
    }
}
