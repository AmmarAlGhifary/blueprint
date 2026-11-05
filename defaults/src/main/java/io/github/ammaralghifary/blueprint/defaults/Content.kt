package io.github.ammaralghifary.blueprint.defaults

import android.annotation.SuppressLint
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import io.github.ammaralghifary.blueprint.model.SduiComponent
import io.github.ammaralghifary.blueprint.model.SduiModifier
import io.github.ammaralghifary.blueprint.modifier.toTextStyle
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@SuppressLint("UnsafeOptInUsageError")
/**
 * `text`: a run of text. [style] is `display|headline|title|body|label` (unknown → body), [align] is
 * `start|center|end`, and [maxLines] truncates with an ellipsis.
 *
 * @author Ammar Al Ghifary
 */
@Serializable
public data class SduiText(
    val text: String,
    val style: String? = null,
    @SerialName("max_lines") val maxLines: Int? = null,
    val align: String? = null,
    override val modifier: SduiModifier? = null,
) : SduiComponent()

@SuppressLint("UnsafeOptInUsageError")
/**
 * `image`: a remote image. [scale] is `fit|crop|fill` (unknown → fit); a null [contentDescription]
 * marks it decorative.
 *
 * @author Ammar Al Ghifary
 */
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
