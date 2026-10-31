package io.github.ammaralghifary.blueprint.modifier

import androidx.compose.ui.graphics.Color

/**
 * Parses `#RGB`, `#RRGGBB` or `#AARRGGBB` into an ARGB int, or null when malformed.
 * Pure Kotlin (no android.graphics) so it is unit-testable on the JVM.
 */
internal fun parseColorArgb(hex: String?): Int? {
    val s = hex?.trim()?.removePrefix("#") ?: return null
    if (s.isEmpty() || s.any { Character.digit(it, 16) < 0 }) return null
    return when (s.length) {
        3 -> {
            val (r, g, b) = s.map { Character.digit(it, 16) * 17 }
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        6 -> (0xFF000000L or s.toLong(16)).toInt()
        8 -> s.toLong(16).toInt()
        else -> null
    }
}

/** Null-safe hex to [Color]; null for missing or malformed input. */
public fun String?.toColorOrNull(): Color? = parseColorArgb(this)?.let { Color(it) }
