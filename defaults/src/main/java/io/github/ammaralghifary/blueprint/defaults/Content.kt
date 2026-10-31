package io.github.ammaralghifary.blueprint.defaults

import io.github.ammaralghifary.blueprint.toTextStyle
import io.github.ammaralghifary.blueprint.SduiModifier
import io.github.ammaralghifary.blueprint.SduiComponent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `text`: a run of text. [style] is `display|headline|title|body|label` (unknown → body). */
@Serializable
public data class SduiText(
    val text: String,
    val style: String? = null,
    @SerialName("max_lines") val maxLines: Int? = null,
    val align: String? = null,
    override val modifier: SduiModifier? = null,
) : SduiComponent()

/** `image`: a remote image. [scale] is `fit|crop|fill` (unknown → fit). */
@Serializable
public data class SduiImage(
    val url: String,
    @SerialName("content_description") val contentDescription: String? = null,
    val scale: String? = null,
    override val modifier: SduiModifier? = null,
) : SduiComponent()

@Composable
internal fun RenderText(c: SduiText, mod: Modifier) {
    val lines = c.maxLines?.coerceAtLeast(1)
    Text(
        text = c.text,
        modifier = mod,
        style = c.modifier.toTextStyle(MaterialTheme.typography.forRole(c.style)),
        textAlign = textAlignOf(c.align),
        maxLines = lines ?: Int.MAX_VALUE,
        overflow = if (lines != null) TextOverflow.Ellipsis else TextOverflow.Clip,
    )
}

@Composable
internal fun RenderImage(c: SduiImage, mod: Modifier) {
    AsyncImage(
        model = c.url,
        contentDescription = c.contentDescription,
        modifier = mod,
        contentScale = contentScaleOf(c.scale),
    )
}
