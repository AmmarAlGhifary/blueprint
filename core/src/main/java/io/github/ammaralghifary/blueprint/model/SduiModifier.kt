package io.github.ammaralghifary.blueprint.model

import io.github.ammaralghifary.blueprint.modifier.toComposeModifier
import io.github.ammaralghifary.blueprint.modifier.toTextStyle
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Server-sent box model. Every field is optional and parsed defensively, see [toComposeModifier].
 * [weight] and [alignment] need a parent `RowScope`/`ColumnScope`, so they are not applied
 * here; layout components read them from the model.
 *
 * [textColor], [fontSize] (sp) and [fontWeight] (100-900, clamped) are optional typography hints.
 * Like [weight] and [alignment], [toComposeModifier] does not apply them; text-bearing components
 * read them via [toTextStyle].
 */
@Serializable
public data class SduiModifier(
    val margin: SduiPadding? = null,
    val padding: SduiPadding? = null,
    val width: SduiSize? = null,
    val height: SduiSize? = null,
    val alignment: String? = null,
    val weight: Float? = null,
    val elevation: Int? = null,
    @SerialName("background_color") val backgroundColor: String? = null,
    @SerialName("corner_radius") val cornerRadius: Int? = null,
    @SerialName("border_width") val borderWidth: Int? = null,
    @SerialName("border_color") val borderColor: String? = null,
    @SerialName("text_color") val textColor: String? = null,
    @SerialName("font_size") val fontSize: Int? = null,
    @SerialName("font_weight") val fontWeight: Int? = null,
)

/** Spacing in dp. Specific sides win over [horizontal]/[vertical], which win over [all]. */
@Serializable
public data class SduiPadding(
    val all: Int? = null,
    val horizontal: Int? = null,
    val vertical: Int? = null,
    val start: Int? = null,
    val top: Int? = null,
    val end: Int? = null,
    val bottom: Int? = null,
) {
    val resolvedStart: Int get() = start ?: horizontal ?: all ?: 0
    val resolvedTop: Int get() = top ?: vertical ?: all ?: 0
    val resolvedEnd: Int get() = end ?: horizontal ?: all ?: 0
    val resolvedBottom: Int get() = bottom ?: vertical ?: all ?: 0
}

/** [type] is one of `fill`, `wrap`, `exact` (needs [value] in dp). Anything else is ignored. */
@Serializable
public data class SduiSize(
    val type: String,
    val value: Int? = null,
)
