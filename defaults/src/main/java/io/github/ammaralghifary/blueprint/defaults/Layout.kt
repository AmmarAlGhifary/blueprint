package io.github.ammaralghifary.blueprint.defaults

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ammaralghifary.blueprint.model.SduiComponent
import io.github.ammaralghifary.blueprint.model.SduiModifier
import io.github.ammaralghifary.blueprint.modifier.toColorOrNull
import io.github.ammaralghifary.blueprint.render.SduiScope
import kotlinx.serialization.Serializable

/** `column`: vertical stack. [arrangement] is `start|center|end|space_between|space_around|space_evenly`. */
@Serializable
public data class SduiColumn(
    val children: List<SduiComponent> = emptyList(),
    val arrangement: String? = null,
    val scrollable: Boolean = false,
    override val modifier: SduiModifier? = null,
) : SduiComponent()

/** `row`: horizontal stack; [arrangement] takes the same names as [SduiColumn]. */
@Serializable
public data class SduiRow(
    val children: List<SduiComponent> = emptyList(),
    val arrangement: String? = null,
    override val modifier: SduiModifier? = null,
) : SduiComponent()

/** `box`: overlapping children, placed by each child's `modifier.alignment`. */
@Serializable
public data class SduiBox(
    val children: List<SduiComponent> = emptyList(),
    override val modifier: SduiModifier? = null,
) : SduiComponent()

/** `list`: lazy list; with no bounded room on its scroll axis (e.g. inside a scrollable column) it is a plain stack. */
@Serializable
public data class SduiList(
    val children: List<SduiComponent> = emptyList(),
    val orientation: String? = null,
    val spacing: Int? = null,
    override val modifier: SduiModifier? = null,
) : SduiComponent()

/** `spacer`: empty space of [size] dp (default 8). */
@Serializable
public data class SduiSpacer(
    val size: Int? = null,
    override val modifier: SduiModifier? = null,
) : SduiComponent()

/** `divider`: a hairline; [thickness] in dp (default 1), [color] as hex. */
@Serializable
public data class SduiDivider(
    val thickness: Int? = null,
    val color: String? = null,
    override val modifier: SduiModifier? = null,
) : SduiComponent()

@Composable
internal fun RenderColumn(c: SduiColumn, mod: Modifier, scope: SduiScope) {
    val base = if (c.scrollable) mod.verticalScroll(rememberScrollState()) else mod
    Column(base, verticalArrangement = verticalArrangementOf(c.arrangement)) {
        scope.RenderAll(c.children) { child ->
            var m: Modifier = Modifier
            child.modifier?.weight?.takeIf { it > 0f }?.let { m = m.weight(it) }
            horizontalAlignmentOf(child.modifier?.alignment)?.let { m = m.align(it) }
            m
        }
    }
}

@Composable
internal fun RenderRow(c: SduiRow, mod: Modifier, scope: SduiScope) {
    Row(mod, horizontalArrangement = horizontalArrangementOf(c.arrangement)) {
        scope.RenderAll(c.children) { child ->
            var m: Modifier = Modifier
            child.modifier?.weight?.takeIf { it > 0f }?.let { m = m.weight(it) }
            verticalAlignmentOf(child.modifier?.alignment)?.let { m = m.align(it) }
            m
        }
    }
}

@Composable
internal fun RenderBox(c: SduiBox, mod: Modifier, scope: SduiScope) {
    Box(mod) {
        scope.RenderAll(c.children) { child ->
            boxAlignmentOf(child.modifier?.alignment)?.let { Modifier.align(it) } ?: Modifier
        }
    }
}

@Composable
internal fun RenderList(c: SduiList, mod: Modifier, scope: SduiScope) {
    val gap = (c.spacing ?: 0).coerceAtLeast(0).dp
    val horizontal = c.orientation == "horizontal"
    BoxWithConstraints(mod, propagateMinConstraints = true) {
        // A lazy list given unbounded room on its scroll axis throws (inside a scrollable column,
        // or another list's item), so it falls back to a plain stack there.
        val bounded = if (horizontal) constraints.hasBoundedWidth else constraints.hasBoundedHeight
        when {
            !bounded && horizontal -> Row(horizontalArrangement = Arrangement.spacedBy(gap)) { scope.RenderAll(c.children) }
            !bounded -> Column(verticalArrangement = Arrangement.spacedBy(gap)) { scope.RenderAll(c.children) }
            horizontal -> LazyRow(horizontalArrangement = Arrangement.spacedBy(gap)) {
                itemsIndexed(c.children, key = { i, child -> i to child::class }) { _, child -> scope.Render(child) }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(gap)) {
                itemsIndexed(c.children, key = { i, child -> i to child::class }) { _, child -> scope.Render(child) }
            }
        }
    }
}

@Composable
internal fun RenderSpacer(c: SduiSpacer, mod: Modifier) {
    val s: Dp = (c.size ?: 8).coerceAtLeast(0).dp
    Spacer(mod.size(s))
}

@Composable
internal fun RenderDivider(c: SduiDivider, mod: Modifier) {
    val color = c.color.toColorOrNull()
    val t = (c.thickness ?: 1).coerceAtLeast(0).dp
    if (color != null) HorizontalDivider(mod, thickness = t, color = color)
    else HorizontalDivider(mod, thickness = t)
}
