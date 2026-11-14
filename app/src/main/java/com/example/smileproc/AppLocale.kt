package com.example.smileproc

import android.annotation.SuppressLint
import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import com.example.smileproc.notify.CheckInNotifier
import java.util.Locale

enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    EN("en"),
    LT("lt"),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage = when (tag?.lowercase(Locale.ROOT)) {
            "en" -> EN
            "lt" -> LT
            else -> SYSTEM
        }
    }
}

/**
 * Per-app language helper for API 29-37 without AppCompat.
 *
 * - API 33+: delegates to platform [LocaleManager] (paired with `android:localeConfig`).
 * - API 29-32: stores only the selected language tag in SharedPreferences (already excluded
 *   from backups/transfers in `backup_rules.xml` and `data_extraction_rules.xml`) and wraps
 *   base contexts in [MainActivity] and [CheckInNotifier].
 */
object AppLocale {
    private const val PREFS_NAME = "smileproc_locale"
    private const val KEY_LANGUAGE_TAG = "language_tag"

    fun currentLanguage(context: Context): AppLanguage {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            val locales = localeManager?.applicationLocales
            if (locales != null && !locales.isEmpty) {
                return AppLanguage.fromTag(locales[0]?.language)
            }
            return AppLanguage.SYSTEM
        }
        val tag = context.applicationContextOrSelf()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE_TAG, null)
        return AppLanguage.fromTag(tag)
    }

    fun effectiveLocale(context: Context): Locale {
        val locales = context.resources.configuration.locales
        return if (!locales.isEmpty) locales[0] else Locale.getDefault()
    }

    fun effectiveLanguageCode(context: Context): String =
        effectiveLocale(context).language.lowercase(Locale.ROOT)

    fun wrapContext(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return base
        }
        val tag = base.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE_TAG, null)
        if (tag.isNullOrBlank()) {
            return base
        }
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    @SuppressLint("ApplySharedPref")
    fun setLanguage(context: Context, language: AppLanguage) {
        if (language == currentLanguage(context)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            if (localeManager != null) {
                localeManager.applicationLocales = if (language == AppLanguage.SYSTEM) {
                    LocaleList.getEmptyLocaleList()
                } else {
                    LocaleList.forLanguageTags(language.tag!!)
                }
            }
            CheckInNotifier.ensureChannel(context)
            return
        }

        val prefs = context.applicationContextOrSelf()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (language.tag == null) {
                remove(KEY_LANGUAGE_TAG)
            } else {
                putString(KEY_LANGUAGE_TAG, language.tag)
            }
        }.commit()

        val targetLocale = if (language.tag != null) {
            Locale.forLanguageTag(language.tag)
        } else {
            val systemLocales = Resources.getSystem().configuration.locales
            if (!systemLocales.isEmpty) systemLocales[0] else Locale.getDefault()
        }
        Locale.setDefault(targetLocale)
        CheckInNotifier.ensureChannel(wrapContext(context))
        context.findActivity()?.recreate()
    }

    @SuppressLint("ApplySharedPref")
    fun reset(context: Context) {
        context.applicationContextOrSelf()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                LocaleList.getEmptyLocaleList()
        }
    }

    private fun Context.applicationContextOrSelf(): Context = applicationContext ?: this

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
