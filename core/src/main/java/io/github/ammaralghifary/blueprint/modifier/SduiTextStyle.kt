package io.github.ammaralghifary.blueprint.modifier

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.ammaralghifary.blueprint.model.SduiModifier

/**
 * Merges the modifier's typography hints (`text_color`, `font_size`, `font_weight`) onto [base].
 * Unparsable colours and non-positive sizes are skipped; weight is clamped to 100..900.
 * Never throws.
 */
public fun SduiModifier?.toTextStyle(base: TextStyle): TextStyle {
    if (this == null) return base
    var style = base
    textColor.toColorOrNull()?.let { style = style.copy(color = it) }
    fontSize?.takeIf { it > 0 }?.let { style = style.copy(fontSize = it.sp) }
    fontWeight?.let { style = style.copy(fontWeight = FontWeight(it.coerceIn(100, 900))) }
    return style
}
