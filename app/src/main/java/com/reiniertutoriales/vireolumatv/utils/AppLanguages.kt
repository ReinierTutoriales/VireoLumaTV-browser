package com.reiniertutoriales.vireolumatv.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import androidx.core.text.layoutDirection
import java.util.Locale

/**
 * The interface languages the app ships and the one chosen for it. The choice goes through
 * AppCompat: on Android 13+ it is the system's per-app language, also editable in Android settings;
 * before that AppCompat stores it itself (autoStoreLocales in the manifest).
 */
object AppLanguages {
    /** One tag per values-* folder, base English first; [AppLanguagesTest] keeps them in step. */
    val TAGS = listOf("en", "es", "de", "it", "pl", "ru", "uk", "vi", "zh-CN", "zh-TW", "he", "fa")

    /** Position in [names]: 0 follows the system, n is TAGS[n - 1]. */
    fun chosen(): Int {
        val current = AppCompatDelegate.getApplicationLocales()[0] ?: return 0
        val index = TAGS.indexOfFirst { same(Locale.forLanguageTag(it), current) }
        return index + 1 // An unknown stored language shows as "system", which is what applies.
    }

    /** [system] first, then every language in its own name, so it can be found whatever is shown now. */
    fun names(system: String): Array<String> = arrayOf(system) + TAGS.map { tag ->
        val locale = Locale.forLanguageTag(tag)
        locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    }

    /** Applies the language at [position] in [names]; open screens are recreated in it. */
    fun choose(position: Int) {
        if (position == chosen()) return
        AppCompatDelegate.setApplicationLocales(
            if (position == 0) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(TAGS[position - 1]))
    }

    /**
     * [base] with the chosen language. Android 13+ applies it to every context itself; before that
     * AppCompat applies it only to its activities, so services, receivers and the application
     * context need this to show text in the same language as the screens.
     */
    fun localized(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val chosen = AppCompatDelegate.getApplicationLocales()
        if (chosen.isEmpty) return base
        val config = Configuration(base.resources.configuration)
        ConfigurationCompat.setLocales(config, chosen)
        return base.createConfigurationContext(config)
    }

    /** The language [context] shows text in, for pages that need lang and dir attributes. */
    fun localeOf(context: Context): Locale =
        ConfigurationCompat.getLocales(context.resources.configuration)[0] ?: Locale.getDefault()

    fun isRtl(locale: Locale) = locale.layoutDirection == View.LAYOUT_DIRECTION_RTL

    private fun same(a: Locale, b: Locale) =
        a.toLanguageTag() == b.toLanguageTag() || (a.language == b.language && a.country.isEmpty())
}
