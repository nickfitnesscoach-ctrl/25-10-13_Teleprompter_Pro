package com.teleprompter.app.utils

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.teleprompter.app.R

/**
 * Utility object for managing font families throughout the app
 * Centralizes font mapping logic to avoid duplication
 */
object FontManager {

    /**
     * Available fonts with their display names and corresponding font families
     */
    val availableFonts = listOf(
        "Default" to FontFamily.Default,
        "Bebas Neue" to FontFamily(Font(R.font.bebas_neue)),
        "Comfortaa" to FontFamily(Font(R.font.comfortaa_regular)),
        "Druk Cyr Bold" to FontFamily(Font(R.font.drukcyr_bold)),
        "Montserrat" to FontFamily(Font(R.font.montserrat_regular)),
        "Open Sans" to FontFamily(Font(R.font.opensans_regular)),
        "Oswald" to FontFamily(Font(R.font.oswald_regular)),
        "PT Sans" to FontFamily(Font(R.font.ptsans_regular)),
        "Raleway" to FontFamily(Font(R.font.raleway_regular)),
        "Roboto" to FontFamily(Font(R.font.roboto_regular)),
        "Ubuntu" to FontFamily(Font(R.font.ubuntu_regular))
    )

    /**
     * Convert display font name to preference key
     * Example: "Bebas Neue" -> "bebas_neue"
     */
    fun fontNameToKey(fontName: String): String {
        return when (fontName) {
            "Bebas Neue" -> "bebas_neue"
            "Comfortaa" -> "comfortaa_regular"
            "Druk Cyr Bold" -> "drukcyr_bold"
            "Montserrat" -> "montserrat_regular"
            "Open Sans" -> "opensans_regular"
            "Oswald" -> "oswald_regular"
            "PT Sans" -> "ptsans_regular"
            "Raleway" -> "raleway_regular"
            "Roboto" -> "roboto_regular"
            "Ubuntu" -> "ubuntu_regular"
            else -> "default"
        }
    }

    /**
     * Convert preference key to FontFamily
     * Example: "bebas_neue" -> FontFamily(Font(R.font.bebas_neue))
     */
    fun keyToFontFamily(key: String): FontFamily {
        return when (key) {
            "bebas_neue" -> FontFamily(Font(R.font.bebas_neue))
            "comfortaa_regular" -> FontFamily(Font(R.font.comfortaa_regular))
            "drukcyr_bold" -> FontFamily(Font(R.font.drukcyr_bold))
            "montserrat_regular" -> FontFamily(Font(R.font.montserrat_regular))
            "opensans_regular" -> FontFamily(Font(R.font.opensans_regular))
            "oswald_regular" -> FontFamily(Font(R.font.oswald_regular))
            "ptsans_regular" -> FontFamily(Font(R.font.ptsans_regular))
            "raleway_regular" -> FontFamily(Font(R.font.raleway_regular))
            "roboto_regular" -> FontFamily(Font(R.font.roboto_regular))
            "ubuntu_regular" -> FontFamily(Font(R.font.ubuntu_regular))
            else -> FontFamily.Default
        }
    }
}
