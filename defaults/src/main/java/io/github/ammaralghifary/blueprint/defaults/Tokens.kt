package io.github.ammaralghifary.blueprint.defaults

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Typography
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign

// String -> Compose mappers. Every one falls back to a documented default on an unknown value so a
// newer server can talk to an older app without throwing.

internal fun Typography.forRole(style: String?): TextStyle = when (style) {
    "display" -> displayMedium
    "headline" -> headlineMedium
    "title" -> titleMedium
    "label" -> labelMedium
    else -> bodyMedium
}

internal fun textAlignOf(align: String?): TextAlign? = when (align) {
    "start" -> TextAlign.Start
    "center" -> TextAlign.Center
    "end" -> TextAlign.End
    else -> null
}

internal fun contentScaleOf(scale: String?): ContentScale = when (scale) {
    "crop" -> ContentScale.Crop
    "fill" -> ContentScale.FillBounds
    else -> ContentScale.Fit
}

internal fun verticalArrangementOf(name: String?): Arrangement.Vertical = when (name) {
    "center" -> Arrangement.Center
    "end" -> Arrangement.Bottom
    "space_between" -> Arrangement.SpaceBetween
    "space_around" -> Arrangement.SpaceAround
    "space_evenly" -> Arrangement.SpaceEvenly
    else -> Arrangement.Top
}

internal fun horizontalArrangementOf(name: String?): Arrangement.Horizontal = when (name) {
    "center" -> Arrangement.Center
    "end" -> Arrangement.End
    "space_between" -> Arrangement.SpaceBetween
    "space_around" -> Arrangement.SpaceAround
    "space_evenly" -> Arrangement.SpaceEvenly
    else -> Arrangement.Start
}

/** Column children: `start|center|end`. */
internal fun horizontalAlignmentOf(name: String?): Alignment.Horizontal? = when (name) {
    "start" -> Alignment.Start
    "center" -> Alignment.CenterHorizontally
    "end" -> Alignment.End
    else -> null
}

/** Row children: `top|center|bottom`. */
internal fun verticalAlignmentOf(name: String?): Alignment.Vertical? = when (name) {
    "top" -> Alignment.Top
    "center" -> Alignment.CenterVertically
    "bottom" -> Alignment.Bottom
    else -> null
}

/** Box children: `top_start|top_center|...|bottom_end`. */
internal fun boxAlignmentOf(name: String?): Alignment? = when (name) {
    "top_start" -> Alignment.TopStart
    "top_center" -> Alignment.TopCenter
    "top_end" -> Alignment.TopEnd
    "center_start" -> Alignment.CenterStart
    "center" -> Alignment.Center
    "center_end" -> Alignment.CenterEnd
    "bottom_start" -> Alignment.BottomStart
    "bottom_center" -> Alignment.BottomCenter
    "bottom_end" -> Alignment.BottomEnd
    else -> null
}
