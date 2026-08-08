package org.sainm.psy.respondent.ui

import android.content.Context
import android.content.res.Resources
import androidx.annotation.StringRes
import java.util.Locale

object AppText {
    private lateinit var resources: Resources

    fun initialize(context: Context, localeTag: String) {
        val configuration = context.applicationContext.resources.configuration
        val localizedConfiguration = android.content.res.Configuration(configuration).apply {
            setLocale(Locale.forLanguageTag(localeTag))
        }
        resources = context.applicationContext.createConfigurationContext(localizedConfiguration).resources
    }

    fun get(@StringRes id: Int, vararg arguments: Any?): String {
        check(::resources.isInitialized) { "AppText must be initialized before rendering UI" }
        return resources.getString(id, *arguments.map { it ?: "" }.toTypedArray())
    }
}

internal fun tr(@StringRes id: Int, vararg arguments: Any?): String = AppText.get(id, *arguments)
