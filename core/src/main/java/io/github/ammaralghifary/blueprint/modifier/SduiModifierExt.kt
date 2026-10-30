package io.github.ammaralghifary.blueprint

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private fun Int.safeDp(): Dp = coerceAtLeast(0).dp

/**
 * Applies the server box model on top of [baseModifier], in this order:
 * margin, size, shadow, clip, border, background, padding.
 *
 * Bad input never throws: negative dp values are clamped to 0 and unparsable colors or unknown
 * size types are skipped.
 */
public fun SduiModifier.toComposeModifier(baseModifier: Modifier = Modifier): Modifier {
    var mod = baseModifier
    val shape = RoundedCornerShape((cornerRadius ?: 0).safeDp())

    margin?.let {
        mod = mod.padding(it.resolvedStart.safeDp(), it.resolvedTop.safeDp(), it.resolvedEnd.safeDp(), it.resolvedBottom.safeDp())
    }

    width?.let { size ->
        mod = when (size.type) {
            "fill" -> mod.fillMaxWidth()
            "wrap" -> mod.wrapContentWidth()
            "exact" -> size.value?.let { mod.width(it.safeDp()) } ?: mod
            else -> mod
        }
    }
    height?.let { size ->
        mod = when (size.type) {
            "fill" -> mod.fillMaxHeight()
            "wrap" -> mod.wrapContentHeight()
            "exact" -> size.value?.let { mod.height(it.safeDp()) } ?: mod
            else -> mod
        }
    }

    // The shadow must come before clip, otherwise the clip cuts the shadow off.
    elevation?.let { mod = mod.shadow(it.safeDp(), shape, clip = false) }

    cornerRadius?.let { mod = mod.clip(shape) }

    val borderColorParsed = borderColor.toColorOrNull()
    if (borderWidth != null && borderColorParsed != null) {
        mod = mod.border(borderWidth.safeDp(), borderColorParsed, shape)
    }

    backgroundColor.toColorOrNull()?.let { mod = mod.background(it, shape) }

    padding?.let {
        mod = mod.padding(it.resolvedStart.safeDp(), it.resolvedTop.safeDp(), it.resolvedEnd.safeDp(), it.resolvedBottom.safeDp())
    }

    return mod
}
