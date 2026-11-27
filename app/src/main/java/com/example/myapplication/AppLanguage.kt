package com.example.myapplication

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.annotation.StringRes
import java.util.Locale

internal enum class AppLanguage(val tag: String, val nativeName: String) {
    CHINESE("zh-CN", "简体中文"),
    ENGLISH("en", "English");

    val locale: Locale get() = Locale.forLanguageTag(tag)

    fun wrap(context: Context): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList(locale))
        return context.createConfigurationContext(configuration)
    }

    companion object {
        private const val preferences = "display_settings"
        private const val language = "display_language"

        fun read(context: Context): AppLanguage {
            val tag = context.getSharedPreferences(preferences, Context.MODE_PRIVATE).getString(language, null)
            return entries.firstOrNull { it.tag == tag } ?: CHINESE
        }

        fun write(context: Context, selection: AppLanguage) {
            context.getSharedPreferences(preferences, Context.MODE_PRIVATE).edit()
                .putString(language, selection.tag)
                .apply()
        }
    }
}

class FlApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        refreshAppStrings(this)
    }
}

// Background tasks can resolve messages without retaining an Activity that will be recreated.
@Volatile private lateinit var localizedResources: Map<AppLanguage, Resources>
@Volatile private var currentLanguage = AppLanguage.CHINESE

internal fun refreshAppStrings(context: Context) {
    val app = context.applicationContext
    localizedResources = AppLanguage.entries.associateWith { it.wrap(app).resources }
    currentLanguage = AppLanguage.read(app)
}

internal val appLocale: Locale get() = currentLanguage.locale

internal fun originalChineseText(@StringRes id: Int): String =
    localizedResources.getValue(AppLanguage.CHINESE).getString(id)

internal fun appText(@StringRes id: Int, vararg args: Any): String =
    localizedResources.getValue(currentLanguage).let {
        if (args.isEmpty()) it.getString(id) else it.getString(id, *args)
    }
