package com.reiniertutoriales.vireolumatv.utils

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.core.os.ConfigurationCompat
import androidx.core.text.layoutDirection
import java.io.File
import java.util.Locale

/**
 * The interface languages the app ships and the one chosen for it. On Android 13+ the choice is
 * the system's per-app language ([LocaleManager]), also editable in Android settings, and the
 * system applies it everywhere. Before Android 13 the app stores it and applies it itself through
 * [localized] in each screen's and service's base context.
 */
object AppLanguages {
    /** One tag per values-* folder, base English first; [AppLanguagesTest] keeps them in step. */
    val TAGS = listOf("en", "es", "de", "it", "pl", "ru", "uk", "vi", "zh-CN", "zh-TW", "he", "fa")

    private const val PREFS = "app_language"
    private const val TAG = "tag"
    /** Where AppCompat stored the choice before Android 13, read once to keep it after the move. */
    private const val APPCOMPAT_RECORD = "androidx.appcompat.app.AppCompatDelegate.application_locales_record_file"

    /** Position in [names]: 0 follows the system, n is TAGS[n - 1]. */
    fun chosen(context: Context): Int {
        val current = current(context) ?: return 0
        val index = TAGS.indexOfFirst { same(Locale.forLanguageTag(it), current) }
        return index + 1 // An unknown stored language shows as "system", which is what applies.
    }

    /** [system] first, then every language in its own name, so it can be found whatever is shown now. */
    fun names(system: String): Array<String> = arrayOf(system) + TAGS.map { tag ->
        val locale = Locale.forLanguageTag(tag)
        locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    }

    /**
     * Applies the language at [position] in [names]. Android 13+ recreates every screen itself;
     * before that [activity], the screen where it was chosen, is recreated in the new language.
     */
    fun choose(activity: Activity, position: Int) {
        if (position == chosen(activity)) return
        val tag = if (position == 0) "" else TAGS[position - 1]
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setSystemLocales(activity, tag)
        } else {
            activity.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(TAG, tag) }
            activity.recreate()
        }
    }

    /**
     * [base] with the chosen language. Android 13+ applies it to every context itself; before that
     * screens, services, receivers and the application context need this to show the chosen one.
     */
    fun localized(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val chosen = current(base) ?: return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(chosen)
        config.setLayoutDirection(chosen)
        return base.createConfigurationContext(config)
    }

    /** The language [context] shows text in, for pages that need lang and dir attributes. */
    fun localeOf(context: Context): Locale =
        ConfigurationCompat.getLocales(context.resources.configuration)[0] ?: Locale.getDefault()

    fun isRtl(locale: Locale) = locale.layoutDirection == View.LAYOUT_DIRECTION_RTL

    /** The chosen language, or null when the app follows the system. */
    private fun current(context: Context): Locale? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return context.getSystemService(LocaleManager::class.java)?.applicationLocales?.get(0)
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val tag = prefs.getString(TAG, null) ?: migrateFromAppCompat(context).also { prefs.edit { putString(TAG, it) } }
        return tag.takeIf { it.isNotEmpty() }?.let(Locale::forLanguageTag)
    }

    /** The tag AppCompat stored before Android 13 ("" when none), so an upgrade keeps the choice. */
    private fun migrateFromAppCompat(context: Context): String {
        val record = File(context.filesDir, APPCOMPAT_RECORD)
        val tag = runCatching { Regex("application_locales=\"([^\",]*)").find(record.readText())?.groupValues?.get(1) }
            .getOrNull().orEmpty()
        record.delete()
        return tag
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun setSystemLocales(context: Context, tag: String) {
        context.getSystemService(LocaleManager::class.java)?.applicationLocales =
            if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    }

    private fun same(a: Locale, b: Locale) =
        a.toLanguageTag() == b.toLanguageTag() || (a.language == b.language && a.country.isEmpty())
}
