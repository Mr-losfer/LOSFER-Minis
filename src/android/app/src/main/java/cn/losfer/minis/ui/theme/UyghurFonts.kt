package cn.losfer.minis.ui.theme

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import cn.losfer.minis.R
import cn.losfer.minis.ui.settings.PREF_APPEARANCE

/**
 * LOSFER Minis — Uyghur font selection.
 *
 * Six Uyghur (Arabic-script) typefaces ship inside the APK under res/font/.
 * The selection is stored in the appearance prefs ("font_uyghur" — the
 * resource-name key below) and applied app-wide through [MinisTheme]'s
 * typography, so every screen picks it up without per-screen wiring.
 *
 * The DEFAULT is ALKATIP Asliya, the classic Uyghur body typeface — used
 * whenever the pref is unset or names a font this build no longer ships.
 * "system" is offered in the picker for users who prefer the platform font.
 *
 * The fonts carry Uyghur + Latin glyphs only; CJK and other scripts fall
 * back to the system font automatically (Compose chains missing glyphs to
 * the platform default), so mixed-language text stays readable.
 */
object UyghurFonts {

    const val PREF_KEY = "font_uyghur"
    const val SYSTEM = "system"
    const val DEFAULT = "alkatip_asliya"

    /** Picker entries: resource-name key -> self-name shown in the UI. */
    val options: List<Pair<String, String>> = listOf(
        DEFAULT to "ALKATIP Asliya",
        "alkatip_basma" to "ALKATIP Basma",
        "alkatip_kitab" to "ALKATIP Kitab",
        "alpsoft_basma" to "Alpsoft Basma",
        "ukij_tuz" to "UKIJ Tuz",
        "uus_un_asliya" to "UUS UN Asliya",
        SYSTEM to "System",
    )

    private val families: Map<String, FontFamily> = mapOf(
        DEFAULT to FontFamily(Font(R.font.alkatip_asliya)),
        "alkatip_basma" to FontFamily(Font(R.font.alkatip_basma)),
        "alkatip_kitab" to FontFamily(Font(R.font.alkatip_kitab)),
        "alpsoft_basma" to FontFamily(Font(R.font.alpsoft_basma)),
        "ukij_tuz" to FontFamily(Font(R.font.ukij_tuz)),
        "uus_un_asliya" to FontFamily(Font(R.font.uus_un_asliya)),
    )

    fun selectedKey(context: Context): String =
        context.getSharedPreferences(PREF_APPEARANCE, Context.MODE_PRIVATE)
            .getString(PREF_KEY, DEFAULT)
            ?.takeIf { it != SYSTEM && families.containsKey(it) }
            ?: DEFAULT

    /** The FontFamily for the saved selection; null = use the system font. */
    fun selectedFamily(context: Context): FontFamily? = families[selectedKey(context)]
}
